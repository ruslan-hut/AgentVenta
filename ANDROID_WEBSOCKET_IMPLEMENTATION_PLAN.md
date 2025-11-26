# Android WebSocket Implementation Plan

## Current Server Status
✅ **Server is ready** with the following features:
- REST API endpoints (POST /api/v1/push, GET /api/v1/pull)
- WebSocket handler at /ws/device
- License authentication
- MongoDB with device/queue/license collections
- Connection management and ping/pong
- Message routing infrastructure

## Goal
Implement basic WebSocket functionality in the Android app to test connection and data exchange with the relay server.

---

## 🔑 Key Architecture Decision: Multi-Account Support

### Understanding the Current Architecture
The app already supports **multiple accounts** (multiple connections to different 1C databases):
- Each `UserAccount` represents a separate connection
- Each account has a unique `guid` field (UUID)
- All data entities use `db_guid` to separate data per account
- Only one account is "current" at a time (`is_current = 1`)
- Entities like `Client`, `Product` use composite keys: `["guid", "db_guid"]`

### WebSocket Implementation Strategy
**❌ WRONG**: One device UUID shared by all accounts
**✅ CORRECT**: Each `UserAccount.guid` serves as the device UUID for WebSocket

### Why This Matters:
1. **Server Side**: Each account connects with its own device UUID (`UserAccount.guid`)
2. **Data Separation**: The server tracks devices by UUID, which maps to account GUIDs
3. **Multiple Connections**: Device can have multiple accounts, each with separate WebSocket connections
4. **Database Consistency**: The `db_guid` in local entities matches the WebSocket device UUID

### Example Scenario:
```
Phone: Samsung Galaxy S21
├── Account A (guid: "aaa-111")
│   ├── Connects to: wss://relay.com/ws/device?uuid=aaa-111&license=key1
│   └── Local data: Orders/Clients with db_guid="aaa-111"
│
├── Account B (guid: "bbb-222")
│   ├── Connects to: wss://relay.com/ws/device?uuid=bbb-222&license=key2
│   └── Local data: Orders/Clients with db_guid="bbb-222"
│
└── Switch accounts → Disconnect old, connect new
```

**Result**: No need for separate device UUID management! Use `UserAccount.guid` everywhere.

---

## Phase 1: Foundation & Database Schema (Day 1-2)

### Step 1.1: Add Constants
**File**: `app/src/main/java/ua/com/programmer/agentventa/utility/Constants.java`

**Action**: Add new constants for WebSocket support

```java
// Add after line 42 (after SYNC_FORMAT_HTTP)
public static final String SYNC_FORMAT_WEBSOCKET = "WebSocket_relay";

// Add in a new section for WebSocket configuration
public static final int WEBSOCKET_RECONNECT_INITIAL_DELAY = 1000; // 1s
public static final int WEBSOCKET_RECONNECT_MAX_DELAY = 60000; // 60s
public static final int WEBSOCKET_PING_INTERVAL = 30000; // 30s
public static final String WEBSOCKET_MESSAGE_TYPE_DATA = "data";
public static final String WEBSOCKET_MESSAGE_TYPE_ACK = "ack";
public static final String WEBSOCKET_MESSAGE_TYPE_PING = "ping";
public static final String WEBSOCKET_MESSAGE_TYPE_PONG = "pong";
public static final String WEBSOCKET_MESSAGE_TYPE_ERROR = "error";
```

**Test**: Build project to ensure no compilation errors.

---

### Step 1.2: Update UserAccount Entity
**File**: `app/src/main/java/ua/com/programmer/agentventa/data/local/entity/UserAccount.kt`

**Action**: Add new field for WebSocket relay server only

**IMPORTANT**: The existing `UserAccount.guid` will be used as the device UUID for WebSocket connections. Each account (connection to a different 1C database) gets its own unique identifier that's already tracked via `guid`. This matches the multi-account architecture where `db_guid` separates data in the local database.

```kotlin
@Entity(tableName = "user_accounts", primaryKeys = ["guid"])
data class UserAccount(
    val guid: String,  // This serves as device_uuid for WebSocket!
    @ColumnInfo(name = "is_current") val isCurrent: Int = 0,
    @ColumnInfo(name = "extended_id") val extendedId: Int = 0,
    val description: String = "",
    val license: String = "",
    @ColumnInfo(name = "data_format") val dataFormat: String = "",
    @ColumnInfo(name = "db_server") val dbServer: String = "",
    @ColumnInfo(name = "db_name") val dbName: String = "",
    @ColumnInfo(name = "db_user") val dbUser: String = "",
    @ColumnInfo(name = "db_password") val dbPassword: String = "",
    val token: String = "",
    val options: String = "",
    // NEW FIELD
    @ColumnInfo(name = "relay_server") val relayServer: String = ""
)
```

**Test**: Build project - expect Room compiler errors (will fix in next step).

---

