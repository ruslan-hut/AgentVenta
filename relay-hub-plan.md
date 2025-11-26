# Relay Server Development Plan

## 1. Architecture Overview

The Relay Server acts as a bridge between the accounting system (ERP, 1C, etc.) and Android mobile clients. It handles data routing, buffering, and synchronization through **dual communication channels**: existing REST API and new WebSocket relay.

### Components
- **API Gateway (REST)** – Receives data from the accounting system and serves HTTP-based sync (existing)
- **WebSocket Relay** – Real-time bidirectional communication with Android clients (new)
- **MongoDB** – NoSQL database for queues, devices, licenses, and settings
- **Connection Manager** – Handles device registration, connection state, and routing logic

### Data Flow

**HTTP Mode (Existing):**
1. Android client connects via Retrofit to 1C HTTP API (`http://{server}/{db_name}/hs/dex/`)
2. Direct synchronous request-response pattern
3. Token-based authentication with Basic Auth credentials
4. Polling-based updates (manual sync by user)

**WebSocket Mode (New):**
1. Accounting system sends data to relay server via REST API → stored in queue (`ready=false`, `device_uuid`)
2. Server monitors active WebSocket connections; when matching device is online → sends queued message
3. Device sends ACK → record marked `ready=true` → later removed from queue
4. Reverse: device sends data via WebSocket → stored in outgoing queue → accounting system fetches via REST

### Hybrid Operation
- Android app supports **both modes** simultaneously via `UserAccount.dataFormat` field
- Existing: `"HTTP_service"` for direct 1C connection
- New: `"WebSocket_relay"` for relay server connection
- User selects mode in account settings

---

## 2. Data Model (MongoDB)

### Collections

**licenses**
```javascript
{
  _id: ObjectId,
  license_key: String,        // Unique license identifier
  secret_hash: String,         // Hashed authentication secret
  expiration_date: ISODate,
  device_limit: Number,
  created_at: ISODate,
  updated_at: ISODate,
  status: String,              // "active" | "expired" | "suspended"
  parameters: Object           // Custom configuration per license
}
```

**devices**
```javascript
{
  _id: ObjectId,
  uuid: String,                // Android device UUID (unique)
  license_id: ObjectId,        // FK to licenses
  connection_state: String,    // "online" | "offline"
  last_seen: ISODate,
  first_connected: ISODate,
  device_info: {               // Optional device metadata
    model: String,
    android_version: String,
    app_version: String
  },
  db_guid: String              // Links to UserAccount.guid in Android app
}
```

**queue** (incoming: accounting → device)
```javascript
{
  _id: ObjectId,
  device_uuid: String,         // Target device
  data: Object,                // Payload (order, catalog updates, etc.)
  data_type: String,           // "order" | "catalog" | "settings" | "message"
  ready: Boolean,              // false=pending, true=delivered
  created_at: ISODate,
  delivered_at: ISODate,
  ttl: ISODate                 // TTL index for auto-cleanup (24h default)
}
```

**outgoing_queue** (device → accounting)
```javascript
{
  _id: ObjectId,
  device_uuid: String,
  license_id: ObjectId,
  data: Object,                // Order, Cash, Image, Location
  data_type: String,           // Maps to Android Constants: "order", "cash", "client_image", "location"
  created_at: ISODate,
  fetched: Boolean,            // false=pending, true=retrieved by accounting
  fetched_at: ISODate,
  ttl: ISODate                 // TTL index (7 days default)
}
```

**settings**
```javascript
{
  _id: ObjectId,
  device_uuid: String,
  user_email: String,          // Google OAuth email for recovery
  options: Object,             // UserOptions JSON (maps to Android UserAccount.options)
  updated_at: ISODate
}
```

### Indexes
- `devices.uuid`: unique index
- `queue.device_uuid + ready`: compound index
- `queue.ttl`: TTL index (expireAfterSeconds: 0)
- `outgoing_queue.license_id + fetched`: compound index
- `outgoing_queue.ttl`: TTL index
- `licenses.license_key`: unique index

---

## 3. REST API (Accounting System ↔ Relay Server)

### Authentication
- **Header**: `Authorization: Bearer {license_key}:{secret}`
- Validate license status and expiration on each request

### Endpoints

**POST /api/v1/push**
- **Purpose**: Accounting system sends data to device
- **Body**:
  ```json
  {
    "device_uuid": "abc-123-def",
    "data_type": "order",
    "data": { /* order payload */ }
  }
  ```
- **Response**: `{ "queued": true, "queue_id": "..." }`
- **Logic**: Insert into `queue` collection with `ready=false`

**GET /api/v1/pull?license_key={key}&limit=50**
- **Purpose**: Accounting system retrieves device data
- **Response**:
  ```json
  {
    "count": 3,
    "items": [
      { "device_uuid": "...", "data_type": "order", "data": {...}, "created_at": "..." }
    ]
  }
  ```
- **Logic**: Find all `outgoing_queue` records for license where `fetched=false`, mark as `fetched=true`

**POST /api/v1/license**
- **Purpose**: Register or update license
- **Body**: `{ "license_key": "...", "secret": "...", "device_limit": 10, "expiration_date": "..." }`
- **Response**: `{ "created": true, "license_id": "..." }`

**GET /api/v1/devices?license_key={key}**
- **Purpose**: List all devices for license
- **Response**: Array of device objects with connection state

**DELETE /api/v1/devices/{uuid}**
- **Purpose**: Unbind device from license
- **Response**: `{ "deleted": true }`

---

## 4. WebSocket Relay (Android ↔ Relay Server)

### Connection Protocol

**WebSocket URL**: `wss://{relay_server_host}/ws/device?uuid={device_uuid}&license={license_key}`

**Connection Flow**:
1. Device generates persistent UUID on first app launch (stored in SharedPreferences)
2. When `UserAccount.dataFormat = "WebSocket_relay"`, Android connects via OkHttp WebSocket
3. Handshake validates license key and device binding
4. Server updates `devices.connection_state = "online"`, `last_seen = now()`
5. Connection stored in active connections map: `Map<String, WebSocket>`

