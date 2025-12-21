# Device Connection API (WebSocket)

Complete WebSocket API reference for Android device connections.

## Overview

The Device Connection API uses WebSocket for real-time bidirectional communication between Android devices and the relay server. Devices connect via WebSocket and exchange messages in JSON format.

## Base URL

- **Development**: `ws://localhost:9880/ws/device`
- **Production**: `wss://relay.example.com/ws/device`

## Authentication

### Connection Authentication

Devices authenticate using the `Authorization` header during WebSocket handshake:

```
Authorization: Bearer <API_KEY>:<DEVICE_UUID>
```

**Format**: `Bearer {API_KEY}:{DEVICE_UUID}`

**Example**:
```
Authorization: Bearer abc123def456ghi789:550e8400-e29b-41d4-a716-446655440000
```

### Authentication Flow

1. Device connects to WebSocket endpoint
2. Server validates API key against configured keys
3. Server looks up device by UUID
4. If device doesn't exist, creates pending device
5. If device is pending, server sends error message with `status: "pending"` and closes connection
6. Server validates license status and device limits
7. Connection established if all checks pass

**Note**: All server-to-device messages include a `status` field with the current device status (`pending`, `approved`, or `denied`). Devices should check this field to determine their current state.

### Error Responses

**401 Unauthorized** - Invalid API key:
```json
{
  "error": "Invalid API key"
}
```

**403 Forbidden** - Device access denied:
```json
{
  "error": "Device access denied"
}
```

**403 Forbidden** - Device limit reached:
```json
{
  "error": "Device limit reached"
}
```

**WebSocket Error Message** - Device pending (sent after WebSocket upgrade, then connection closed):
```json
{
  "type": "error",
  "message_id": "",
  "timestamp": "2025-01-15T10:30:00Z",
  "status": "pending",
  "payload": {
    "error": "Device is pending approval"
  }
}
```

**Note**: When a device is in pending state, the WebSocket connection is upgraded successfully, but an error message is immediately sent with `status: "pending"` and the connection is closed. The device should check the `status` field in the error message to determine its pending status.

---

## Message Format

All WebSocket messages use a standard JSON envelope:

```json
{
  "type": "data",
  "message_id": "msg-12345",
  "timestamp": "2025-01-15T10:30:00Z",
  "status": "approved",
  "payload": {
    // Message-specific payload
  }
}
```

### Message Fields

- `type` (string, required): Message type (`data`, `ack`, `ping`, `pong`, `error`)
- `message_id` (string, required): Unique message identifier
- `timestamp` (string, required): ISO 8601 timestamp
- `status` (string, optional): Current device status (`pending`, `approved`, `denied`). Always included in server-to-device messages.
- `payload` (object, required): Message payload (varies by type)

---

## Message Types

### Data Messages

Send data from device to accounting system.

**Type**: `data`

**Payload**:
```json
{
  "data_type": "order",
  "data": {
    "order_id": "12345",
    "client_guid": "client-789",
    "total": 15000.50,
    "content": [
      {
        "product_guid": "prod-001",
        "quantity": 2,
        "price": 7500.25
      }
    ]
  }
}
```

**Supported Data Types**:
- `order` - Order data
- `cash` - Cash document
- `client_image` - Client image
- `location` - Location data
- `catalog` - Catalog updates
- `settings` - Settings synchronization

**Response**: ACK message
```json
{
  "type": "ack",
  "message_id": "msg-12345",
  "timestamp": "2025-01-15T10:30:05Z",
  "status": "approved",
  "payload": {
    "status": "received"
  }
}
```

### ACK Messages

Acknowledge receipt of messages from server.

**Type**: `ack`

**Payload**:
```json
{
  "status": "received"
}
```

**Usage**: Send ACK after receiving data messages from server.

### Ping/Pong Messages

Keep-alive messages for connection health.

**Type**: `ping` (client → server) or `pong` (server → client)

**Payload**:
```json
{}
```

**Usage**: 
- Server sends ping every 30 seconds (configurable)
- Client should respond with pong
- Client can send ping to check connection

### Settings Synchronization

#### Upload Settings

**Type**: `data` with `data_type: "settings"`

**Payload**:
```json
{
  "data_type": "settings",
  "user_email": "user@example.com",
  "options": {
    "write": true,
    "read": true,
    "loadImages": true,
    "useCompanies": true,
    "useStores": true,
    "clientsLocations": true,
    "fiscalProvider": "Checkbox",
    "fiscalProviderId": "provider-123",
    "fiscalDeviceId": "device-456",
    "fiscalCashierPin": "1234"
  }
}
```

**Response**: ACK message

#### Sync Settings Request

**Type**: `sync_settings`

