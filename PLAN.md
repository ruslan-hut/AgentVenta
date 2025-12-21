# WebSocket Migration Completion Plan

## Current State Summary

The WebSocket infrastructure is **~75% complete**. Core connection management, settings sync, and catalog download work. **Document synchronization (orders, cash, images, locations) is the critical missing piece.**

### What Works
- WebSocket connection with reconnection logic
- Settings upload/download via `SettingsSyncRepository`
- Catalog download with unified payload format
- Device approval status handling
- Ping/pong keepalive

### What's Broken/Missing
- Document upload methods have **type mismatches** (return `Flow<Result>` but call methods returning `Flow<SendResult>`)
- **Empty list bugs** - images/locations pass empty lists instead of actual data
- No **database update after WebSocket ACK** - documents never marked as sent
- No **server response handlers** for upload confirmations
- SyncManager doesn't check WebSocket connection state

---

## Implementation Tasks

### Phase 1: Fix Critical Bugs (Priority: CRITICAL)

#### 1.1 Fix Type Mismatch in NetworkRepositoryImpl
**File:** `data/repository/NetworkRepositoryImpl.kt`

**Problem:** Methods return `Flow<Result>` but `webSocketRepository.sendMessage()` returns `Flow<SendResult>`. Need conversion.

**Changes:**
```kotlin
// Add extension or helper to convert SendResult to Result
private fun Flow<SendResult>.toResult(): Flow<Result> = this.map { sendResult ->
    when {
        sendResult.success -> Result.Success(sendResult)
        else -> Result.Error(sendResult.error ?: "Unknown error")
    }
}
```

**Apply to methods:**
- `uploadOrderViaWebSocket()` (lines 610-664)
- `uploadCashViaWebSocket()` (lines 669-715)
- `uploadImagesViaWebSocket()` (lines 720-765)
- `uploadLocationsViaWebSocket()` (lines 770-811)
- `downloadCatalogsViaWebSocket()` (lines 820-872)

#### 1.2 Fix Empty List Bugs
**File:** `data/repository/NetworkRepositoryImpl.kt` (lines 493, 509)

**Problem:** `uploadImagesViaWebSocket(emptyList())` and `uploadLocationsViaWebSocket(emptyList())` pass empty lists.

**Fix:**
```kotlin
// Replace empty lists with actual data retrieval
val images = dataExchangeRepository.getClientImages()
if (images.isNotEmpty()) {
    uploadImagesViaWebSocket(images.map { it.toProductImage() })
}

val locations = dataExchangeRepository.getClientLocations()
if (locations.isNotEmpty()) {
    uploadLocationsViaWebSocket(locations)
}
```

---

### Phase 2: Implement Document Status Updates (Priority: HIGH)

#### 2.1 Add WebSocket-specific DAO methods
**File:** `data/local/dao/DataExchangeDao.kt`

**Add methods to mark documents as sent after WebSocket ACK:**
```kotlin
@Query("UPDATE orders SET is_sent=1 WHERE db_guid=:accountGuid AND guid=:orderGuid")
suspend fun markOrderSentViaWebSocket(accountGuid: String, orderGuid: String): Int

@Query("UPDATE cash SET is_sent=1 WHERE db_guid=:accountGuid AND guid=:docGuid")
suspend fun markCashSentViaWebSocket(accountGuid: String, docGuid: String): Int

@Query("UPDATE client_images SET is_sent=1, is_local=0 WHERE db_guid=:accountGuid AND guid=:guid")
suspend fun markImageSentViaWebSocket(accountGuid: String, guid: String): Int
```

#### 2.2 Add ACK handler for document uploads
**File:** `data/repository/WebSocketRepositoryImpl.kt`

**In `handleIncomingMessage()`, add cases for upload confirmations:**
```kotlin
when (message.type) {
    Constants.WEBSOCKET_MESSAGE_TYPE_ACK -> {
        val payload = message.payload
        val documentType = payload?.get("document_type")?.asString
        val documentGuid = payload?.get("document_guid")?.asString

        when (documentType) {
            "order" -> dataExchangeDao.markOrderSentViaWebSocket(accountGuid, documentGuid)
            "cash" -> dataExchangeDao.markCashSentViaWebSocket(accountGuid, documentGuid)
            "image" -> dataExchangeDao.markImageSentViaWebSocket(accountGuid, documentGuid)
            // ... handle other types
        }

        // Also remove from pending messages
        removePendingMessage(message.messageId)
    }
}
```