### Message Format

**JSON envelope**:
```json
{
  "type": "data" | "ack" | "ping" | "pong" | "error",
  "message_id": "unique-msg-id",
  "timestamp": "2025-01-15T10:30:00Z",
  "payload": { /* type-specific data */ }
}
```

**Message Types**:
- `data`: Actual business data (order, catalog, etc.)
- `ack`: Acknowledgment of received message
- `ping`/`pong`: Keep-alive (30s interval)
- `error`: Error notifications

### Message Routing Logic

**Server → Device (Incoming)**:
1. Accounting system calls `POST /api/v1/push` → creates queue record
2. Server monitors `queue` collection (change stream or polling)
3. When `queue.device_uuid` matches online device → send via WebSocket
4. Device processes and sends `ack` message with `message_id`
5. Server marks `queue.ready = true`, schedules deletion (1h cleanup job)

**Device → Server (Outgoing)**:
1. Device sends document (order, cash, image) via WebSocket
2. Server validates and inserts into `outgoing_queue`
3. Server sends `ack` back to device
4. Device marks local `isSent = 1` in Room database
5. Accounting system fetches via `GET /api/v1/pull`

### Reconnection Handling

**Client-side (Android)**:
- Exponential backoff: 1s, 2s, 4s, 8s, max 60s
- Persist connection state in ViewModel
- On reconnect: request missed messages via `sync` message type
- Display connection status in UI (online/offline indicator)

**Server-side**:
- Detect disconnect via WebSocket close/error events
- Update `devices.connection_state = "offline"`
- Queue messages accumulate until reconnection
- On reconnect: send all pending messages (`ready=false`)

### Message Ordering

- FIFO per device: messages sorted by `created_at`
- Single-threaded delivery per device (no concurrent sends)
- Retry failed delivery after timeout (30s), max 3 attempts

---

## 5. Reverse Channel (Device → Accounting System)

### Data Types Supported

From Android `Constants.java`:
- `DOCUMENT_ORDER = "order"` (Order entity with OrderContent)
- `DOCUMENT_CASH = "cash"` (Cash receipt)
- `DATA_CLIENT_IMAGE = "client_image"` (Base64 encoded photo)
- `DATA_CLIENT_LOCATION = "client_location"` (GPS coordinates with timestamp)
- `DATA_LOCATION = "location"` (LocationHistory entry)

### Synchronization Flow

1. Device creates document offline → stored in Room with `isSent = 0`
2. When connected (HTTP or WebSocket):
   - **HTTP Mode**: `NetworkRepositoryImpl.sendDocuments()` uploads via Retrofit
   - **WebSocket Mode**: Serialize to JSON and send via WebSocket
3. Server receives and stores in `outgoing_queue`
4. Server sends `ack` → device marks `isSent = 1`
5. Accounting system polls `GET /api/v1/pull` → retrieves and processes
6. After processing, delete from `outgoing_queue` (or mark `fetched=true` + TTL cleanup)

### Data Transformation

**Android → Server**:
- Use existing `toMap()` extension functions from entities
- Gson serialization (already in `NetworkRepositoryImpl`)
- Include `db_guid` for multi-account support

**Server → 1C**:
- Return data in same JSON format expected by 1C `/hs/dex/post` endpoint
- Preserve field names and structure from current HTTP API

---

## 6. License & Device Management

### License Model

- **Expiration**: Checked on each API call and WebSocket connect
- **Device Limit**: Enforced on device registration (reject if limit reached)
- **Parameters**: Custom config per license (forwarded to device as `UserAccount.options`)

### Device Binding

**First Connection**:
1. Device connects with UUID + license key
2. Server checks:
   - License exists and is active
   - Device limit not exceeded
   - UUID not already bound to different license
3. If valid: Create `devices` record, allow connection
4. If invalid: Reject with error message

**Unbinding**:
- Admin can call `DELETE /api/v1/devices/{uuid}`
- Disconnect active WebSocket
- Remove from `devices` collection
- Free slot for new device

### Admin Endpoints

**GET /admin/licenses**
- List all licenses with device counts and status

**POST /admin/licenses**
- Create new license

**PUT /admin/licenses/{id}**
- Update expiration, device limit, parameters

**GET /admin/devices?license_key={key}**
- List devices for license with connection state

**DELETE /admin/devices/{uuid}**
- Unbind device

---

## 7. Settings Synchronization

### Android Settings Storage

**Current Structure** (`UserAccount.options` JSON):
```json
{
  "write": true,
  "read": true,
  "loadImages": true,
  "useCompanies": true,
  "useStores": true,
  "clientsLocations": true,
  "fiscalProvider": "Checkbox",
  "fiscalProviderId": "...",
  "fiscalDeviceId": "...",
  "fiscalCashierPin": "..."
}
```

Parsed to `UserOptions` data class via `UserOptionsBuilder.build()`.

### Cloud Sync Flow

**Upload to Cloud**:
1. User modifies settings in `UserAccountFragment`
2. Settings saved to local Room database
3. If WebSocket connected: Send `settings` message type
4. Server stores in `settings` collection linked to `device_uuid` + `user_email`

**Restore from Cloud**:
1. User logs in with Google OAuth (Firebase Auth)
2. Device sends `sync_settings` request with email
3. Server returns stored settings
4. Android merges with local `UserAccount.options`

### Google OAuth Integration

**Android Side**:
- Use existing Firebase Auth (`firebase-auth` dependency already present)
- Trigger OAuth in Settings screen
- Store email in SharedPreferences

**Server Side**:
- Verify Google ID token
- Link device to email for settings recovery
- Allow multiple devices per email (different UUIDs)

---

## 8. Infrastructure & Deployment

### Technology Stack

**Server**:
- **Language**: Go 1.24+
- **HTTP Router**: Chi (lightweight, composable, stdlib-based)
- **WebSocket**: `gorilla/websocket` library
- **Logger**: `log/slog` (stdlib structured logging)
- **Database Driver**: `mongo-go-driver`
- **Authentication**: JWT for admin, bcrypt for license secrets

