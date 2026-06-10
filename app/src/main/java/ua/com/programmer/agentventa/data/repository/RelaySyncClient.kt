package ua.com.programmer.agentventa.data.repository

import android.content.SharedPreferences
import android.util.Base64
import androidx.core.content.edit
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import ua.com.programmer.agentventa.data.local.entity.UserAccount
import ua.com.programmer.agentventa.data.local.entity.toMap
import ua.com.programmer.agentventa.data.remote.Result
import ua.com.programmer.agentventa.data.remote.api.RelayApi
import ua.com.programmer.agentventa.data.remote.dto.RelayAckRequest
import ua.com.programmer.agentventa.data.remote.dto.RelayStatusData
import ua.com.programmer.agentventa.data.remote.dto.RelayUploadDocument
import ua.com.programmer.agentventa.data.remote.dto.RelayUploadRequest
import ua.com.programmer.agentventa.domain.repository.DataExchangeRepository
import ua.com.programmer.agentventa.domain.repository.UserAccountRepository
import ua.com.programmer.agentventa.infrastructure.config.ApiKeyProvider
import ua.com.programmer.agentventa.infrastructure.logger.Logger
import ua.com.programmer.agentventa.utility.Constants
import ua.com.programmer.agentventa.utility.XMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Data-exchange engine for REST-relay accounts. It is the request/response
 * counterpart of WebSocketRepositoryImpl's catalog push and upload frames:
 *
 *  - [checkApproval] -> GET /status      (replaces WS approval/license state)
 *  - [pullCatalog]   -> GET /pull + ack  (replaces WS catalog push)
 *  - [uploadDocuments] -> POST /upload   (replaces WS upload_* frames)
 *
 * The catalog save + cleanup logic intentionally mirrors
 * WebSocketRepositoryImpl.processUnifiedPayloadLocked so both transports route
 * data through DataExchangeRepository.saveData by value_id and clean up stale
 * rows off the 1C-authoritative batch_complete timestamp.
 */
