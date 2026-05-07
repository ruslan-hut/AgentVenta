package ua.com.programmer.agentventa.data.remote.dto

import com.google.gson.JsonObject

data class DebugLogUploadDto(
    val device_uuid: String,
    val account_description: String,
    val app_version: String,
    val app_version_code: Int,
    val entries: List<DebugLogEntryDto>
)

data class DebugLogEntryDto(
    val timestamp: Long,
    val level: String,
    val tag: String,
    val message: String,
    val fields: JsonObject?
)