**Database**:
- **MongoDB**: 8.0+ (latest stable with improved change streams)
- **Replica Set**: Required for change streams in production

**Deployment**:
- **Reverse Proxy**: Nginx (handles TLS termination, static files, load balancing)
- **TLS**: Managed by Nginx with Let's Encrypt
- **Server**: Listens on HTTP internally (e.g., `localhost:8080`)

### Docker Compose

```yaml
version: '3.8'
services:
  relay-server:
    build: .
    ports:
      - "127.0.0.1:8080:8080"  # Internal only, proxied by Nginx
    environment:
      - MONGO_URI=mongodb://mongo:27017
      - JWT_SECRET=${JWT_SECRET}
      - SERVER_PORT=8080
    depends_on:
      - mongo
    restart: unless-stopped

  mongo:
    image: mongo:8.0
    ports:
      - "127.0.0.1:27017:27017"  # Internal only
    volumes:
      - mongo-data:/data/db
    command: --replSet rs0
    restart: unless-stopped

  mongo-init:
    image: mongo:8.0
    depends_on:
      - mongo
    entrypoint: ["sh", "-c", "sleep 5 && mongosh --host mongo --eval 'rs.initiate()'"]

  nginx:
    image: nginx:alpine
    ports:
      - "80:80"
      - "443:443"
    volumes:
      - ./nginx.conf:/etc/nginx/nginx.conf:ro
      - /etc/letsencrypt:/etc/letsencrypt:ro
      - /var/www/certbot:/var/www/certbot:ro
    depends_on:
      - relay-server
    restart: unless-stopped

  certbot:
    image: certbot/certbot
    volumes:
      - /etc/letsencrypt:/etc/letsencrypt
      - /var/www/certbot:/var/www/certbot
    entrypoint: "/bin/sh -c 'trap exit TERM; while :; do certbot renew; sleep 12h & wait $${!}; done;'"

volumes:
  mongo-data:
```

### Environment Variables

```bash
# Server
SERVER_HOST=0.0.0.0
SERVER_PORT=8080

# MongoDB
MONGO_URI=mongodb://mongo:27017/relay_hub
MONGO_DATABASE=relay_hub

# Security
JWT_SECRET=<random-secret>

# Queue Cleanup
QUEUE_TTL_HOURS=24
OUTGOING_QUEUE_TTL_HOURS=168  # 7 days
CLEANUP_JOB_INTERVAL_MINUTES=60

# WebSocket
WS_PING_INTERVAL_SECONDS=30
WS_MAX_MESSAGE_SIZE=10485760  # 10MB for images

# Logging
LOG_LEVEL=info  # debug, info, warn, error
LOG_FORMAT=json # json or text
```

### Nginx Configuration

**`nginx.conf`** (production example):
```nginx
events {
    worker_connections 1024;
}

http {
    upstream relay_server {
        server relay-server:8080;
    }

    # HTTP → HTTPS redirect
    server {
        listen 80;
        server_name relay.example.com;

        location /.well-known/acme-challenge/ {
            root /var/www/certbot;
        }

        location / {
            return 301 https://$host$request_uri;
        }
    }

    # HTTPS server
    server {
        listen 443 ssl http2;
        server_name relay.example.com;

        ssl_certificate /etc/letsencrypt/live/relay.example.com/fullchain.pem;
        ssl_certificate_key /etc/letsencrypt/live/relay.example.com/privkey.pem;
        ssl_protocols TLSv1.2 TLSv1.3;
        ssl_ciphers HIGH:!aNULL:!MD5;

        # WebSocket upgrade
        location /ws/ {
            proxy_pass http://relay_server;
            proxy_http_version 1.1;
            proxy_set_header Upgrade $http_upgrade;
            proxy_set_header Connection "upgrade";
            proxy_set_header Host $host;
            proxy_set_header X-Real-IP $remote_addr;
            proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
            proxy_set_header X-Forwarded-Proto $scheme;
            proxy_read_timeout 3600s;
            proxy_send_timeout 3600s;
        }

        # REST API
        location /api/ {
            proxy_pass http://relay_server;
            proxy_set_header Host $host;
            proxy_set_header X-Real-IP $remote_addr;
            proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
            proxy_set_header X-Forwarded-Proto $scheme;
        }

        # Admin endpoints
        location /admin/ {
            proxy_pass http://relay_server;
            proxy_set_header Host $host;
            proxy_set_header X-Real-IP $remote_addr;
        }

        # Metrics (restrict to internal network)
        location /metrics {
            allow 10.0.0.0/8;
            deny all;
            proxy_pass http://relay_server;
        }
    }
}
```

### TLS/HTTPS Setup

**Managed by Nginx + Let's Encrypt**:
1. Initial certificate generation:
   ```bash
   certbot certonly --webroot -w /var/www/certbot \
     -d relay.example.com --email admin@example.com --agree-tos
   ```
2. Auto-renewal via certbot container (runs every 12h)
3. Go server runs plain HTTP on internal port 8080
4. Android app connects to `wss://relay.example.com/ws/device`

**Development**:
- Use `ngrok` or self-signed certificates in Nginx
- Or run without TLS on local network (test only)

### Logging & Monitoring

**Logging**:
- **Library**: `log/slog` (Go stdlib, structured logging)
- **Format**: JSON for production, text for development
- **Levels**: DEBUG, INFO, WARN, ERROR
- **Output**: stdout (captured by Docker logging driver)
- **Example**:
  ```go
  slog.Info("websocket connected",
      "device_uuid", uuid,
      "license_key", license,
      "remote_addr", conn.RemoteAddr())
  ```

**Metrics**:
- Prometheus endpoint: `/metrics`
- Key metrics:
  - `websocket_connections_active`
  - `queue_messages_pending`
  - `queue_messages_delivered_total`
  - `http_requests_total`
  - `http_request_duration_seconds`

**Monitoring Stack** (optional):
- Prometheus for metrics collection
- Grafana for dashboards
- Alertmanager for notifications

### Background Workers