@Singleton
class RelaySyncClient @Inject constructor(
    private val relayApi: RelayApi,
    private val dataExchangeRepository: DataExchangeRepository,
    private val userAccountRepository: UserAccountRepository,
    private val apiKeyProvider: ApiKeyProvider,
    private val sharedPreferences: SharedPreferences,
    private val logger: Logger,
) {
    private val logTag = "RelaySyncClient"
    private val gson = Gson()

    private fun authHeader(account: UserAccount) =
        "Bearer ${apiKeyProvider.webSocketApiKey}:${account.guid}"

    // base64url JSON of device metadata, sent on /status so the relay can
    // identify an auto-registered device in the admin pending list. Matches the
    // encoding the WS connect handler uses (URL-safe, no wrap; the relay trims
    // padding before decoding).
    private fun appParametersParam(account: UserAccount): String {
        val params = mapOf(
            "device_uuid" to account.guid,
            "description" to account.description,
            "license" to account.license,
            "data_format" to account.dataFormat,
        )
        val json = gson.toJson(params)
        return Base64.encodeToString(json.toByteArray(Charsets.UTF_8), Base64.URL_SAFE or Base64.NO_WRAP)
    }

    /**
     * Verifies the device may transfer data, via GET /status. Returns
     * (true, "") when allowed, otherwise (false, reason). Mirrors the
     * Pair<Boolean,String> contract of NetworkRepositoryImpl.checkDeviceApproval
     * so the REST branch reports approval errors the same way the WS branch does.
     * Side effect: persists the license number reported by the relay.
     */
    suspend fun checkApproval(account: UserAccount): Pair<Boolean, String> {
        return try {
            val resp = relayApi.status(authHeader(account), appParametersParam(account))
            val data = resp.data
            if (!resp.success || data == null) {
                return Pair(false, resp.statusMessage ?: "Status check failed")
            }
            persistLicense(data.licenseNumber)
            if (data.canTransfer) {
                Pair(true, "")
            } else {
                Pair(false, approvalMessage(data))
            }
        } catch (e: Exception) {
            logger.e(logTag, "status check failed: ${e.message}")
            Pair(false, e.message ?: "Status check failed")
        }
    }

    private fun approvalMessage(data: RelayStatusData): String = when {
        data.licenseError == "license_expired" -> "License expired"
        data.licenseError == "license_not_active" -> "License not active"
        data.licenseError == "device_limit_reached" -> "Device limit reached"
        data.status == "pending" -> "Device pending approval. Please wait for administrator to approve this device."
        data.status == "denied" -> "Device access denied"
        !data.licenseErrorReason.isNullOrBlank() -> data.licenseErrorReason
        else -> "Device cannot transfer data"
    }

    private suspend fun persistLicense(licenseNumber: String?) {
        if (licenseNumber.isNullOrBlank()) return
        try {
            userAccountRepository.updateCurrent { current ->
                if (current.license == licenseNumber) current
                else current.copy(license = licenseNumber)
            }
        } catch (e: Exception) {
            logger.w(logTag, "failed to persist license: ${e.message}")
        }
    }

    /**
     * Drains the device's catalog queue: pull -> save -> ack, looping until the
     * relay reports an empty queue. Runs stale-row cleanup whenever a
     * batch_complete sentinel is seen, using the 1C timestamp it carries (never
     * a client clock), with the same crash-recovery checkpoint as the WS path.
     */
    fun pullCatalog(account: UserAccount): Flow<Result> = flow {
        val auth = authHeader(account)
        val accountGuid = account.guid
        var totalSaved = 0
        // Safety bound against a server that never drains; each round pulls up
        // to `limit` messages, so this caps a single sync at limit * rounds.
        val maxRounds = 1000

        for (round in 0 until maxRounds) {
            val resp = try {
                relayApi.pull(auth, PULL_LIMIT)
            } catch (e: Exception) {
                logger.e(logTag, "pull failed: ${e.message}")
                emit(Result.Error(e.message ?: "pull failed"))
                return@flow
            }
            if (!resp.success) {
                emit(Result.Error(resp.statusMessage ?: "pull failed"))
                return@flow
            }
            val data = resp.data ?: break
            if (data.count == 0 || data.messages.isEmpty()) break

            val batch = ArrayList<XMap>(BATCH_SIZE)
            val optionsItems = mutableListOf<JsonObject>()
            val ackIds = ArrayList<String>(data.messages.size)
            var batchCompleteTs: Long? = null

            for (message in data.messages) {
                ackIds.add(message.messageId)
                for (item in message.items) {
                    val type = item.get("type")?.asString
                    if (type == Constants.VALUE_ID_BATCH_COMPLETE) {
                        batchCompleteTs = item.get("timestamp")?.asLong
                        continue
                    }
                    val valueId = item.get("value_id")?.asString
                    if (valueId.isNullOrEmpty()) continue
                    if (valueId == Constants.VALUE_ID_OPTIONS) {
                        optionsItems.add(item)
                        continue
                    }
                    // 1C embeds a UTC timestamp in every element; XMap reads it
                    // from the map. We only stamp the account guid here.
                    val itemMap = gson.fromJson(item, Map::class.java) as Map<*, *>
                    val xMap = XMap(itemMap)
                    xMap.setDatabaseId(accountGuid)
                    batch.add(xMap)
                    if (batch.size >= BATCH_SIZE) {
                        dataExchangeRepository.saveData(batch)
                        totalSaved += batch.size
                        batch.clear()
                    }
                }
            }
            if (batch.isNotEmpty()) {
                dataExchangeRepository.saveData(batch)
                totalSaved += batch.size
                batch.clear()
            }
            if (optionsItems.isNotEmpty()) {
                processOptions(optionsItems)
            }

            // Confirm delivery so the relay marks these rows delivered. Done
            // after persistence — an unacked message simply redelivers and our
            // catalog writes are idempotent upserts.
            if (ackIds.isNotEmpty()) {
                try {
                    relayApi.ack(auth, RelayAckRequest(ackIds))
                } catch (e: Exception) {
                    logger.w(logTag, "ack failed: ${e.message}")
                }
            }

            emit(Result.Progress("catalog: $totalSaved"))

            if (batchCompleteTs != null) {
                runBatchCleanup(accountGuid, batchCompleteTs)
                logger.d(logTag, "Catalog sync complete: $totalSaved items")
            }
        }
    }

    private suspend fun processOptions(optionsItems: List<JsonObject>) {
        try {
            val obj = optionsItems.first().deepCopy()
            val license = obj.get("license")?.asString ?: ""
            obj.remove("value_id")
            val optionsJson = gson.toJson(obj)
            userAccountRepository.updateCurrent { current ->
                current.copy(
                    options = optionsJson,
                    license = license.ifEmpty { current.license },
                )
            }
        } catch (e: Exception) {
            logger.e(logTag, "options save failed: ${e.message}")
        }
    }

    // Mirror of WebSocketRepositoryImpl.runBatchCleanup: write a checkpoint
    // before cleanup so a crash between save and cleanup replays exactly once.
    private suspend fun runBatchCleanup(accountGuid: String, timestamp: Long) {
        try {
            sharedPreferences.edit {
                putLong(Constants.PREF_PENDING_CLEANUP_TIMESTAMP, timestamp)
                putString(Constants.PREF_PENDING_CLEANUP_ACCOUNT, accountGuid)
            }
            dataExchangeRepository.cleanUp(accountGuid, timestamp)
            sharedPreferences.edit {
                remove(Constants.PREF_PENDING_CLEANUP_TIMESTAMP)
                remove(Constants.PREF_PENDING_CLEANUP_ACCOUNT)
            }
        } catch (e: Exception) {
            logger.e(logTag, "Batch cleanup failed (will retry next session): ${e.message}")
        }
    }

    /**
     * Uploads all unsent documents (orders+content, cash, images, locations) in
     * one POST /upload, then marks each accepted document sent. Payload shapes
     * match the WS upload_* frames so 1C receives identical data. The relay
     * dedupes on document_guid, so a re-send after a dropped response is safe.
     */
    fun uploadDocuments(account: UserAccount): Flow<Result> = flow {
        val auth = authHeader(account)
        val accountGuid = account.guid

        val documents = mutableListOf<RelayUploadDocument>()

        val orders = dataExchangeRepository.getOrders(accountGuid)
        val pendingContent = if (orders.isNotEmpty()) {
            dataExchangeRepository.getPendingOrdersContent(accountGuid)
        } else {
            emptyMap()
        }
        for (order in orders) {
            val content = pendingContent[order.guid].orEmpty().map { it.toMap() }
            documents.add(
                RelayUploadDocument(
                    type = "order",
                    documentGuid = order.guid,
                    data = order.toMap(accountGuid, content),
                )
            )
        }
        for (cash in dataExchangeRepository.getCash(accountGuid)) {
            documents.add(RelayUploadDocument("cash", cash.guid, cash.toMap(accountGuid)))
        }
        for (image in dataExchangeRepository.getClientImages(accountGuid)) {
            documents.add(RelayUploadDocument("image", image.guid, image.toMap()))
        }
        for (location in dataExchangeRepository.getClientLocations(accountGuid)) {
            // For locations the idempotency / mark-sent key is the client guid.
            documents.add(RelayUploadDocument("location", location.clientGuid, location.toMap()))
        }

        if (documents.isEmpty()) {
            emit(Result.Progress("upload: 0"))
            return@flow
        }

        val resp = try {
            relayApi.upload(auth, RelayUploadRequest(documents))
        } catch (e: Exception) {
            logger.e(logTag, "upload failed: ${e.message}")
            emit(Result.Error(e.message ?: "upload failed"))
            return@flow
        }
        val data = resp.data
        if (!resp.success || data == null) {
            emit(Result.Error(resp.statusMessage ?: "upload failed"))
            return@flow
        }

        var sent = 0
        for (result in data.results) {
            if (!result.queued) continue
            val guid = result.documentGuid ?: continue
            when (result.type) {
                "order" -> dataExchangeRepository.markOrderSentViaWebSocket(accountGuid, guid)
                "cash" -> dataExchangeRepository.markCashSentViaWebSocket(accountGuid, guid)
                "image" -> dataExchangeRepository.markImageSentViaWebSocket(accountGuid, guid)
                "location" -> dataExchangeRepository.markLocationSentViaWebSocket(accountGuid, guid)
            }
            sent++
        }

        logger.d(logTag, "Uploaded $sent/${documents.size} documents via REST")
        emit(Result.Progress("upload: $sent"))
    }

    private companion object {
        const val PULL_LIMIT = 25
        const val BATCH_SIZE = 500
    }
}