#### 2.3 Update DataExchangeRepository interface
**File:** `domain/repository/DataExchangeRepository.kt`

**Add methods:**
```kotlin
suspend fun markOrderSentViaWebSocket(accountGuid: String, orderGuid: String)
suspend fun markCashSentViaWebSocket(accountGuid: String, docGuid: String)
suspend fun markImageSentViaWebSocket(accountGuid: String, imageGuid: String)
```

---

### Phase 3: Improve Sync Flow (Priority: MEDIUM)

#### 3.1 Add connection check before sync
**File:** `presentation/common/viewmodel/SyncManager.kt`

**Modify sync methods to check WebSocket state:**
```kotlin
fun callDiffSync() {
    viewModelScope.launch {
        val account = userAccountRepository.getCurrentAccount() ?: return@launch

        if (account.shouldUseWebSocket()) {
            // Check WebSocket connection
            if (!webSocketRepository.isConnected()) {
                _syncState.value = SyncState.Error("WebSocket not connected")
                // Attempt to connect
                webSocketRepository.connect(account)
                delay(2000) // Wait for connection
                if (!webSocketRepository.isConnected()) {
                    _syncState.value = SyncState.Error("Failed to connect to relay server")
                    return@launch
                }
            }
        }

        // Proceed with sync
        networkRepository.updateDifferential().collect { result ->
            // ... existing handling
        }
    }
}
```

#### 3.2 Expose WebSocket state to UI
**File:** `presentation/common/viewmodel/SyncManager.kt`

**Add observable for connection state:**
```kotlin
val webSocketState: StateFlow<WebSocketState> = webSocketRepository.connectionState
```

#### 3.3 Add WebSocket state indicator in SyncFragment
**File:** `presentation/features/settings/SyncFragment.kt`

**Show connection status:**
```kotlin
viewModel.webSocketState.collectLatest { state ->
    binding.wsStatusIcon.setImageResource(
        when (state) {
            is WebSocketState.Connected -> R.drawable.ic_cloud_done
            is WebSocketState.Connecting -> R.drawable.ic_cloud_sync
            is WebSocketState.Disconnected -> R.drawable.ic_cloud_off
            is WebSocketState.Error -> R.drawable.ic_cloud_error
        }
    )
}
```

---

### Phase 4: Handle Upload Responses (Priority: MEDIUM)

#### 4.1 Define server response format for uploads
**Expected server ACK for document upload:**
```json
{
  "type": "ack",
  "message_id": "original-message-id",
  "timestamp": "2025-01-15T10:30:00Z",
  "status": "approved",
  "payload": {
    "status": "received",
    "document_type": "order",
    "document_guid": "order-guid-123"
  }
}
```

#### 4.2 Update WebSocketRepositoryImpl to handle upload ACKs
**File:** `data/repository/WebSocketRepositoryImpl.kt`

**Extend ACK handling (around line 369):**
```kotlin
Constants.WEBSOCKET_MESSAGE_TYPE_ACK -> {
    val ackPayload = message.payload
    val status = ackPayload?.get("status")?.asString

    if (status == "received") {
        val docType = ackPayload?.get("document_type")?.asString
        val docGuid = ackPayload?.get("document_guid")?.asString

        if (docType != null && docGuid != null) {
            // Emit to Flow for repository to handle DB update
            _documentAcks.emit(DocumentAck(docType, docGuid))
        }
    }

    // Remove from pending
    removePendingMessage(message.messageId)
}
```

#### 4.3 Add DocumentAck handling in NetworkRepositoryImpl
**File:** `data/repository/NetworkRepositoryImpl.kt`