**Queue Cleanup Job**:
- Run every 1 hour
- Delete `queue` records where `ready=true` and `delivered_at < now() - 1h`
- Delete `outgoing_queue` records where `fetched=true` and `fetched_at < now() - 7d`

**Connection Monitor**:
- Run every 5 minutes
- Mark devices as `offline` if `last_seen < now() - 2m`

**License Expiration Check**:
- Run daily at midnight
- Update `licenses.status = "expired"` where `expiration_date < now()`
- Disconnect devices linked to expired licenses

---

## 9. Android Client Modifications

### New Constants

**Add to `Constants.java`**:
```java
public static final String SYNC_FORMAT_WEBSOCKET = "WebSocket_relay";
public static final int WEBSOCKET_RECONNECT_MAX_DELAY = 60000; // 60s
public static final int WEBSOCKET_PING_INTERVAL = 30000; // 30s
```

### Database Schema Changes

**Update `UserAccount` entity** (increment DB version to 21):
```kotlin
@Entity(tableName = "user_accounts", primaryKeys = ["guid"])
data class UserAccount(
    // ... existing fields ...
    @ColumnInfo(name = "relay_server") val relayServer: String = "",  // NEW
    @ColumnInfo(name = "device_uuid") val deviceUuid: String = ""     // NEW
)
```

**Migration**:
```kotlin
val MIGRATION_20_21 = object : Migration(20, 21) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE user_accounts ADD COLUMN relay_server TEXT NOT NULL DEFAULT ''")
        database.execSQL("ALTER TABLE user_accounts ADD COLUMN device_uuid TEXT NOT NULL DEFAULT ''")
    }
}
```

### New Dependency

**Add to `app/build.gradle`**:
```gradle
// WebSocket (OkHttp already included via Retrofit)
implementation "com.squareup.okhttp3:okhttp:5.3.2"  // Ensure latest version
```

### New Repository: WebSocketRepository

**Interface** (`/domain/repository/WebSocketRepository.kt`):
```kotlin
interface WebSocketRepository {
    fun connect(account: UserAccount): Flow<ConnectionState>
    fun disconnect()
    suspend fun sendMessage(type: String, payload: Any): Result<Unit>
    fun observeIncomingMessages(): Flow<WebSocketMessage>
    fun getConnectionState(): Flow<ConnectionState>
}

sealed class ConnectionState {
    object Disconnected : ConnectionState()
    object Connecting : ConnectionState()
    object Connected : ConnectionState()
    data class Error(val message: String) : ConnectionState()
}

data class WebSocketMessage(
    val type: String,
    val messageId: String,
    val timestamp: String,
    val payload: JsonObject
)
```

**Implementation** (`/data/repository/WebSocketRepositoryImpl.kt`):
- Use OkHttp `WebSocket` client
- Implement reconnection logic with exponential backoff
- Parse incoming messages via Gson
- Send ACKs for received messages
- Emit connection state via `StateFlow`
- Store connection in ViewModel scope (not Singleton)

### Modified NetworkModule

**Add WebSocket provider**:
```kotlin
@Provides
@ViewModelScoped  // NEW: Scoped to ViewModel lifecycle
fun provideWebSocketRepository(
    okHttpClient: OkHttpClient,
    gson: Gson,
    logger: Logger
): WebSocketRepository {
    return WebSocketRepositoryImpl(okHttpClient, gson, logger)
}
```

### Updated NetworkRepositoryImpl

**Modify sync methods** to check `dataFormat`:
```kotlin
override suspend fun updateDifferential(): Flow<Result> = flow {
    val account = currentSystemAccount ?: return@flow

    when (account.dataFormat) {
        Constants.SYNC_FORMAT_HTTP -> {
            // Existing HTTP sync logic
        }
        Constants.SYNC_FORMAT_WEBSOCKET -> {
            // NEW: WebSocket sync via WebSocketRepository
            sendDocumentsViaWebSocket(account)
        }
    }
}
```

### New Extension Functions

**`UserAccount.kt`**:
```kotlin
fun UserAccount.isValidForWebSocketConnection(): Boolean {
    return dataFormat == Constants.SYNC_FORMAT_WEBSOCKET &&
            relayServer.isNotEmpty() &&
            license.isNotEmpty() &&
            deviceUuid.isNotEmpty()
}

fun UserAccount.getWebSocketUrl(): String {
    val host = if (relayServer.startsWith("ws")) relayServer else "wss://$relayServer"
    return "$host/ws/device?uuid=$deviceUuid&license=$license"
}
```

### UI Changes

**UserAccountFragment**:
1. Add `relayServer` EditText field (visible when `dataFormat = "WebSocket_relay"`)
2. Add "Generate Device UUID" button
3. Display connection status indicator (online/offline)
4. Test connection button

**SyncFragment**:
- Show real-time sync status for WebSocket mode
- Display pending queue count
- Connection health indicator

**MainActivity** (optional):
- Persistent connection notification for background sync
- Foreground service for WebSocket (similar to LocationUpdatesService)

---

## 10. Testing & QA

### Unit Tests

**Server (Go)**:
- License validation logic
- Queue CRUD operations
- Message routing logic
- JWT authentication

**Android**:
- `WebSocketRepositoryImpl` with mock WebSocket
- Connection state transitions
- Reconnection backoff logic
- Message serialization/deserialization

### Integration Tests

**End-to-End Flow**:
1. Device connects via WebSocket
2. Accounting system pushes order via REST
3. Device receives and ACKs order
4. Device sends cash document via WebSocket
5. Accounting system retrieves via REST API

**Load Testing**:
- 100 concurrent WebSocket connections
- 1000 messages/second throughput
- Queue latency under load

**Connection Stability**:
- Disconnect and reconnect scenarios
- Network loss simulation (airplane mode)
- Server restart with pending messages
- Message ordering verification

### Test Tools

- **Go**: `testing` package, `testify` for assertions
- **Android**: JUnit, Mockito, Turbine for Flow testing
- **Load Testing**: `k6` or `Artillery`
- **WebSocket Client**: `wscat` for manual testing

---

## 11. Scalability & Future Extensions

### Horizontal Scaling

