# WebSocket API Key Authentication Implementation Plan

## Overview

Implement API key authentication for WebSocket connections using a shared API key stored in `local.properties` file. Devices authenticate using format: `<api-key>:<device-uuid>`.

---

## 🎯 Proposed Architecture

### Authentication Flow
```
Android Device                    Backend Server
     |                                  |
     |  WebSocket Connection Request    |
     |  ws://server/ws/device           |
     |  Header: Authorization:          |
     |    Bearer <API_KEY>:<DEVICE_ID>  |
     |--------------------------------->|
     |                                  |
     |                         Validate API Key
     |                         Extract Device UUID
     |                         Lookup Device in DB
     |                         Check License Status
     |                                  |
     |  Connection Accepted/Rejected    |
     |<---------------------------------|
```

### Key Points
- ✅ **Shared API Key**: One API key for all Android devices (stored in `local.properties`)
- ✅ **Device Identification**: Device UUID uniquely identifies each device
- ✅ **Backend Logic**: API key validates request is legitimate, then device UUID determines routing
- ✅ **No License in URL**: License number is NOT sent by device (backend lookup only)

---

## 📱 Android App Changes

### 1. Add API Key to `local.properties`

**File:** `/local.properties`

Add new property:
```properties
# WebSocket Relay API Key
WEBSOCKET_API_KEY=your_api_key_here_abc123xyz789
```

**Important:** This file is already in `.gitignore`, so the key won't be committed to version control.

---

### 2. Update `app/build.gradle` to Read API Key

**File:** `/app/build.gradle`

**Current State:** Already uses `secrets-gradle-plugin` (line 4)

**Add to `defaultConfig` block:**
```gradle
android {
    // ... existing config ...

    defaultConfig {
        // ... existing config ...

        // Read from local.properties via secrets plugin
        // The secrets plugin automatically reads local.properties
        // and makes values available as BuildConfig fields
    }

    buildTypes {
        release {
            // ... existing config ...
            buildConfigField "String", "WEBSOCKET_API_KEY", "\"${project.findProperty('WEBSOCKET_API_KEY') ?: ''}\""
        }
        debug {
            // ... existing config ...
            buildConfigField "String", "WEBSOCKET_API_KEY", "\"${project.findProperty('WEBSOCKET_API_KEY') ?: ''}\""
        }
    }
}
```

**Note:** The `secrets-gradle-plugin` (already in use) can automatically expose `local.properties` values to BuildConfig. We just need to ensure the plugin is configured correctly.

---

### 3. Update WebSocket Repository Implementation

**File:** `/app/src/main/java/ua/com/programmer/agentventa/data/repository/WebSocketRepositoryImpl.kt`

**Current Code (lines 160-175):**
```kotlin
private fun connectInternal(account: UserAccount) {
    val url = account.getWebSocketUrl()

    val request = Request.Builder()
        .url(url)
        .build()

    webSocket = okHttpClient.newWebSocket(request, createWebSocketListener())
}
```

**Updated Code:**
```kotlin
private fun connectInternal(account: UserAccount) {
    val url = account.getWebSocketUrl()

    // Construct authentication token: <API_KEY>:<DEVICE_UUID>
    val apiKey = BuildConfig.WEBSOCKET_API_KEY
    val deviceUuid = account.guid
    val authToken = "$apiKey:$deviceUuid"

    logger.d(logTag, "Connecting with auth token format: <api_key>:<device_uuid>")

    val request = Request.Builder()
        .url(url)
        .addHeader("Authorization", "Bearer $authToken")
        .build()

    webSocket = okHttpClient.newWebSocket(request, createWebSocketListener())
}
```

**Key Changes:**
- ✅ Read API key from `BuildConfig.WEBSOCKET_API_KEY`
- ✅ Get device UUID from `account.guid`
- ✅ Construct token as `<api_key>:<device_uuid>`
- ✅ Send in `Authorization` header as `Bearer <token>`

---

### 4. Add Validation for Missing API Key

**File:** `/app/src/main/java/ua/com/programmer/agentventa/data/repository/WebSocketRepositoryImpl.kt`