### Step 1.3: Create Database Migration
**File**: `app/src/main/java/ua/com/programmer/agentventa/data/local/database/AppDatabase.kt`

**Action**:
1. Find current database version (should be around `version = 20`)
2. Change to `version = 21`
3. Add migration after existing migrations

```kotlin
// Add after existing MIGRATION_X_Y definitions
private val MIGRATION_20_21 = object : Migration(20, 21) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL(
            "ALTER TABLE user_accounts ADD COLUMN relay_server TEXT NOT NULL DEFAULT ''"
        )
    }
}
```

4. Add migration to database builder:

```kotlin
// Find the Room.databaseBuilder section and add .addMigrations()
.addMigrations(
    // ... existing migrations ...
    MIGRATION_20_21  // ADD THIS
)
```

**Test**:
- Build project successfully
- Run app and verify database upgrades without crashes
- Check logcat for migration logs

---

### Step 1.4: Add Extension Functions to UserAccount
**File**: `app/src/main/java/ua/com/programmer/agentventa/data/local/entity/UserAccount.kt`

**Action**: Add validation and helper functions at the end of the file

**Note**: Using `guid` as the device UUID. Each UserAccount represents a separate connection/account.

```kotlin
// Add after existing extension functions (after isDemo())

fun UserAccount.isValidForWebSocketConnection(): Boolean {
    return dataFormat == Constants.SYNC_FORMAT_WEBSOCKET &&
            relayServer.isNotEmpty() &&
            license.isNotEmpty() &&
            guid.isNotEmpty()  // guid is the device UUID
}

fun UserAccount.getWebSocketUrl(): String {
    if (relayServer.isEmpty() || guid.isEmpty()) return ""

    val host = when {
        relayServer.startsWith("ws://") || relayServer.startsWith("wss://") -> relayServer
        else -> "wss://$relayServer"
    }

    val cleanHost = if (host.endsWith("/")) host.dropLast(1) else host
    // Use guid as device_uuid for WebSocket connection
    return "$cleanHost/ws/device?uuid=$guid&license=$license"
}

fun UserAccount.connectionSettingsChangedForWebSocket(account: UserAccount): Boolean {
    return this.guid != account.guid ||
            this.relayServer != account.relayServer ||
            this.license != account.license
}
```

**Test**: Build project successfully.

---

**Step 1.5 - Step 1.6: REMOVED** - No longer needed! The `UserAccount.guid` already serves as the device UUID.

---

## Phase 2: WebSocket Core Implementation (Day 3-5)

### Step 2.1: Create WebSocket Models
**File**: Create `app/src/main/java/ua/com/programmer/agentventa/data/remote/websocket/WebSocketModels.kt`

**Action**: Define data classes for WebSocket communication

```kotlin
package ua.com.programmer.agentventa.data.remote.websocket

import com.google.gson.JsonObject

sealed class ConnectionState {
    object Disconnected : ConnectionState()
    object Connecting : ConnectionState()
    object Connected : ConnectionState()
    data class Error(val message: String) : ConnectionState()
}

data class WebSocketMessage(
    val type: String,
    val message_id: String,
    val timestamp: String,
    val payload: JsonObject
)

data class WebSocketEnvelope(
    val type: String,
    val message_id: String,
    val timestamp: String,
    val payload: Map<String, Any>
)
```

**Test**: Build successfully.

---

### Step 2.2: Create WebSocketRepository Interface
**File**: Create `app/src/main/java/ua/com/programmer/agentventa/domain/repository/WebSocketRepository.kt`

**Action**: Define repository interface

```kotlin
package ua.com.programmer.agentventa.domain.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import ua.com.programmer.agentventa.data.local.entity.UserAccount
import ua.com.programmer.agentventa.data.remote.Result
import ua.com.programmer.agentventa.data.remote.websocket.ConnectionState
import ua.com.programmer.agentventa.data.remote.websocket.WebSocketMessage

interface WebSocketRepository {
    val connectionState: StateFlow<ConnectionState>
    val incomingMessages: Flow<WebSocketMessage>

    fun connect(account: UserAccount)
    fun disconnect()
    suspend fun sendMessage(type: String, payload: Map<String, Any>): Result<Unit>
    fun isConnected(): Boolean
}
```

**Test**: Build successfully.

---

### Step 2.3: Implement WebSocketRepositoryImpl (Part 1: Basic Structure)
**File**: Create `app/src/main/java/ua/com/programmer/agentventa/data/repository/WebSocketRepositoryImpl.kt`

**Action**: Create basic implementation structure