**Multiple Server Instances**:
- Shared MongoDB replica set
- Redis Pub/Sub for cross-instance message routing
- Sticky sessions via UUID hashing (load balancer)
- WebSocket sharding: Device UUID → Server instance mapping

**Architecture**:
```
[Load Balancer]
    ├─> [Relay Server 1] ──┐
    ├─> [Relay Server 2] ──┼─> [MongoDB Replica Set]
    └─> [Relay Server 3] ──┘
            │
         [Redis]
    (pub/sub for routing)
```

### Push Notifications

**FCM Integration** (when device offline):
1. Message arrives in queue for offline device
2. Server sends FCM notification via Firebase Admin SDK
3. Device wakes up, connects via WebSocket
4. Retrieves pending messages

**Android**:
- Use existing Firebase Messaging dependency
- Handle notification in `FirebaseMessagingService`
- Trigger WebSocket sync on notification received

### Advanced Features

- **Message Priorities**: High-priority queue for urgent messages
- **Partial Sync**: Send only changed fields (delta sync)
- **Compression**: gzip for large payloads (images)
- **End-to-End Encryption**: Optional E2E encryption for sensitive data
- **Webhooks**: Notify accounting system via webhook instead of polling

---

## 12. Android Implementation Roadmap

### Phase 1: Foundation (Week 1-2)
1. Add `SYNC_FORMAT_WEBSOCKET` constant
2. Update `UserAccount` entity with `relayServer` and `deviceUuid` fields
3. Create database migration 20→21
4. Implement UUID generation in SharedPreferences
5. Update `UserAccountFragment` UI with relay server field

### Phase 2: WebSocket Core (Week 3-4)
6. Create `WebSocketRepository` interface
7. Implement `WebSocketRepositoryImpl` with OkHttp
8. Add connection state management (StateFlow)
9. Implement message parsing (Gson)
10. Add reconnection logic with exponential backoff

### Phase 3: Integration (Week 5-6)
11. Modify `NetworkRepositoryImpl` to support WebSocket mode
12. Implement `sendDocumentsViaWebSocket()` method
13. Add incoming message handler in ViewModel
14. Create `WebSocketService` (foreground service for persistent connection)
15. Add connection status indicator to UI

### Phase 4: Testing & Polish (Week 7-8)
16. Write unit tests for `WebSocketRepositoryImpl`
17. Integration testing with server
18. Handle edge cases (network loss, server restart)
19. Add diagnostics screen for WebSocket messages
20. Documentation and demo setup

### Testing Checklist
- [ ] Device UUID persists across app restarts
- [ ] WebSocket connects successfully with valid license
- [ ] Connection state updates in UI (online/offline)
- [ ] Reconnection works after network loss
- [ ] Orders sent via WebSocket appear in queue
- [ ] Incoming messages received and ACKed
- [ ] HTTP mode still works (backward compatibility)
- [ ] Multi-account support (different connections per account)
- [ ] Background sync via foreground service
- [ ] Settings sync with Google OAuth

---

## 13. Server Implementation Roadmap

### Phase 1: Setup (Week 1)
1. Initialize Go project with Chi router
2. Set up MongoDB connection and models
3. Create Docker Compose configuration (with Nginx)
4. Implement basic REST endpoints (POST /api/v1/push, GET /api/v1/pull)
5. License validation middleware
6. Configure slog structured logging

### Phase 2: WebSocket Core (Week 2-3)
6. Implement WebSocket handler (`/ws/device`)
7. Device registration and authentication
8. Active connections management (in-memory map)
9. Ping/pong keep-alive mechanism
10. Connection state tracking in MongoDB

### Phase 3: Message Routing (Week 4-5)
11. Queue monitoring (change stream or polling)
12. Message delivery to connected devices
13. ACK handling and queue status updates
14. Outgoing message storage (device → accounting)
15. FIFO message ordering per device

### Phase 4: Admin & Monitoring (Week 6)
16. Admin API endpoints (license/device management)
17. Prometheus metrics integration
18. Structured logging (JSON format)
19. Background cleanup jobs (queue TTL)
20. Connection health monitoring

### Phase 5: Deployment (Week 7-8)
21. Let's Encrypt TLS setup
22. Production Docker Compose configuration
23. MongoDB replica set initialization
24. Load testing and optimization
25. Documentation and deployment guide

---

## 14. Message Format Specification

### Incoming (Accounting → Device)

**Catalog Update**:
```json
{
  "type": "data",
  "message_id": "msg-12345",
  "timestamp": "2025-01-15T10:30:00Z",
  "payload": {
    "data_type": "clients",
    "action": "update",
    "items": [
      { "guid": "client-123", "name": "Store A", ... }
    ]
  }
}
```

**Order Assignment**:
```json
{
  "type": "data",
  "message_id": "msg-67890",
  "timestamp": "2025-01-15T11:00:00Z",
  "payload": {
    "data_type": "order",
    "action": "assign",
    "order": {
      "guid": "order-456",
      "client_guid": "client-123",
      "content": [...]
    }
  }
}
```

### Outgoing (Device → Accounting)

**Order Submission**:
```json
{
  "type": "data",
  "message_id": "msg-abc123",
  "timestamp": "2025-01-15T12:00:00Z",
  "payload": {
    "data_type": "order",
    "db_guid": "account-guid-789",
    "document": {
      "guid": "order-new-123",
      "client_guid": "client-123",
      "total": 15000.50,
      "content": [...]
    }
  }
}
```

### Acknowledgment

```json
{
  "type": "ack",
  "message_id": "msg-12345",
  "timestamp": "2025-01-15T10:30:05Z",
  "payload": {
    "status": "received"
  }
}
```

---

## 15. Security Considerations

### Authentication
- **License Secrets**: Hashed with bcrypt (cost 12)
- **WebSocket**: Validate license on connect, reject expired licenses
- **Admin API**: JWT tokens with 1-hour expiration
- **Rate Limiting**: 100 requests/minute per license

### Data Protection
- **TLS 1.3**: Required for all connections (HTTPS + WSS)
- **Input Validation**: Sanitize all inputs (license keys, UUIDs, JSON payloads)
- **SQL Injection**: Not applicable (MongoDB), but validate query parameters
- **XSS**: Not applicable (no web UI), but sanitize log outputs