**Update `connect()` method:**
```kotlin
override fun connect(account: UserAccount) {
    if (!account.isValidForWebSocketConnection()) {
        logger.w(logTag, "Invalid WebSocket connection settings")
        _connectionState.value = ConnectionState.Error("Invalid connection settings")
        return
    }

    // Validate API key is configured
    val apiKey = BuildConfig.WEBSOCKET_API_KEY
    if (apiKey.isNullOrBlank()) {
        logger.e(logTag, "WEBSOCKET_API_KEY not configured in local.properties")
        _connectionState.value = ConnectionState.Error("API key not configured")
        return
    }

    disconnect() // Close existing connection if any

    currentAccount = account
    reconnectAttempts = 0

    logger.d(logTag, "Connecting to WebSocket: ${account.relayServer}")
    _connectionState.value = ConnectionState.Connecting

    connectInternal(account)
}
```

---

### 5. Update ProGuard Rules (for Release Build)

**File:** `/app/proguard-rules.pro`

**Add:**
```proguard
# Keep BuildConfig fields
-keepclassmembers class ua.com.programmer.agentventa.BuildConfig {
    public static final java.lang.String WEBSOCKET_API_KEY;
}
```

This ensures the API key field isn't removed during code minification in release builds.

---

### 6. Update Test Configuration

**File:** `/app/src/test/java/ua/com/programmer/agentventa/data/repository/WebSocketRepositoryImplTest.kt` (if exists)

**Mock BuildConfig:**
```kotlin
// For unit tests, mock the API key
every { BuildConfig.WEBSOCKET_API_KEY } returns "test_api_key_123"
```

Alternatively, use dependency injection to provide the API key, making it easier to test.

---

### 7. Optional: Inject API Key via Dependency Injection (Better Design)

**Create ApiKeyProvider:**

**File:** `/app/src/main/java/ua/com/programmer/agentventa/infrastructure/config/ApiKeyProvider.kt`
```kotlin
package ua.com.programmer.agentventa.infrastructure.config

import ua.com.programmer.agentventa.BuildConfig
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ApiKeyProvider @Inject constructor() {

    val webSocketApiKey: String
        get() = BuildConfig.WEBSOCKET_API_KEY ?: ""

    fun hasWebSocketApiKey(): Boolean {
        return webSocketApiKey.isNotBlank()
    }
}
```

**Update Hilt Module:**

**File:** `/app/src/main/java/ua/com/programmer/agentventa/di/NetworkModule.kt`
```kotlin
@Provides
@Singleton
fun provideApiKeyProvider(): ApiKeyProvider {
    return ApiKeyProvider()
}
```

**Update WebSocketRepositoryImpl:**
```kotlin
class WebSocketRepositoryImpl @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val gson: Gson,
    private val logger: Logger,
    private val apiKeyProvider: ApiKeyProvider  // NEW
) : WebSocketRepository {

    override fun connect(account: UserAccount) {
        // Validate API key
        if (!apiKeyProvider.hasWebSocketApiKey()) {
            logger.e(logTag, "WebSocket API key not configured")
            _connectionState.value = ConnectionState.Error("API key not configured")
            return
        }
        // ... rest of connect logic
    }

    private fun connectInternal(account: UserAccount) {
        val authToken = "${apiKeyProvider.webSocketApiKey}:${account.guid}"
        // ... rest of connection logic
    }
}
```

**Benefits:**
- ✅ Easier to test (can inject mock ApiKeyProvider)
- ✅ Cleaner separation of concerns
- ✅ Can add API key validation logic in one place

---

### 8. Update Documentation Comments

**File:** `/app/src/main/java/ua/com/programmer/agentventa/data/local/entity/UserAccount.kt`

**Update comment on `getWebSocketUrl()`:**
```kotlin
// Constructs WebSocket URL for connection
// Uses UserAccount.guid as device UUID for server identification
// NOTE: License number is NOT sent in the URL - it's not used for authorization.
// NOTE: Authentication is done via Authorization header with format: Bearer <API_KEY>:<DEVICE_UUID>
// The API key is shared across all app instances, device UUID identifies individual accounts.
// The backend links device UUIDs to license numbers (and therefore to 1C bases) server-side.
// License is only received from backend and stored locally for display/reference purposes.
fun UserAccount.getWebSocketUrl(): String {
    // ... existing implementation
}
```

---

## 🖥️ Backend Changes

### 1. Database Schema - No Changes Needed

The existing schema already supports this:
- `devices` collection has `uuid` field (device identifier)
- `devices` collection has `license_number` field (links to 1C base)
- No changes needed

---

### 2. Add API Key Configuration

**Environment Variable:**
```bash
# .env file or environment
WEBSOCKET_API_KEY=your_secure_api_key_here_abc123xyz789
```