**Subscribe to ACKs and update database:**
```kotlin
init {
    // Listen for document ACKs
    viewModelScope.launch {
        webSocketRepository.documentAcks.collect { ack ->
            val accountGuid = currentSystemAccount?.guid ?: return@collect
            when (ack.documentType) {
                "order" -> dataExchangeDao.markOrderSentViaWebSocket(accountGuid, ack.documentGuid)
                "cash" -> dataExchangeDao.markCashSentViaWebSocket(accountGuid, ack.documentGuid)
                "image" -> dataExchangeDao.markImageSentViaWebSocket(accountGuid, ack.documentGuid)
            }
        }
    }
}
```

---

### Phase 5: Error Recovery (Priority: LOW)

#### 5.1 Implement retry for failed uploads
**File:** `data/repository/WebSocketRepositoryImpl.kt`

**Already has `retryFailedMessages()` - ensure it's called on reconnection:**
```kotlin
override fun onOpen(webSocket: WebSocket, response: Response) {
    // ... existing code

    // Retry failed messages after reconnection
    scope.launch {
        delay(1000) // Wait for connection to stabilize
        val retried = retryFailedMessages()
        logger.i(logTag, "Retried $retried failed messages after reconnection")
    }
}
```

#### 5.2 Handle partial sync failures
**File:** `data/repository/NetworkRepositoryImpl.kt`

**Track which documents failed and report:**
```kotlin
private val failedUploads = mutableListOf<String>()

// In upload methods, track failures
if (result is SendResult && !result.success) {
    failedUploads.add(documentGuid)
}

// At end of sync, report failures
if (failedUploads.isNotEmpty()) {
    emit(Result.Progress("Warning: ${failedUploads.size} documents failed to upload"))
}
```

---

### Phase 6: Testing & Verification (Priority: HIGH)

#### 6.1 Unit Tests
- Test `SendResult` to `Result` conversion
- Test ACK handling with mock WebSocket
- Test document status updates

#### 6.2 Integration Tests
- Full sync flow with WebSocket mode
- Mode switching (WebSocket ↔ HTTP)
- Reconnection with pending documents

#### 6.3 Manual Testing Checklist
- [ ] Create order → Sync via WebSocket → Verify `isSent=1` in DB
- [ ] Create cash receipt → Sync → Verify sent
- [ ] Take client photo → Sync → Verify image uploaded
- [ ] Disconnect network → Create order → Reconnect → Verify queued and sent
- [ ] Switch to HTTP mode → Verify HTTP sync still works
- [ ] Switch back to WebSocket → Verify WebSocket sync works

---

## File Changes Summary

| File | Changes |
|------|---------|
| `NetworkRepositoryImpl.kt` | Fix type mismatches, fix empty list bugs, add ACK listener |
| `WebSocketRepositoryImpl.kt` | Add document ACK handler, emit DocumentAck flow |
| `DataExchangeDao.kt` | Add `markOrderSentViaWebSocket`, `markCashSentViaWebSocket`, `markImageSentViaWebSocket` |
| `DataExchangeRepository.kt` | Add interface methods for WebSocket document updates |
| `SyncManager.kt` | Add connection check, expose WebSocket state |
| `SyncFragment.kt` | Add WebSocket status indicator |
| `WebSocketRepository.kt` | Add `documentAcks: Flow<DocumentAck>` |

---

## Estimated Effort

| Phase | Description | Effort |
|-------|-------------|--------|
| Phase 1 | Fix Critical Bugs | 2-3 hours |
| Phase 2 | Document Status Updates | 3-4 hours |
| Phase 3 | Improve Sync Flow | 2-3 hours |
| Phase 4 | Handle Upload Responses | 2-3 hours |
| Phase 5 | Error Recovery | 2-3 hours |
| Phase 6 | Testing | 4-6 hours |
| **Total** | | **15-22 hours** |

---

## Dependencies

- Backend must send proper ACK messages with `document_type` and `document_guid`
- Backend must handle `upload_order`, `upload_cash`, `upload_image`, `upload_location` message types
- Backend must queue documents for 1C retrieval

---

## Success Criteria

1. Orders created in WebSocket mode are marked as sent after server ACK
2. Cash receipts sync correctly via WebSocket
3. Client images upload via WebSocket and are marked as sent
4. Locations sync via WebSocket
5. HTTP mode continues to work (backward compatibility)
6. UI shows WebSocket connection status
7. Failed uploads are retried on reconnection
8. No data loss during mode switching