```kotlin
package ua.com.programmer.agentventa.data.repository

import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import ua.com.programmer.agentventa.data.local.entity.UserAccount
import ua.com.programmer.agentventa.data.local.entity.getWebSocketUrl
import ua.com.programmer.agentventa.data.local.entity.isValidForWebSocketConnection
import ua.com.programmer.agentventa.data.remote.Result
import ua.com.programmer.agentventa.data.remote.websocket.ConnectionState
import ua.com.programmer.agentventa.data.remote.websocket.WebSocketEnvelope
import ua.com.programmer.agentventa.data.remote.websocket.WebSocketMessage
import ua.com.programmer.agentventa.domain.repository.WebSocketRepository
import ua.com.programmer.agentventa.infrastructure.logger.Logger
import ua.com.programmer.agentventa.utility.Constants
import java.util.UUID
import javax.inject.Inject

class WebSocketRepositoryImpl @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val gson: Gson,
    private val logger: Logger
) : WebSocketRepository {

    private val scope = CoroutineScope(Dispatchers.IO + Job())

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    override val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _incomingMessages = MutableSharedFlow<WebSocketMessage>()
    override val incomingMessages: Flow<WebSocketMessage> = _incomingMessages.asSharedFlow()

    private var webSocket: WebSocket? = null
    private var currentAccount: UserAccount? = null
    private var reconnectAttempts = 0
    private var pingJob: Job? = null

    private val logTag = "WebSocketRepo"

    override fun connect(account: UserAccount) {
        if (!account.isValidForWebSocketConnection()) {
            logger.w(logTag, "Invalid WebSocket connection settings")
            _connectionState.value = ConnectionState.Error("Invalid connection settings")
            return
        }

        disconnect() // Close existing connection if any

        currentAccount = account
        reconnectAttempts = 0

        logger.d(logTag, "Connecting to WebSocket: ${account.relayServer}")
        _connectionState.value = ConnectionState.Connecting

        connectInternal(account)
    }

    private fun connectInternal(account: UserAccount) {
        val url = account.getWebSocketUrl()

        val request = Request.Builder()
            .url(url)
            .build()

        webSocket = okHttpClient.newWebSocket(request, createWebSocketListener())
    }

    override fun disconnect() {
        logger.d(logTag, "Disconnecting WebSocket")
        pingJob?.cancel()
        webSocket?.close(1000, "Client disconnect")
        webSocket = null
        currentAccount = null
        reconnectAttempts = 0
        _connectionState.value = ConnectionState.Disconnected
    }

    override suspend fun sendMessage(type: String, payload: Map<String, Any>): Result<Unit> {
        val ws = webSocket
        if (ws == null || !isConnected()) {
            return Result.Error("WebSocket not connected")
        }

        return try {
            val envelope = WebSocketEnvelope(
                type = type,
                message_id = UUID.randomUUID().toString(),
                timestamp = System.currentTimeMillis().toString(),
                payload = payload
            )

            val json = gson.toJson(envelope)
            val success = ws.send(json)

            if (success) {
                logger.d(logTag, "Sent message: type=$type")
                Result.Success(Unit)
            } else {
                logger.w(logTag, "Failed to send message: type=$type")
                Result.Error("Failed to send message")
            }
        } catch (e: Exception) {
            logger.e(logTag, "Error sending message: $e")
            Result.Error(e.message ?: "Unknown error")
        }
    }

    override fun isConnected(): Boolean {
        return connectionState.value is ConnectionState.Connected
    }

    // Will implement listener in next step
    private fun createWebSocketListener(): WebSocketListener {
        return object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                // Implement in Step 2.4
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                // Implement in Step 2.4
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                // Implement in Step 2.4
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                // Implement in Step 2.4
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                // Implement in Step 2.4
            }
        }
    }
}
```

**Test**: Build successfully (warnings about unimplemented methods are OK).

---

### Step 2.4: Implement WebSocket Listener Logic
**File**: `app/src/main/java/ua/com/programmer/agentventa/data/repository/WebSocketRepositoryImpl.kt`

**Action**: Complete the WebSocketListener implementation