### Network
- **Firewall**: Allow only ports 80, 443, 27017 (MongoDB internal)
- **CORS**: Not needed (no browser clients)
- **DDoS Protection**: Use Cloudflare or AWS Shield

### Android
- **Credentials Storage**: Room database encrypted via SQLCipher (optional)
- **License Key**: Store in `UserAccount`, never in logs
- **Device UUID**: Non-sensitive, OK to log

---

## 16. Deployment Models

### Option 1: Single Cloud Instance (Recommended for MVP)
- One relay server on DigitalOcean/AWS/GCP
- MongoDB replica set (3 nodes for HA)
- All clients connect to same server
- Pros: Simple setup, low cost
- Cons: Single point of failure, limited scalability

### Option 2: Per-Client Instances
- Separate relay server + MongoDB per accounting system
- Full isolation between clients
- Pros: Data privacy, custom configurations
- Cons: Higher cost, more maintenance

### Option 3: Multi-Tenant SaaS
- Single infrastructure, logical separation via license keys
- Shared MongoDB cluster
- Horizontal scaling with multiple server instances
- Pros: Cost-efficient, scalable
- Cons: Complex routing, shared resource contention

**Recommendation**: Start with Option 1, migrate to Option 3 if >1000 devices.

---

## 17. Cost Estimation (Option 1: Single Cloud Instance)