**Payload**:
```json
{
  "user_email": "user@example.com"
}
```

**Response**: Data message with settings
```json
{
  "type": "data",
  "message_id": "sync-001",
  "timestamp": "2025-01-15T10:30:00Z",
  "status": "approved",
  "payload": {
    "data_type": "settings",
    "data": {
      "user_email": "user@example.com",
      "options": {
        "write": true,
        "read": true
      },
      "found": true,
      "updated_at": "2025-01-15T10:30:00Z"
    }
  }
}
```

If settings not found:
```json
{
  "type": "data",
  "message_id": "sync-001",
  "timestamp": "2025-01-15T10:30:00Z",
  "status": "approved",
  "payload": {
    "data_type": "settings",
    "data": {
      "user_email": "user@example.com",
      "options": {},
      "found": false
    }
  }
}
```

### Error Messages

**Type**: `error`

**Payload**:
```json
{
  "error": "Error message description"
}
```

**Example Error Message**:
```json
{
  "type": "error",
  "message_id": "",
  "timestamp": "2025-01-15T10:30:00Z",
  "status": "pending",
  "payload": {
    "error": "Device is pending approval"
  }
}
```

**Common Error Values**:
- `"Device is pending approval"` - Device status is pending (check `status` field)
- `"Invalid message format"` - Message JSON parsing failed
- `"Missing data_type in payload"` - Required data_type field missing
- `"Device is not approved for data transfer"` - Device pending or denied
- `"Device access has been denied"` - Device status is denied

**Note**: The `status` field in error messages always contains the current device status (`pending`, `approved`, or `denied`). When `status: "pending"`, the connection will be closed after sending the error message.

---

## Connection Lifecycle

### 1. Connection Establishment

```javascript
const ws = new WebSocket('wss://relay.example.com/ws/device', {
  headers: {
    'Authorization': 'Bearer API_KEY:DEVICE_UUID'
  }
});

ws.onopen = () => {
  console.log('Connected');
  // Start sending ping messages
  setInterval(() => {
    ws.send(JSON.stringify({
      type: 'ping',
      message_id: `ping-${Date.now()}`,
      timestamp: new Date().toISOString(),
      payload: {}
    }));
  }, 30000);
};

// Important: Check for pending status immediately after connection
ws.onmessage = (event) => {
  const message = JSON.parse(event.data);
  
  // Check if device is pending (check status field)
  if (message.type === 'error' && message.status === 'pending') {
    console.log('Device is pending approval');
    // Handle pending state - wait for admin approval
    ws.close();
    return;
  }
  
  // Handle other messages...
  // Note: All messages include a 'status' field with current device status
};
```

### 2. Receiving Messages

```javascript
ws.onmessage = (event) => {
  const message = JSON.parse(event.data);
  
  switch (message.type) {
    case 'data':
      // Handle data message
      handleDataMessage(message);
      // Send ACK
      sendACK(message.message_id);
      break;
    case 'pong':
      // Server responded to ping
      break;
    case 'error':
      // Handle error
      if (message.status === 'pending') {
        console.log('Device is pending approval');
        // Device is pending - wait for admin approval before reconnecting
        ws.close();
      } else {
        console.error('Error:', message.payload.error, 'Status:', message.status);
      }
      break;
  }
};
```

### 3. Sending Messages

```javascript
function sendDataMessage(dataType, data) {
  const message = {
    type: 'data',
    message_id: `msg-${Date.now()}-${Math.random()}`,
    timestamp: new Date().toISOString(),
    payload: {
      data_type: dataType,
      data: data
    }
  };
  
  ws.send(JSON.stringify(message));
}

function sendACK(messageId) {
  const ack = {
    type: 'ack',
    message_id: messageId,
    timestamp: new Date().toISOString(),
    payload: {
      status: 'received'
    }
  };
  
  ws.send(JSON.stringify(ack));
}
```

### 4. Connection Cleanup

```javascript
ws.onclose = () => {
  console.log('Disconnected');
  // Implement reconnection logic with exponential backoff
};

ws.onerror = (error) => {
  console.error('WebSocket error:', error);
};
```

---

## Message Delivery

### Incoming Messages (Server → Device)

When the accounting system sends data via REST API (`POST /api/v1/push`), the server:
1. Stores message in queue
2. When device connects, delivers pending messages
3. Waits for ACK from device
4. Marks message as delivered

**Message Delivery Order**: FIFO (First In, First Out)

**Pending Messages**: Messages are delivered automatically when device connects.

### Outgoing Messages (Device → Server)

When device sends data via WebSocket:
1. Server validates message format
2. Server stores in outgoing queue
3. Server sends ACK to device
4. Accounting system retrieves via `GET /api/v1/pull`