```kotlin
// Replace the empty WebSocketListener methods with:

override fun onOpen(webSocket: WebSocket, response: Response) {
    logger.i(logTag, "WebSocket connected: ${response.code}")
    _connectionState.value = ConnectionState.Connected
    reconnectAttempts = 0
    startPingScheduler()
}

override fun onMessage(webSocket: WebSocket, text: String) {
    logger.d(logTag, "Received message: ${text.take(100)}")

    scope.launch {
        try {
            val message = gson.fromJson(text, WebSocketMessage::class.java)

            when (message.type) {
                Constants.WEBSOCKET_MESSAGE_TYPE_PING -> {
                    // Respond with pong
                    sendPong(message.message_id)
                }
                Constants.WEBSOCKET_MESSAGE_TYPE_PONG -> {
                    // Ignore, just keep-alive response
                    logger.d(logTag, "Received pong")
                }
                Constants.WEBSOCKET_MESSAGE_TYPE_DATA -> {
                    // Emit to flow for processing
                    _incomingMessages.emit(message)
                    // Send ACK
                    sendAck(message.message_id)
                }
                Constants.WEBSOCKET_MESSAGE_TYPE_ACK -> {
                    // Message delivery confirmed
                    logger.d(logTag, "Received ACK for: ${message.message_id}")
                }
                Constants.WEBSOCKET_MESSAGE_TYPE_ERROR -> {
                    logger.w(logTag, "Received error: ${message.payload}")
                }
            }
        } catch (e: Exception) {
            logger.e(logTag, "Error parsing message: $e")
        }
    }
}

override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
    logger.w(logTag, "WebSocket closing: code=$code, reason=$reason")
    webSocket.close(1000, null)
}

override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
    logger.w(logTag, "WebSocket closed: code=$code, reason=$reason")
    pingJob?.cancel()
    _connectionState.value = ConnectionState.Disconnected

    // Attempt reconnection if not manually disconnected
    if (currentAccount != null && code != 1000) {
        scheduleReconnect()
    }
}

override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
    logger.e(logTag, "WebSocket failure: ${t.message}")
    pingJob?.cancel()
    _connectionState.value = ConnectionState.Error(t.message ?: "Connection failed")

    // Attempt reconnection
    if (currentAccount != null) {
        scheduleReconnect()
    }
}

// Helper methods
private fun startPingScheduler() {
    pingJob?.cancel()
    pingJob = scope.launch {
        while (isConnected()) {
            delay(Constants.WEBSOCKET_PING_INTERVAL.toLong())
            sendPing()
        }
    }
}

private fun sendPing() {
    scope.launch {
        sendMessage(
            type = Constants.WEBSOCKET_MESSAGE_TYPE_PING,
            payload = emptyMap()
        )
    }
}

private fun sendPong(messageId: String) {
    scope.launch {
        sendMessage(
            type = Constants.WEBSOCKET_MESSAGE_TYPE_PONG,
            payload = mapOf("reply_to" to messageId)
        )
    }
}

private fun sendAck(messageId: String) {
    scope.launch {
        sendMessage(
            type = Constants.WEBSOCKET_MESSAGE_TYPE_ACK,
            payload = mapOf(
                "message_id" to messageId,
                "status" to "received"
            )
        )
    }
}

private fun scheduleReconnect() {
    val delay = calculateReconnectDelay()
    logger.i(logTag, "Scheduling reconnect in ${delay}ms (attempt ${reconnectAttempts + 1})")

    scope.launch {
        delay(delay)
        reconnectAttempts++
        currentAccount?.let { account ->
            logger.i(logTag, "Attempting reconnect...")
            _connectionState.value = ConnectionState.Connecting
            connectInternal(account)
        }
    }
}

private fun calculateReconnectDelay(): Long {
    val baseDelay = Constants.WEBSOCKET_RECONNECT_INITIAL_DELAY.toLong()
    val maxDelay = Constants.WEBSOCKET_RECONNECT_MAX_DELAY.toLong()
    val exponentialDelay = baseDelay * (1 shl reconnectAttempts.coerceAtMost(6))
    return exponentialDelay.coerceAtMost(maxDelay)
}
```

**Test**: Build successfully.

---

### Step 2.5: Add WebSocketRepository to Hilt
**File**: `app/src/main/java/ua/com/programmer/agentventa/di/NetworkModule.kt`

**Action**: Add WebSocket provider

```kotlin
// Add these imports at the top
import ua.com.programmer.agentventa.domain.repository.WebSocketRepository
import ua.com.programmer.agentventa.data.repository.WebSocketRepositoryImpl
import javax.inject.Singleton

// Add after existing @Provides methods

@Provides
@Singleton
fun provideWebSocketRepository(
    okHttpClient: OkHttpClient,
    logger: Logger
): WebSocketRepository {
    return WebSocketRepositoryImpl(
        okHttpClient = okHttpClient,
        gson = com.google.gson.Gson(),
        logger = logger
    )
}
```

**Test**: Build successfully.

---

## Phase 3: UI Integration (Day 6-7)

### Step 3.1: Update UserAccountViewModel
**File**: `app/src/main/java/ua/com/programmer/agentventa/presentation/features/settings/UserAccountViewModel.kt`

**Action**: Add support for WebSocket format and relay server field

Find the initialization section where `formatSpinner` is set up (around line 32):

```kotlin
// Update the format list to include WebSocket
formatSpinner.value = listOf(
    Constants.SYNC_FORMAT_HTTP,
    Constants.SYNC_FORMAT_WEBSOCKET  // ADD THIS
)

// Keep existing selectedFormat initialization
selectedFormat.value = Constants.SYNC_FORMAT_HTTP
```

Add new LiveData for relay server:

```kotlin
// Add after existing LiveData declarations
val relayServer = MutableLiveData<String>()
```

Update the `loadAccount()` method to load new field:

```kotlin
// Find loadAccount() method and add to the end:
relayServer.value = account.relayServer
```

Update `buildAccount()` method to include new field:

```kotlin
// Find buildAccount() method and update the UserAccount construction:
UserAccount(
    guid = accountGuid.value ?: "",
    // ... existing fields ...
    relayServer = relayServer.value ?: ""  // ADD THIS
)
```

**Note**: No need for device UUID management - the `guid` field already handles this!