**Or store in database (more flexible):**
```javascript
// api_keys collection
{
  _id: ObjectId,
  key_hash: String,        // bcrypt hash of API key
  name: String,            // "Android App Master Key"
  created_at: ISODate,
  last_used: ISODate,
  status: String           // "active" | "revoked"
}
```

**Recommendation:** Use environment variable for simplicity, database for production flexibility.

---

### 3. Update WebSocket Connection Handler

**File:** `internal/api/handlers/websocket.go`

**BEFORE:**
```go
func (h *WebSocketHandler) HandleConnection(w http.ResponseWriter, r *http.Request) {
    uuid := r.URL.Query().Get("uuid")

    // Validate device...
}
```

**AFTER:**
```go
func (h *WebSocketHandler) HandleConnection(w http.ResponseWriter, r *http.Request) {
    // 1. Extract Authorization header
    authHeader := r.Header.Get("Authorization")
    if !strings.HasPrefix(authHeader, "Bearer ") {
        logger.Warn("Missing Authorization header")
        http.Error(w, "Unauthorized", http.StatusUnauthorized)
        return
    }

    token := strings.TrimPrefix(authHeader, "Bearer ")

    // 2. Parse token: <API_KEY>:<DEVICE_UUID>
    parts := strings.SplitN(token, ":", 2)
    if len(parts) != 2 {
        logger.Warn("Invalid token format", slog.String("token_format", "expected <api_key>:<device_uuid>"))
        http.Error(w, "Invalid token format", http.StatusUnauthorized)
        return
    }

    apiKey := parts[0]
    deviceUuid := parts[1]

    logger := logger.With(
        slog.String("device_uuid", deviceUuid),
        slog.String("remote_addr", r.RemoteAddr),
    )

    // 3. Validate API key
    if !validateApiKey(apiKey) {
        logger.Warn("Invalid API key")
        http.Error(w, "Invalid API key", http.StatusUnauthorized)
        return
    }

    // 4. Validate device UUID format (basic validation)
    if deviceUuid == "" || len(deviceUuid) < 10 {
        logger.Warn("Invalid device UUID")
        http.Error(w, "Invalid device UUID", http.StatusBadRequest)
        return
    }

    logger.Info("WebSocket connection attempt")

    // 5. Look up device by UUID
    device, err := h.db.GetDeviceByUUID(r.Context(), deviceUuid)
    if err != nil {
        logger.Error("Device lookup failed", slog.String("error", err.Error()))
        http.Error(w, "Device not registered", http.StatusNotFound)
        return
    }

    // 6. Look up license by license_number
    license, err := h.db.GetLicenseByNumber(r.Context(), device.LicenseNumber)
    if err != nil {
        logger.Error("License lookup failed",
            slog.String("license_number", device.LicenseNumber),
            slog.String("error", err.Error()))
        http.Error(w, "Configuration error", http.StatusInternalServerError)
        return
    }

    // 7. Validate license status
    if license.Status != "active" {
        logger.Warn("Inactive license",
            slog.String("license_number", license.LicenseNumber),
            slog.String("status", license.Status))
        http.Error(w, "License inactive", http.StatusForbidden)
        return
    }

    if license.ExpirationDate != nil && license.ExpirationDate.Before(time.Now()) {
        logger.Warn("Expired license",
            slog.String("license_number", license.LicenseNumber),
            slog.Time("expiration_date", *license.ExpirationDate))
        http.Error(w, "License expired", http.StatusForbidden)
        return
    }

    // 8. Upgrade WebSocket connection
    conn, err := h.upgrader.Upgrade(w, r, nil)
    if err != nil {
        logger.Error("WebSocket upgrade failed", slog.String("error", err.Error()))
        return
    }

    // 9. Update device state
    err = h.db.UpdateDeviceState(r.Context(), deviceUuid, "online", time.Now())
    if err != nil {
        logger.Error("Failed to update device state", slog.String("error", err.Error()))
    }

    // 10. Store connection with metadata
    wsClient := &WebSocketClient{
        Conn:          conn,
        DeviceUUID:    deviceUuid,
        LicenseNumber: device.LicenseNumber,
        C1Base:        license.C1Base,
        ConnectedAt:   time.Now(),
    }

    h.connectionManager.AddConnection(deviceUuid, wsClient)

    logger.Info("WebSocket connected",
        slog.String("license_number", device.LicenseNumber),
        slog.String("c1_database", license.C1Base.Database))

    // 11. Send queued messages
    go h.sendQueuedMessages(deviceUuid)

    // 12. Handle connection (read messages, ping/pong, etc.)
    h.handleClient(wsClient)
}
```