---

## Rate Limiting

- **Connection Rate**: 10 connections per second per device UUID
- **Message Rate**: No explicit limit (limited by connection capacity)

**Rate Limit Exceeded Response**:
```
HTTP 429 Too Many Requests
```

---

## Connection Configuration

### Ping Interval

Server sends ping messages every 30 seconds (configurable via `ws_ping_interval_seconds`).

### Pong Timeout

Client must respond to ping within 60 seconds (configurable).

### Max Message Size

Maximum message size: 10MB (configurable via `ws_max_message_size`).

### Connection Timeout

- **Read Timeout**: 60 seconds
- **Write Timeout**: 10 seconds

---

## Error Handling

### Connection Errors

| Error | Description | Action |
|-------|-------------|--------|
| `Invalid API key` | API key doesn't match | Check API key configuration |
| `Device is pending approval` | Device status is "pending" (check `status` field in error message) | Wait for admin approval, then reconnect |
| `Device access denied` | Device status is "denied" | Contact administrator |
| `Device limit reached` | License device limit exceeded | Upgrade license or remove devices |
| `Invalid license` | License not found or inactive | Verify license status |
| `Device not properly registered` | Device registration failed | Retry connection |

**Note**: Error messages include a `status` field indicating the current device status. Check `message.status === "pending"` to detect pending devices.

### Message Errors

| Error | Description | Action |
|-------|-------------|--------|
| `Invalid message format` | JSON parsing failed | Check message structure |
| `Missing data_type in payload` | Required field missing | Include data_type in payload |
| `Missing or invalid data in payload` | Data field missing or invalid | Check data structure |
| `Device is not approved for data transfer` | Device pending or denied | Wait for approval or contact admin |

---

## Best Practices

### 1. Connection Management

- Implement automatic reconnection with exponential backoff
- Handle connection drops gracefully
- Monitor connection state

### 2. Message Handling

- Always send ACK for received messages
- Use unique message IDs
- Include timestamps in all messages
- Validate message format before sending

### 3. Error Handling

- Implement retry logic for failed messages
- Log errors for debugging
- Handle rate limiting gracefully

### 4. Performance

- Batch messages when possible
- Use compression if available
- Monitor message queue size
- Clean up old messages

---

## Example: Complete Android Implementation

```kotlin
class WebSocketClient {
    private var webSocket: WebSocket? = null
    private val client = OkHttpClient()
    
    fun connect(apiKey: String, deviceUUID: String) {
        val request = Request.Builder()
            .url("wss://relay.example.com/ws/device")
            .addHeader("Authorization", "Bearer $apiKey:$deviceUUID")
            .build()
        
        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                // Start ping interval
                startPingInterval()
                // Request pending messages
                requestPendingMessages()
            }
            
            override fun onMessage(webSocket: WebSocket, text: String) {
                val message = Gson().fromJson(text, WebSocketMessage::class.java)
                handleMessage(message)
            }
            
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                // Implement reconnection logic
                reconnect()
            }
        })
    }
    
    private fun handleMessage(message: WebSocketMessage) {
        when (message.type) {
            "data" -> {
                // Process data
                processData(message.payload)
                // Send ACK
                sendACK(message.messageId)
            }
            "pong" -> {
                // Server responded to ping
            }
            "error" -> {
                val error = message.payload["error"] as String
                val status = message.status as? String
                if (status == "pending") {
                    // Device is pending approval
                    handlePendingState()
                    webSocket?.close(1000, "Device pending approval")
                } else {
                    // Handle other errors
                    handleError(error)
                }
            }
        }
    }
    
    private fun handlePendingState() {
        // Device is pending - wait for admin approval
        // Implement logic to check device status periodically
        // or wait for push notification before reconnecting
    }
    
    private fun sendDataMessage(dataType: String, data: Map<String, Any>) {
        val message = WebSocketMessage(
            type = "data",
            messageId = UUID.randomUUID().toString(),
            timestamp = Instant.now().toString(),
            payload = mapOf(
                "data_type" to dataType,
                "data" to data
            )
        )
        webSocket?.send(Gson().toJson(message))
    }
    
    private fun sendACK(messageId: String) {
        val ack = WebSocketMessage(
            type = "ack",
            messageId = messageId,
            timestamp = Instant.now().toString(),
            payload = mapOf("status" to "received")
        )
        webSocket?.send(Gson().toJson(ack))
    }
}
```

---

## Related Documentation

- [1C Base Connection API](API_1C_CONNECTION.md) - REST API for accounting systems
- [Admin API](ADMIN_API.md) - Admin interface API
- [Testing Guide](TESTING.md) - Testing and debugging