**Test**: Build successfully.

---

### Step 3.2: Update UserAccountFragment Layout
**File**: `app/src/main/res/layout/fragment_user_account.xml`

**Action**: Add field for relay server only

Find the layout section with EditText fields and add after the `dbPassword` field:

```xml
<!-- Relay Server (WebSocket) -->
<com.google.android.material.textfield.TextInputLayout
    android:id="@+id/relay_server_layout"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:layout_marginTop="8dp"
    android:hint="@string/relay_server"
    app:layout_constraintTop_toBottomOf="@id/db_password_layout">

    <com.google.android.material.textfield.TextInputEditText
        android:id="@+id/relay_server"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:inputType="textUri"
        android:text="@={viewModel.relayServer}" />
</com.google.android.material.textfield.TextInputLayout>

<!-- Account GUID (Read-only, shows device UUID for WebSocket) -->
<com.google.android.material.textfield.TextInputLayout
    android:id="@+id/account_guid_layout"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:layout_marginTop="8dp"
    android:hint="@string/account_guid"
    app:layout_constraintTop_toBottomOf="@id/relay_server_layout">

    <com.google.android.material.textfield.TextInputEditText
        android:id="@+id/account_guid"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:enabled="false"
        android:text="@={viewModel.accountGuid}"
        android:textSize="12sp"
        android:fontFamily="monospace" />
</com.google.android.material.textfield.TextInputLayout>
```

**Note**: The account GUID field is read-only and displays the UUID that will be used for WebSocket connections. Users don't need to generate it - it's automatic when creating a new account.

**Test**: Build successfully, layout preview shows new fields.

---

### Step 3.3: Add String Resources
**File**: `app/src/main/res/values/strings.xml`

**Action**: Add new strings

```xml
<!-- Add these strings -->
<string name="relay_server">Relay Server (WebSocket)</string>
<string name="account_guid">Account GUID (Device UUID)</string>
<string name="connection_status">Connection Status</string>
<string name="ws_connected">WebSocket Connected</string>
<string name="ws_disconnected">WebSocket Disconnected</string>
<string name="ws_connecting">Connecting…</string>
<string name="ws_error">Connection Error</string>
<string name="test_connection">Test Connection</string>
```

**Test**: Build successfully.

---

### Step 3.4: Update UserAccountFragment Code
**File**: `app/src/main/java/ua/com/programmer/agentventa/presentation/features/settings/UserAccountFragment.kt`

**Action**: Wire up the new UI elements

Add in `onViewCreated()`:

```kotlin
// Add after existing view setup

// Show/hide fields based on selected format
viewModel.selectedFormat.observe(viewLifecycleOwner) { format ->
    val isWebSocket = format == Constants.SYNC_FORMAT_WEBSOCKET
    binding.relayServerLayout.visibility = if (isWebSocket) View.VISIBLE else View.GONE
    binding.accountGuidLayout.visibility = if (isWebSocket) View.VISIBLE else View.GONE

    // Hide HTTP-specific fields when WebSocket is selected
    binding.dbServerLayout.visibility = if (!isWebSocket) View.VISIBLE else View.GONE
    binding.dbNameLayout.visibility = if (!isWebSocket) View.VISIBLE else View.GONE
}
```

**Test**:
- Build and run app
- Navigate to User Account settings
- Switch between HTTP and WebSocket formats
- Verify fields show/hide correctly
- Verify account GUID is displayed (read-only)

---

## Phase 4: Basic Connection Testing (Day 8-9)

### Step 4.1: Create WebSocket Test Fragment
**File**: Create `app/src/main/java/ua/com/programmer/agentventa/presentation/features/websocket/WebSocketTestFragment.kt`

**Action**: Create a simple test UI

```kotlin
package ua.com.programmer.agentventa.presentation.features.websocket

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import ua.com.programmer.agentventa.databinding.FragmentWebsocketTestBinding
import ua.com.programmer.agentventa.data.remote.websocket.ConnectionState

@AndroidEntryPoint
class WebSocketTestFragment : Fragment() {

    private var _binding: FragmentWebsocketTestBinding? = null
    private val binding get() = _binding!!

    private val viewModel: WebSocketTestViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentWebsocketTestBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupObservers()
        setupListeners()
    }

    private fun setupObservers() {
        viewModel.connectionState.onEach { state ->
            updateConnectionStatus(state)
        }.launchIn(lifecycleScope)

        viewModel.messageLog.observe(viewLifecycleOwner) { messages ->
            binding.messageLogText.text = messages.joinToString("\n")
        }
    }

    private fun setupListeners() {
        binding.connectButton.setOnClickListener {
            viewModel.connect()
        }

        binding.disconnectButton.setOnClickListener {
            viewModel.disconnect()
        }

        binding.sendTestButton.setOnClickListener {
            viewModel.sendTestMessage()
        }

        binding.clearLogButton.setOnClickListener {
            viewModel.clearLog()
        }
    }

    private fun updateConnectionStatus(state: ConnectionState) {
        binding.connectionStatusText.text = when (state) {
            is ConnectionState.Connected -> "✓ Connected"
            is ConnectionState.Connecting -> "⟳ Connecting..."
            is ConnectionState.Disconnected -> "✗ Disconnected"
            is ConnectionState.Error -> "✗ Error: ${state.message}"
        }

        binding.connectButton.isEnabled = state is ConnectionState.Disconnected
        binding.disconnectButton.isEnabled = state is ConnectionState.Connected
        binding.sendTestButton.isEnabled = state is ConnectionState.Connected
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
```