---

### 4. Implement API Key Validation Function

**File:** `internal/auth/apikey.go`

```go
package auth

import (
    "os"
    "golang.org/x/crypto/bcrypt"
)

// ValidateApiKey checks if the provided API key is valid
func ValidateApiKey(apiKey string) bool {
    // Option 1: Simple comparison (for single shared key)
    expectedKey := os.Getenv("WEBSOCKET_API_KEY")
    if expectedKey == "" {
        // API key not configured
        return false
    }
    return apiKey == expectedKey
}

// ValidateApiKeySecure checks API key against hashed value in database
func ValidateApiKeySecure(db *database.DB, apiKey string) bool {
    // Option 2: Database lookup with bcrypt (more secure, allows key rotation)
    storedHash, err := db.GetActiveApiKeyHash()
    if err != nil {
        return false
    }

    err = bcrypt.CompareHashAndPassword([]byte(storedHash), []byte(apiKey))
    return err == nil
}
```

**Recommendation:** Use Option 1 (environment variable) for simplicity during initial implementation, migrate to Option 2 (database with bcrypt) for production.

---

### 5. Add API Key Rotation Support (Production)

**Endpoint:** `POST /admin/api-keys/rotate`

```go
func (h *AdminHandler) RotateApiKey(w http.ResponseWriter, r *http.Request) {
    // Generate new API key
    newKey := generateSecureApiKey()

    // Hash and store in database
    hash, err := bcrypt.GenerateFromPassword([]byte(newKey), bcrypt.DefaultCost)
    if err != nil {
        http.Error(w, "Failed to generate key", http.StatusInternalServerError)
        return
    }

    // Revoke old key and activate new key
    err = h.db.RotateApiKey(r.Context(), string(hash))
    if err != nil {
        http.Error(w, "Failed to rotate key", http.StatusInternalServerError)
        return
    }

    // Return new key (only shown once)
    w.Header().Set("Content-Type", "application/json")
    json.NewEncoder(w).Encode(map[string]string{
        "new_api_key": newKey,
        "warning": "Save this key securely. It will not be shown again.",
    })
}

func generateSecureApiKey() string {
    const charset = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
    const keyLength = 64

    b := make([]byte, keyLength)
    for i := range b {
        b[i] = charset[rand.Intn(len(charset))]
    }
    return string(b)
}
```

---

### 6. Add Logging and Monitoring

**Metrics to Track:**
```go
// Prometheus metrics
var (
    wsConnectionAttempts = prometheus.NewCounterVec(
        prometheus.CounterOpts{
            Name: "websocket_connection_attempts_total",
            Help: "Total WebSocket connection attempts",
        },
        []string{"status"},  // "success", "auth_failed", "device_not_found", etc.
    )

    wsAuthFailures = prometheus.NewCounterVec(
        prometheus.CounterOpts{
            Name: "websocket_auth_failures_total",
            Help: "Total WebSocket authentication failures",
        },
        []string{"reason"},  // "invalid_api_key", "invalid_format", "missing_header"
    )
)
```

**Log Examples:**
```go
// Success
logger.Info("WebSocket authenticated",
    slog.String("device_uuid", deviceUuid),
    slog.String("license_number", device.LicenseNumber))

// Failures
logger.Warn("Authentication failed",
    slog.String("reason", "invalid_api_key"),
    slog.String("device_uuid", deviceUuid),
    slog.String("remote_addr", r.RemoteAddr))
```

---

## 🔐 Security Considerations

### 1. API Key Security

**Strengths:**
- ✅ API key never stored in version control (local.properties is .gitignored)
- ✅ API key can be rotated without app updates (just update local.properties)
- ✅ Device UUID provides per-device identification

**Weaknesses:**
- ⚠️ Shared API key means if one device is compromised, all devices affected
- ⚠️ API key stored in BuildConfig can be extracted from APK

**Mitigations:**
1. **Code Obfuscation:** Use ProGuard/R8 in release builds
2. **API Key Rotation:** Regularly rotate the API key
3. **Rate Limiting:** Limit connection attempts per device UUID
4. **Device Registration:** Require devices to be pre-registered
5. **Anomaly Detection:** Alert on unusual connection patterns

### 2. Token Format Validation

Backend should validate:
- Token format is exactly `<api_key>:<device_uuid>`
- API key length is expected length (e.g., 64 characters)
- Device UUID is valid UUID format
- No SQL injection or special characters