### Infrastructure
- **Compute**: 2 vCPU, 4GB RAM ($20/month on DigitalOcean)
- **MongoDB Atlas**: M10 tier, 10GB storage ($57/month)
- **Bandwidth**: 1TB/month (~500 devices) (included in compute)
- **Domain + SSL**: $15/year (Let's Encrypt free)

**Total**: ~$80/month for 500 devices

### Scaling
- 1000 devices: $120/month (upgrade to 4 vCPU, M20 MongoDB)
- 5000 devices: $400/month (horizontal scaling + load balancer)

---

## 18. Support & Maintenance

### Monitoring Alerts
- MongoDB disk usage >80%
- WebSocket connections dropped >10%
- Queue size >10,000 messages
- Server CPU >90% for 5 minutes

### Backup Strategy
- MongoDB automated daily backups (7-day retention)
- Configuration files in Git repository
- Secrets in environment variables (never in code)

### Update Strategy
- Rolling updates with zero downtime
- Blue-green deployment for major versions
- Database migrations with backward compatibility

---

## 19. Next Steps & Action Items

### Immediate (This Week)
1. ✅ **Finalize plan document** with technical details
2. ⬜ **Set up Go project** structure and dependencies
3. ⬜ **Create Android feature branch** (`feature/websocket-relay`)
4. ⬜ **Define JSON message schemas** (create `/schemas` directory)
5. ⬜ **Set up local MongoDB** via Docker

### Week 1-2
6. ⬜ **Implement basic REST API** (push/pull/license endpoints)
7. ⬜ **Implement WebSocket handler** (connection + ping/pong)
8. ⬜ **Android: Add device UUID generation** and storage
9. ⬜ **Android: Update UserAccount schema** and migration
10. ⬜ **Test end-to-end connection** (Android ↔ Server)

### Week 3-4
11. ⬜ **Implement message routing** (queue → WebSocket delivery)
12. ⬜ **Android: Implement WebSocketRepository**
13. ⬜ **Integration testing** with Postman + Android emulator
14. ⬜ **Add reconnection logic** (both server and Android)
15. ⬜ **Implement ACK flow** (delivery confirmation)

### Week 5-6
16. ⬜ **Integrate with NetworkRepositoryImpl** (dual mode support)
17. ⬜ **Add UI indicators** (connection status, sync state)
18. ⬜ **Implement background sync service** (Android foreground service)
19. ⬜ **Add admin endpoints** (license/device management)
20. ⬜ **Deploy to staging server** (DigitalOcean droplet)

### Week 7-8
21. ⬜ **Load testing** (100 concurrent connections)
22. ⬜ **Security audit** (TLS, authentication, input validation)
23. ⬜ **Documentation** (API specs, deployment guide)
24. ⬜ **Beta testing** with 5-10 devices
25. ⬜ **Production deployment** with monitoring

---

## 20. Open Questions

1. **License Distribution**: How will accounting systems obtain license keys? (Admin panel? Manual generation?)
2. **Message Persistence**: Should server persist all messages long-term for audit? (Currently TTL cleanup)
3. **Backward Compatibility**: Will old Android versions without WebSocket still be supported? (Yes, HTTP mode remains)
4. **FCM Push**: Required for MVP or Phase 2? (Recommend Phase 2)
5. **Multi-Account WebSocket**: Should one device maintain multiple WebSocket connections for different accounts? (Recommend single connection per active account)
6. **1C Integration**: Does 1C accounting system need client library for REST API? (Yes, recommend Go SDK)

---

## 21. Success Metrics

### Technical KPIs
- WebSocket uptime: >99.5%
- Message delivery latency: <500ms (p95)
- Reconnection success rate: >95%
- Queue throughput: 1000 messages/second

### Business KPIs
- Device adoption rate: >20% switch from HTTP to WebSocket in first 3 months
- Support tickets related to sync: -50% reduction
- User-reported sync issues: <1% of active devices

---

## Appendix A: Technology Alternatives

### Server Framework
- **Chi** (chosen): Lightweight, composable, stdlib-compatible, excellent middleware
- Gin: Feature-rich but heavier, more opinionated
- Fiber: Very fast but uses fasthttp (not stdlib net/http)
- Echo: Similar to Gin, smaller ecosystem

### Database
- **MongoDB** (chosen): Flexible schema, change streams, TTL indexes
- PostgreSQL + TimescaleDB: Relational, better for analytics
- Redis: Fast but limited persistence guarantees

### WebSocket Library (Go)
- **gorilla/websocket** (chosen): Battle-tested, widely used
- nhooyr.io/websocket: Modern, better API
- gobwas/ws: Lowest overhead, more complex

### WebSocket Library (Android)
- **OkHttp** (chosen): Already in project, stable
- Scarlet: Reactive WebSocket library
- Ktor Client: Kotlin-first, requires new dependency

---

## Appendix B: Go Server Code Structure

### Project Layout

```
relay-hub/
├── cmd/
│   └── server/
│       └── main.go              # Entry point
├── internal/
│   ├── api/
│   │   ├── router.go            # Chi router setup
│   │   ├── middleware.go        # Auth, logging, rate limiting
│   │   ├── handlers/
│   │   │   ├── api.go           # REST API handlers
│   │   │   ├── websocket.go     # WebSocket handler
│   │   │   └── admin.go         # Admin endpoints
│   │   └── response.go          # Response helpers
│   ├── models/
│   │   ├── license.go
│   │   ├── device.go
│   │   ├── queue.go
│   │   └── message.go
│   ├── database/
│   │   ├── mongo.go             # MongoDB client
│   │   ├── license_repo.go
│   │   ├── device_repo.go
│   │   └── queue_repo.go
│   ├── websocket/
│   │   ├── manager.go           # Connection manager
│   │   ├── client.go            # Per-client handler
│   │   └── message.go           # Message routing
│   ├── auth/
│   │   ├── jwt.go
│   │   └── bcrypt.go
│   └── worker/
│       ├── cleanup.go           # Queue cleanup job
│       └── monitor.go           # Connection monitor
├── pkg/
│   └── config/
│       └── config.go            # Environment config
├── go.mod
├── go.sum
├── Dockerfile
└── docker-compose.yml
```

### Example: Main Entry Point

```go
// cmd/server/main.go
package main

import (
    "context"
    "log/slog"
    "net/http"
    "os"
    "os/signal"
    "syscall"
    "time"

    "relay-hub/internal/api"
    "relay-hub/internal/database"
    "relay-hub/pkg/config"
)

func main() {
    // Configure slog
    logLevel := slog.LevelInfo
    if os.Getenv("LOG_LEVEL") == "debug" {
        logLevel = slog.LevelDebug
    }

    var handler slog.Handler
    if os.Getenv("LOG_FORMAT") == "json" {
        handler = slog.NewJSONHandler(os.Stdout, &slog.HandlerOptions{Level: logLevel})
    } else {
        handler = slog.NewTextHandler(os.Stdout, &slog.HandlerOptions{Level: logLevel})
    }

    logger := slog.New(handler)
    slog.SetDefault(logger)

    // Load config
    cfg := config.Load()

    // Connect to MongoDB
    ctx := context.Background()
    db, err := database.Connect(ctx, cfg.MongoURI)
    if err != nil {
        slog.Error("failed to connect to database", "error", err)
        os.Exit(1)
    }
    defer db.Disconnect(ctx)

    slog.Info("connected to MongoDB", "uri", cfg.MongoURI)

    // Initialize router
    router := api.NewRouter(db, cfg)

    // Start HTTP server
    server := &http.Server{
        Addr:         cfg.ServerAddr,
        Handler:      router,
        ReadTimeout:  15 * time.Second,
        WriteTimeout: 15 * time.Second,
        IdleTimeout:  60 * time.Second,
    }

    // Graceful shutdown
    go func() {
        slog.Info("starting server", "addr", cfg.ServerAddr)
        if err := server.ListenAndServe(); err != nil && err != http.ErrServerClosed {
            slog.Error("server error", "error", err)
            os.Exit(1)
        }
    }()

    // Wait for interrupt signal
    quit := make(chan os.Signal, 1)
    signal.Notify(quit, syscall.SIGINT, syscall.SIGTERM)
    <-quit

    slog.Info("shutting down server...")
    ctx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
    defer cancel()

    if err := server.Shutdown(ctx); err != nil {
        slog.Error("server shutdown error", "error", err)
    }
    slog.Info("server stopped")
}
```

### Example: Chi Router Setup

```go
// internal/api/router.go
package api

import (
    "net/http"
    "time"

    "github.com/go-chi/chi/v5"
    "github.com/go-chi/chi/v5/middleware"
    "github.com/go-chi/cors"

    "relay-hub/internal/api/handlers"
    "relay-hub/internal/database"
    "relay-hub/pkg/config"
)

func NewRouter(db *database.DB, cfg *config.Config) *chi.Mux {
    r := chi.NewRouter()

    // Middleware
    r.Use(middleware.RequestID)
    r.Use(middleware.RealIP)
    r.Use(middleware.Logger)  // Chi's default logger
    r.Use(middleware.Recoverer)
    r.Use(middleware.Timeout(60 * time.Second))

    // CORS (if needed)
    r.Use(cors.Handler(cors.Options{
        AllowedOrigins:   []string{"*"},
        AllowedMethods:   []string{"GET", "POST", "PUT", "DELETE", "OPTIONS"},
        AllowedHeaders:   []string{"Accept", "Authorization", "Content-Type"},
        AllowCredentials: false,
        MaxAge:           300,
    }))

    // Initialize handlers
    apiHandler := handlers.NewAPIHandler(db)
    wsHandler := handlers.NewWebSocketHandler(db)
    adminHandler := handlers.NewAdminHandler(db, cfg)

    // Health check
    r.Get("/health", func(w http.ResponseWriter, r *http.Request) {
        w.WriteHeader(http.StatusOK)
        w.Write([]byte("OK"))
    })

    // API routes (with license auth middleware)
    r.Route("/api/v1", func(r chi.Router) {
        r.Use(LicenseAuthMiddleware(db))
        r.Post("/push", apiHandler.Push)
        r.Get("/pull", apiHandler.Pull)
        r.Post("/license", apiHandler.CreateLicense)
        r.Get("/devices", apiHandler.ListDevices)
        r.Delete("/devices/{uuid}", apiHandler.DeleteDevice)
    })

    // WebSocket route
    r.Get("/ws/device", wsHandler.HandleConnection)

    // Admin routes (with JWT auth)
    r.Route("/admin", func(r chi.Router) {
        r.Use(JWTAuthMiddleware(cfg.JWTSecret))
        r.Get("/licenses", adminHandler.ListLicenses)
        r.Post("/licenses", adminHandler.CreateLicense)
        r.Put("/licenses/{id}", adminHandler.UpdateLicense)
        r.Get("/devices", adminHandler.ListDevices)
        r.Delete("/devices/{uuid}", adminHandler.DeleteDevice)
    })

    // Metrics
    r.Get("/metrics", handlers.MetricsHandler)

    return r
}
```

### Example: slog Usage in Handler

```go
// internal/api/handlers/websocket.go
package handlers

import (
    "log/slog"
    "net/http"

    "github.com/gorilla/websocket"
    "relay-hub/internal/database"
)

type WebSocketHandler struct {
    db       *database.DB
    upgrader websocket.Upgrader
}

func NewWebSocketHandler(db *database.DB) *WebSocketHandler {
    return &WebSocketHandler{
        db: db,
        upgrader: websocket.Upgrader{
            ReadBufferSize:  1024,
            WriteBufferSize: 1024,
            CheckOrigin: func(r *http.Request) bool {
                return true // Configure properly in production
            },
        },
    }
}

func (h *WebSocketHandler) HandleConnection(w http.ResponseWriter, r *http.Request) {
    // Extract parameters
    uuid := r.URL.Query().Get("uuid")
    license := r.URL.Query().Get("license")

    // Structured logging with slog
    logger := slog.With(
        "device_uuid", uuid,
        "license_key", license,
        "remote_addr", r.RemoteAddr,
    )

    logger.Info("websocket connection attempt")

    // Validate license
    valid, err := h.db.ValidateLicense(r.Context(), license)
    if err != nil {
        logger.Error("license validation failed", "error", err)
        http.Error(w, "internal error", http.StatusInternalServerError)
        return
    }
    if !valid {
        logger.Warn("invalid license")
        http.Error(w, "unauthorized", http.StatusUnauthorized)
        return
    }

    // Upgrade connection
    conn, err := h.upgrader.Upgrade(w, r, nil)
    if err != nil {
        logger.Error("websocket upgrade failed", "error", err)
        return
    }

    logger.Info("websocket connected")

    // Handle connection (pass logger to client handler)
    // client := NewClient(conn, uuid, license, h.db, logger)
    // client.Handle()
}
```

### Example: Middleware with slog

```go
// internal/api/middleware.go
package api

import (
    "context"
    "log/slog"
    "net/http"
    "strings"
    "time"

    "github.com/go-chi/chi/v5"
    "relay-hub/internal/database"
)

// Structured logging middleware
func LoggingMiddleware(next http.Handler) http.Handler {
    return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
        start := time.Now()

        // Create logger with request context
        logger := slog.With(
            "method", r.Method,
            "path", r.URL.Path,
            "remote_addr", r.RemoteAddr,
            "request_id", chi.RouteContext(r.Context()).RoutePattern(),
        )

        logger.Info("request started")

        // Wrap response writer to capture status
        ww := &responseWriter{ResponseWriter: w, statusCode: http.StatusOK}
        next.ServeHTTP(ww, r)

        logger.Info("request completed",
            "status", ww.statusCode,
            "duration_ms", time.Since(start).Milliseconds(),
        )
    })
}

type responseWriter struct {
    http.ResponseWriter
    statusCode int
}

func (w *responseWriter) WriteHeader(code int) {
    w.statusCode = code
    w.ResponseWriter.WriteHeader(code)
}

// License authentication middleware
func LicenseAuthMiddleware(db *database.DB) func(http.Handler) http.Handler {
    return func(next http.Handler) http.Handler {
        return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
            // Extract license from Authorization header
            auth := r.Header.Get("Authorization")
            if !strings.HasPrefix(auth, "Bearer ") {
                slog.Warn("missing authorization header", "path", r.URL.Path)
                http.Error(w, "unauthorized", http.StatusUnauthorized)
                return
            }

            token := strings.TrimPrefix(auth, "Bearer ")
            // Parse license_key:secret
            parts := strings.SplitN(token, ":", 2)
            if len(parts) != 2 {
                slog.Warn("invalid authorization format")
                http.Error(w, "unauthorized", http.StatusUnauthorized)
                return
            }

            licenseKey := parts[0]
            secret := parts[1]

            // Validate
            valid, err := db.ValidateLicenseWithSecret(r.Context(), licenseKey, secret)
            if err != nil {
                slog.Error("license validation error", "error", err)
                http.Error(w, "internal error", http.StatusInternalServerError)
                return
            }
            if !valid {
                slog.Warn("invalid license credentials", "license_key", licenseKey)
                http.Error(w, "unauthorized", http.StatusUnauthorized)
                return
            }

            // Add license to context
            ctx := context.WithValue(r.Context(), "license_key", licenseKey)
            next.ServeHTTP(w, r.WithContext(ctx))
        })
    }
}
```

### Dependencies (go.mod)

```go
module relay-hub

go 1.24

require (
    github.com/go-chi/chi/v5 v5.0.11
    github.com/go-chi/cors v1.2.1
    github.com/gorilla/websocket v1.5.1
    go.mongodb.org/mongo-driver v1.13.1
    golang.org/x/crypto v0.18.0  // for bcrypt
    github.com/golang-jwt/jwt/v5 v5.2.0
    github.com/prometheus/client_golang v1.18.0  // for /metrics
)
```

---

## Appendix C: Glossary

- **UUID**: Universally Unique Identifier (device identifier)
- **TTL**: Time-To-Live (auto-expiration)
- **ACK**: Acknowledgment (confirmation of message receipt)
- **FIFO**: First-In-First-Out (message ordering)
- **WSS**: WebSocket Secure (TLS-encrypted WebSocket)
- **FCM**: Firebase Cloud Messaging (push notifications)
- **1C**: Russian accounting/ERP system (primary integration target)
- **db_guid**: Database GUID (multi-account identifier in Android app)
- **Chi**: Lightweight Go HTTP router built on stdlib net/http
- **slog**: Structured logging library in Go standard library (log/slog)