**Test**: Build successfully (will have missing layout error - fix in next step).

---

### Step 4.2: Create WebSocket Test Layout
**File**: Create `app/src/main/res/layout/fragment_websocket_test.xml`

**Action**: Create test UI layout

```xml
<?xml version="1.0" encoding="utf-8"?>
<ScrollView xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:padding="16dp">

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="vertical">

        <TextView
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="WebSocket Test"
            android:textSize="24sp"
            android:textStyle="bold" />

        <TextView
            android:id="@+id/connection_status_text"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="16dp"
            android:text="✗ Disconnected"
            android:textSize="18sp" />

        <LinearLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="16dp"
            android:orientation="horizontal">

            <Button
                android:id="@+id/connect_button"
                android:layout_width="0dp"
                android:layout_height="wrap_content"
                android:layout_marginEnd="8dp"
                android:layout_weight="1"
                android:text="Connect" />

            <Button
                android:id="@+id/disconnect_button"
                android:layout_width="0dp"
                android:layout_height="wrap_content"
                android:layout_weight="1"
                android:enabled="false"
                android:text="Disconnect" />
        </LinearLayout>

        <Button
            android:id="@+id/send_test_button"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="8dp"
            android:enabled="false"
            android:text="Send Test Message" />

        <TextView
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:layout_marginTop="24dp"
            android:text="Message Log"
            android:textSize="18sp"
            android:textStyle="bold" />

        <Button
            android:id="@+id/clear_log_button"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:layout_marginTop="8dp"
            android:text="Clear Log"
            style="@style/Widget.MaterialComponents.Button.TextButton" />

        <TextView
            android:id="@+id/message_log_text"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="8dp"
            android:background="@android:color/darker_gray"
            android:fontFamily="monospace"
            android:padding="8dp"
            android:text="No messages"
            android:textSize="12sp" />
    </LinearLayout>
</ScrollView>
```

**Test**: Build successfully.

---

### Step 4.3: Create WebSocket Test ViewModel
**File**: Create `app/src/main/java/ua/com/programmer/agentventa/presentation/features/websocket/WebSocketTestViewModel.kt`

**Action**: Implement test logic

```kotlin
package ua.com.programmer.agentventa.presentation.features.websocket

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import ua.com.programmer.agentventa.data.remote.websocket.ConnectionState
import ua.com.programmer.agentventa.domain.repository.UserAccountRepository
import ua.com.programmer.agentventa.domain.repository.WebSocketRepository
import ua.com.programmer.agentventa.infrastructure.logger.Logger
import ua.com.programmer.agentventa.utility.Constants
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class WebSocketTestViewModel @Inject constructor(
    private val webSocketRepository: WebSocketRepository,
    private val userAccountRepository: UserAccountRepository,
    private val logger: Logger
) : ViewModel() {

    val connectionState: StateFlow<ConnectionState> = webSocketRepository.connectionState

    private val _messageLog = MutableLiveData<List<String>>(emptyList())
    val messageLog: LiveData<List<String>> = _messageLog

    private val dateFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    init {
        // Listen for incoming messages
        webSocketRepository.incomingMessages.onEach { message ->
            addToLog("← Received: type=${message.type}, id=${message.message_id}")
            logger.d("WSTest", "Received message: $message")
        }.launchIn(viewModelScope)
    }

    fun connect() {
        viewModelScope.launch {
            val account = userAccountRepository.getCurrentAccount()
            if (account != null && account.dataFormat == Constants.SYNC_FORMAT_WEBSOCKET) {
                addToLog("→ Connecting to: ${account.relayServer}")
                webSocketRepository.connect(account)
            } else {
                addToLog("✗ Error: No WebSocket account configured")
            }
        }
    }

    fun disconnect() {
        addToLog("→ Disconnecting...")
        webSocketRepository.disconnect()
    }

    fun sendTestMessage() {
        viewModelScope.launch {
            val payload = mapOf(
                "test" to "Hello from Android",
                "timestamp" to System.currentTimeMillis(),
                "data_type" to "test_message"
            )

            addToLog("→ Sending test message...")
            val result = webSocketRepository.sendMessage(
                type = Constants.WEBSOCKET_MESSAGE_TYPE_DATA,
                payload = payload
            )

            when {
                result is ua.com.programmer.agentventa.data.remote.Result.Success ->
                    addToLog("✓ Test message sent")
                result is ua.com.programmer.agentventa.data.remote.Result.Error ->
                    addToLog("✗ Send failed: ${result.message}")
            }
        }
    }

    fun clearLog() {
        _messageLog.value = emptyList()
    }

    private fun addToLog(message: String) {
        val timestamp = dateFormat.format(Date())
        val logEntry = "[$timestamp] $message"
        _messageLog.value = (_messageLog.value ?: emptyList()) + logEntry
    }
}
```