### 3. Transport Security

- ✅ **Always use WSS (WebSocket Secure)** - TLS encryption
- ✅ **Nginx handles TLS termination** - Certificate validation
- ❌ **Never use WS (unencrypted)** in production

### 4. Rate Limiting

Implement rate limiting:
```go
// Per device UUID
rateLimiter := rate.NewLimiter(rate.Every(time.Second), 10)  // 10 connections per second

if !rateLimiter.Allow() {
    http.Error(w, "Too many requests", http.StatusTooManyRequests)
    return
}
```

### 5. API Key Extraction Risk

**Risk:** API key can be extracted from APK using tools like `apktool` or `jadx`.

**Mitigations:**
- Use ProGuard/R8 with aggressive obfuscation
- Store API key hash in app, validate against backend
- Implement certificate pinning
- Use Android NDK to store API key in native code (harder to extract)
- Implement runtime integrity checks

**Best Practice:** Accept that API key in APK can be extracted, design system to handle it:
- Combine API key with device registration (both required)
- Monitor for suspicious activity
- Implement device revocation
- Rotate API key periodically

---

## 📋 Implementation Checklist

### Android App

- [ ] Add `WEBSOCKET_API_KEY` to `local.properties`
- [ ] Update `build.gradle` to expose API key as `BuildConfig.WEBSOCKET_API_KEY`
- [ ] Create `ApiKeyProvider` class (optional but recommended)
- [ ] Update `WebSocketRepositoryImpl.connectInternal()` to add Authorization header
- [ ] Update `WebSocketRepositoryImpl.connect()` to validate API key exists
- [ ] Add ProGuard rules to protect BuildConfig fields
- [ ] Update code comments to document authentication flow
- [ ] Test with missing API key (should show error)
- [ ] Test with valid API key (should connect successfully)
- [ ] Test release build (ProGuard enabled)

### Backend

- [ ] Add `WEBSOCKET_API_KEY` environment variable
- [ ] Implement `validateApiKey()` function
- [ ] Update WebSocket handler to extract Authorization header
- [ ] Update WebSocket handler to parse token format
- [ ] Update WebSocket handler to validate API key
- [ ] Add logging for authentication attempts
- [ ] Add Prometheus metrics for auth success/failure
- [ ] Implement rate limiting per device UUID
- [ ] Test with missing Authorization header
- [ ] Test with invalid token format
- [ ] Test with invalid API key
- [ ] Test with valid API key and device UUID
- [ ] Document API key generation process
- [ ] Create API key rotation endpoint (optional, for production)

### Documentation

- [ ] Update `BACKEND_LICENSE_MANAGEMENT.md` with new auth flow
- [ ] Update `ANDROID_WEBSOCKET_IMPLEMENTATION_PLAN.md` with Authorization header
- [ ] Update `CLAUDE.md` with API key usage notes
- [ ] Create setup guide for developers (how to add API key to local.properties)
- [ ] Document API key rotation procedure

---

## 🧪 Testing Scenarios

### 1. Valid Authentication
```
Request:
  Authorization: Bearer abc123xyz789:uuid-device-123

Backend:
  ✅ API key valid
  ✅ Device UUID found in database
  ✅ License active and not expired
  → Connection accepted
```

### 2. Missing Authorization Header
```
Request:
  (no Authorization header)

Backend:
  ❌ Missing Authorization
  → 401 Unauthorized
```

### 3. Invalid Token Format
```
Request:
  Authorization: Bearer invalid_token_format

Backend:
  ❌ Token format invalid (no colon separator)
  → 401 Unauthorized
```

### 4. Invalid API Key
```
Request:
  Authorization: Bearer wrong_api_key:uuid-device-123

Backend:
  ❌ API key doesn't match
  → 401 Unauthorized
```

### 5. Device Not Registered
```
Request:
  Authorization: Bearer abc123xyz789:uuid-unknown-999

Backend:
  ✅ API key valid
  ❌ Device UUID not found
  → 404 Not Found
```

### 6. Inactive License
```
Request:
  Authorization: Bearer abc123xyz789:uuid-device-123

Backend:
  ✅ API key valid
  ✅ Device UUID found
  ❌ License status = "suspended"
  → 403 Forbidden
```

### 7. Expired License
```
Request:
  Authorization: Bearer abc123xyz789:uuid-device-123

Backend:
  ✅ API key valid
  ✅ Device UUID found
  ❌ License expiration_date < now()
  → 403 Forbidden
```

---

## 🚀 Deployment Plan