**Test**: Build successfully.

---

### Step 4.4: Add Test Fragment to Navigation
**File**: `app/src/main/res/navigation/navigation.xml`

**Action**: Add test fragment to nav graph

```xml
<!-- Add this fragment definition -->
<fragment
    android:id="@+id/webSocketTestFragment"
    android:name="ua.com.programmer.agentventa.presentation.features.websocket.WebSocketTestFragment"
    android:label="WebSocket Test"
    tools:layout="@layout/fragment_websocket_test" />
```

**Test**: Build successfully.

---

### Step 4.5: Add Menu Item to Access Test Screen
**File**: Find the main menu file (likely `app/src/main/res/menu/main_menu.xml` or similar)

**Action**: Add test menu item

```xml
<item
    android:id="@+id/action_websocket_test"
    android:title="WebSocket Test"
    android:icon="@android:drawable/ic_menu_send" />
```

In MainActivity or the fragment that handles menu:

```kotlin
R.id.action_websocket_test -> {
    findNavController().navigate(R.id.webSocketTestFragment)
    true
}
```

**Test**: Build and run, verify menu item appears.

---

## Phase 5: Testing Checklist

### Test 5.1: Database Migration
- [ ] Uninstall app completely
- [ ] Install new version
- [ ] Verify app starts without crashes
- [ ] Check logcat for successful migration message

### Test 5.2: Account GUID Display
- [ ] Open User Account settings
- [ ] Create new account (auto-generates GUID)
- [ ] Switch to "WebSocket_relay" format
- [ ] Verify Account GUID field shows the account's UUID
- [ ] Verify field is read-only (grayed out)
- [ ] Save and reopen account
- [ ] Verify GUID persists and matches

### Test 5.3: User Account Configuration
- [ ] Create new WebSocket account with:
  - Format: WebSocket_relay
  - Relay Server: your-server-domain.com (or IP:port for testing)
  - License: test-license-key
  - Account GUID: (auto-generated, read-only)
- [ ] Save account
- [ ] Verify account saves successfully
- [ ] Set as current account
- [ ] Note the Account GUID for server-side verification

### Test 5.4: WebSocket Connection
- [ ] Navigate to WebSocket Test screen
- [ ] Click "Connect" button
- [ ] Verify status changes to "Connecting..."
- [ ] Verify status changes to "Connected" (if server is running)
- [ ] Check server logs for connection from account GUID
- [ ] Verify server shows correct device UUID (UserAccount.guid)
- [ ] Check app logcat for connection messages

### Test 5.5: Send Test Message
- [ ] After connection established
- [ ] Click "Send Test Message"
- [ ] Verify message appears in log
- [ ] Check server logs for received message
- [ ] Verify ACK received (check logs)

### Test 5.6: Ping/Pong
- [ ] Stay connected for >30 seconds
- [ ] Check logs for ping/pong messages
- [ ] Verify connection stays alive

### Test 5.7: Disconnection
- [ ] Click "Disconnect" button
- [ ] Verify status changes to "Disconnected"
- [ ] Check server logs for disconnect event

### Test 5.8: Reconnection
- [ ] Connect to server
- [ ] Stop server (or disable network on device)
- [ ] Observe reconnection attempts in logs
- [ ] Restart server
- [ ] Verify app reconnects automatically
- [ ] Check exponential backoff (1s, 2s, 4s, 8s...)

### Test 5.9: Error Handling
- [ ] Try to connect with invalid server address
- [ ] Verify error message displayed
- [ ] Try to connect with invalid license
- [ ] Verify server rejects connection (401)
- [ ] Verify error shown in UI

### Test 5.10: Multiple Accounts (Multi-Connection Support)
- [ ] Create first WebSocket account (Account A)
  - Note its GUID (e.g., "guid-aaa")
- [ ] Create second WebSocket account (Account B)
  - Note its GUID (e.g., "guid-bbb")
- [ ] Set Account A as current
- [ ] Connect via WebSocket
- [ ] Verify server registers connection with "guid-aaa"
- [ ] Switch to Account B (set as current)
- [ ] Disconnect from Account A
- [ ] Connect via WebSocket
- [ ] Verify server registers NEW connection with "guid-bbb"
- [ ] Verify both accounts can maintain separate data in local database
- [ ] Verify `db_guid` in Orders/Clients tables matches respective account GUIDs

---

## Phase 6: Integration with NetworkRepository (Future)

**Note**: After basic testing is successful, implement in separate phase:

### Step 6.1: Modify NetworkRepositoryImpl
- Add WebSocketRepository injection
- Check `UserAccount.dataFormat` in sync methods
- Route to WebSocket or HTTP based on format

### Step 6.2: Implement sendDocumentsViaWebSocket()
- Serialize Order, Cash, Image documents
- Send via WebSocket instead of HTTP POST
- Handle ACKs and update `isSent` flags

### Step 6.3: Implement receiveDataViaWebSocket()
- Listen for incoming catalog updates
- Parse and save to Room database
- Update UI via existing LiveData/Flow

---

## Expected Outcome After Phase 1-5

✅ **What Should Work**:
1. Database migrates successfully to v21 (adds `relay_server` field)
2. Each UserAccount has unique GUID (serves as device UUID)
3. WebSocket account can be created and saved
4. Multiple WebSocket accounts supported (different GUIDs)
5. WebSocket connection establishes using account GUID as device UUID
6. Test messages send and receive ACKs
7. Ping/pong keeps connection alive
8. Automatic reconnection works
9. Connection state visible in UI
10. Message log shows all activity
11. Account switching disconnects old connection and can connect new one
12. Server sees distinct device UUIDs for each account

❌ **What Won't Work Yet**:
1. Actual document sync (orders, cash, etc.)
2. Catalog updates via WebSocket
3. Background service for persistent connection
4. Integration with existing sync UI
5. FCM push notifications

---

## Troubleshooting Guide

### Issue: Build fails with Room compiler errors
**Solution**: Clean project (`Build > Clean Project`), then rebuild

### Issue: App crashes on migration
**Solution**:
- Uninstall app completely
- Check migration SQL syntax
- Verify database version incremented

### Issue: WebSocket connection fails
**Solution**:
- Check server is running
- Verify server address in UserAccount
- Check license key is valid
- Review server logs for error
- Check Android logcat for exceptions

### Issue: No messages received
**Solution**:
- Verify WebSocket is connected (check status)
- Check server queue has messages for device UUID
- Review message format on server side
- Check Gson parsing (add debug logs)

### Issue: Connection drops frequently
**Solution**:
- Check network stability
- Verify ping/pong working (check logs)
- Increase ping interval if needed
- Check server timeout settings

---

## Development Tips

1. **Use Logcat Filtering**: Filter by "WebSocketRepo" and "WSTest" tags
2. **Server Logs**: Monitor server logs in parallel during testing
3. **Network Inspector**: Use Charles Proxy or similar to inspect WebSocket traffic
4. **Incremental Testing**: Test each step before moving to next
5. **Database Inspection**: Use Database Inspector in Android Studio
6. **Emulator vs Device**: Test on both (emulator easier for debugging)

---

## Next Steps After Successful Testing

1. Implement document synchronization via WebSocket
2. Create foreground service for persistent connection
3. Add connection status indicator to main UI
4. Implement background sync on reconnect
5. Add settings sync feature
6. Implement FCM push notifications
7. Production hardening and error handling
8. Performance optimization
9. Documentation and user guide

---

## Estimated Timeline

- **Phase 1** (Foundation): 2 days
- **Phase 2** (WebSocket Core): 3 days
- **Phase 3** (UI Integration): 2 days
- **Phase 4** (Testing): 2 days
- **Total**: ~9 days for basic functional implementation

**Buffer**: Add 20% for unexpected issues = **~11 days total**

---

## Success Criteria

✅ Implementation is successful when:
1. All tests in Phase 5 checklist pass
2. WebSocket connects to server reliably
3. Messages send and receive successfully
4. Reconnection works automatically
5. No crashes or memory leaks
6. Server logs show correct device activity
7. Ready for integration with sync logic

---

## Files Modified Summary

### New Files (7):
1. `WebSocketModels.kt`
2. `WebSocketRepository.kt`
3. `WebSocketRepositoryImpl.kt`
4. `WebSocketTestFragment.kt`
5. `WebSocketTestViewModel.kt`
6. `fragment_websocket_test.xml`
7. `ANDROID_WEBSOCKET_IMPLEMENTATION_PLAN.md` (this file)

### Modified Files (8):
1. `Constants.java` - Added WebSocket constants
2. `UserAccount.kt` - Added `relayServer` field + extension functions
3. `AppDatabase.kt` - Added migration 20→21 (single field)
4. `NetworkModule.kt` - Added WebSocketRepository provider
5. `UserAccountViewModel.kt` - Added relay server support
6. `fragment_user_account.xml` - Added relay server field and GUID display
7. `strings.xml` - Added new string resources
8. `navigation.xml` - Added test fragment
9. Menu file - Added test screen menu item

### Total Impact:
- ~1200 lines of new code
- 9 existing files modified
- 7 new files created
- 1 database migration (adds 1 column)
- No breaking changes to existing functionality
- **Key**: Reuses existing `UserAccount.guid` as device UUID (no separate UUID management needed)