### Phase 1: Development Environment
1. Generate test API key
2. Add to `local.properties` on development machines
3. Update Android app code
4. Update backend code
5. Test end-to-end connection
6. Verify authentication flow

### Phase 2: Staging Environment
1. Generate staging API key
2. Configure backend environment variable
3. Deploy updated backend
4. Build Android APK with staging API key
5. Test with multiple devices
6. Verify device registration flow
7. Test license validation scenarios

### Phase 3: Production Deployment
1. Generate production API key (64+ characters, cryptographically secure)
2. Store API key in secure location (password manager, vault)
3. Configure production backend environment variable
4. Deploy backend with monitoring enabled
5. Build production APK with API key
6. Distribute API key to development team securely
7. Monitor authentication metrics
8. Document API key rotation procedure

---

## 🔄 API Key Rotation Procedure

### When to Rotate
- Suspected compromise
- Developer leaves team
- Regular security practice (e.g., annually)
- APK with key is publicly distributed

### How to Rotate
1. **Generate new API key** on backend
2. **Update backend** to accept both old and new keys temporarily
3. **Update `local.properties`** with new key
4. **Build new APK** with new key
5. **Distribute new APK** to all devices
6. **Wait for all devices to update** (monitor connection logs)
7. **Revoke old API key** on backend
8. **Verify** all active connections use new key

### Zero-Downtime Rotation
```go
// Support multiple active keys during transition
var activeApiKeys = []string{
    os.Getenv("WEBSOCKET_API_KEY_PRIMARY"),
    os.Getenv("WEBSOCKET_API_KEY_SECONDARY"),  // Old key during rotation
}

func validateApiKey(apiKey string) bool {
    for _, validKey := range activeApiKeys {
        if apiKey == validKey {
            return true
        }
    }
    return false
}
```

---

## 📊 Comparison: Before vs After

### Before (No Authentication)
```
WebSocket URL: wss://server/ws/device?uuid=device-123
Authentication: None
Validation: Device UUID existence only
```

### After (API Key Authentication)
```
WebSocket URL: wss://server/ws/device
Authorization: Bearer <API_KEY>:<DEVICE_UUID>
Validation: API key + Device UUID + License status
```

### Benefits
- ✅ **Authentication:** Validates request comes from legitimate Android app
- ✅ **Device Identification:** Device UUID still identifies individual device
- ✅ **License Routing:** Backend still uses device-to-license mapping
- ✅ **Security:** Adds layer of protection against unauthorized connections
- ✅ **Flexibility:** API key can be rotated without backend schema changes

### Trade-offs
- ⚠️ **Shared Key:** All devices share same API key (acceptable for this use case)
- ⚠️ **APK Extraction:** API key can be extracted from APK (mitigated by obfuscation + device registration)
- ⚠️ **Key Management:** Need process for distributing and rotating API key

---

## ❓ Open Questions

1. **API Key Length:** What length should the API key be?
   - **Recommendation:** 64 characters (alphanumeric)

2. **API Key Generation:** How should the initial API key be generated?
   - **Recommendation:** Use cryptographically secure random generator
   - **Tool:** `openssl rand -base64 48` or custom Go function

3. **Multiple API Keys:** Should backend support multiple active API keys?
   - **Recommendation:** Yes, for zero-downtime rotation
   - **Implementation:** Environment variables `WEBSOCKET_API_KEY_PRIMARY` and `WEBSOCKET_API_KEY_SECONDARY`

4. **API Key in Logs:** Should API key be logged?
   - **Recommendation:** NO - log only last 4 characters for debugging
   - **Example:** `api_key=****xyz9`

5. **Developer Distribution:** How to securely share API key with developers?
   - **Recommendation:** Use password manager (1Password, LastPass) or secure vault
   - **Alternative:** Each developer generates their own key for development

---

## 📚 Related Documentation

- `BACKEND_LICENSE_MANAGEMENT.md` - License architecture and device registration
- `ANDROID_WEBSOCKET_IMPLEMENTATION_PLAN.md` - WebSocket implementation details
- `CLAUDE.md` - License usage clarifications
- `local.properties` - API key storage (not in git)

---

## Summary

This plan implements API key authentication for WebSocket connections using the format `<API_KEY>:<DEVICE_UUID>` sent via Authorization header. The API key validates the request is from a legitimate Android app, while the device UUID identifies the specific device for backend routing logic. This approach balances security, simplicity, and flexibility while maintaining the existing device-to-license architecture.
