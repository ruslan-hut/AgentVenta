# WebSocket Implementation Test Checklist

**Date**: ____________
**Tester**: ____________
**Build Version**: ____________

---

## Prerequisites

- [ ] App built and installed: `./gradlew installDebug`
- [ ] `local.properties` contains `WEBSOCKET_API_KEY` and `KEY_HOST`
- [ ] Backend server running and accessible
- [ ] Test device/emulator connected

---

## Phase 1: Core Connection Tests (P0 - Critical)

### 1.1 Basic WebSocket Connection
- [ ] Open app
- [ ] Wait for connection (check test UI or logs)
- [ ] **Expected**: `Connected` state displayed
- [ ] **Log to check**: `WebSocket: Connected successfully`
- **Notes**: ________________________________________________

### 1.2 Bearer Token Verification
- [ ] Check backend server logs
- [ ] **Expected**: Token format is `<API_KEY>:<DEVICE_UUID>`
- [ ] **Not**: Basic Auth or just UUID
- **Notes**: ________________________________________________

### 1.3 Device Approval - New Device
- [ ] Clear app data or use fresh install
- [ ] Launch app
- [ ] **Expected**: `Pending Approval` state shown
- [ ] **Expected**: No reconnection attempts while pending
- **Notes**: ________________________________________________

### 1.4 Device Approval - After Admin Approval
- [ ] Approve device in backend admin panel
- [ ] Wait for state change
- [ ] **Expected**: Transitions to `Connected`
- [ ] **Expected**: Data sync now allowed
- **Notes**: ________________________________________________

### 1.5 License Error Handling
- [ ] Use device with expired/invalid license
- [ ] **Expected**: `LicenseError` state shown
- [ ] **Expected**: No reconnection attempts
- [ ] **Expected**: Error reason displayed
- **Notes**: ________________________________________________

---

## Phase 2: Document Upload Tests (P0 - Critical)

### 2.1 Order Upload via WebSocket
- [ ] Ensure `useWebSocket = true` (default)
- [ ] Create new order with 1+ products
- [ ] Process order (mark ready to send)
- [ ] Trigger sync (Settings → Sync → Differential)
- [ ] **Expected log**: `Using WebSocket sync`
- [ ] **Expected log**: `Uploading X orders via WebSocket`
- [ ] **Database check**: Order `is_sent = 1` after ACK
```sql
SELECT number, is_sent FROM orders WHERE is_processed = 1 ORDER BY date DESC LIMIT 5;
```
- **Notes**: ________________________________________________

### 2.2 Order Upload via HTTP (Backward Compatibility)
- [ ] Set `useWebSocket = false` in settings
- [ ] Create new order with 1+ products
- [ ] Process order
- [ ] Trigger sync
- [ ] **Expected log**: `Using HTTP sync`
- [ ] **Network check**: POST to `/post/<token>` endpoint
- [ ] **Database check**: Order `is_sent = 1`
- **Notes**: ________________________________________________

### 2.3 Cash Receipt Upload via WebSocket
- [ ] Ensure `useWebSocket = true`
- [ ] Create cash receipt
- [ ] Trigger sync
- [ ] **Expected**: ACK received
- [ ] **Database check**: Cash `is_sent = 1`
```sql
SELECT number, is_sent FROM cash WHERE is_processed = 1 ORDER BY date DESC LIMIT 5;
```
- **Notes**: ________________________________________________

### 2.4 Image Upload via WebSocket
- [ ] Navigate to client
- [ ] Capture new image
- [ ] Trigger sync
- [ ] **Expected**: Image uploaded via WebSocket
- [ ] **Database check**: `is_local = 0` after ACK
```sql
SELECT guid, is_local, is_sent FROM client_images ORDER BY timestamp DESC LIMIT 5;
```
- **Notes**: ________________________________________________

### 2.5 Location Upload via WebSocket
- [ ] Enable location tracking
- [ ] Visit client (or simulate GPS)
- [ ] Trigger sync
- [ ] **Expected**: Locations uploaded via WebSocket
- [ ] **Database check**: `is_modified = 0` after ACK
- **Notes**: ________________________________________________

---

## Phase 3: Catalog Download Tests (P1 - High)

### 3.1 Receive Catalogs via WebSocket
- [ ] Server pushes `download_catalogs` message
- [ ] **Expected log**: `Received catalog update: <type>`
- [ ] **Database check**: Data saved, count increases
```sql
SELECT COUNT(*) FROM clients WHERE db_guid = '<current_account_guid>';
SELECT COUNT(*) FROM goods WHERE db_guid = '<current_account_guid>';
```
- **Notes**: ________________________________________________

### 3.2 Options Update from Server
- [ ] Server pushes options update
- [ ] **Expected**: `UserAccount.options` field updated
- [ ] **Expected**: Features enabled/disabled based on options
- **Notes**: ________________________________________________

### 3.3 License from Pong Response
- [ ] Monitor WebSocket pong messages
- [ ] **Expected**: License number extracted and saved
- [ ] **Database check**: `license` field populated
```sql
SELECT description, license FROM user_accounts WHERE is_current = 1;
```
- **Notes**: ________________________________________________

---

## Phase 4: Mode Switching Tests (P1 - High)

### 4.1 WebSocket → HTTP Switch
- [ ] Start with `useWebSocket = true`
- [ ] Create Order #1
- [ ] Sync → Verify WebSocket used (check logs)
- [ ] Toggle `useWebSocket = false` in settings
- [ ] Create Order #2
- [ ] Sync → Verify HTTP used (check logs)
- **Notes**: ________________________________________________

### 4.2 HTTP → WebSocket Switch
- [ ] Start with `useWebSocket = false`
- [ ] Toggle `useWebSocket = true`
- [ ] Create Order #3
- [ ] Sync → Verify WebSocket used
- **Notes**: ________________________________________________

### 4.3 Data Integrity After Mode Switching
- [ ] All 3 orders from 4.1 and 4.2 are `is_sent = 1`
- [ ] No data loss
- [ ] No sync errors in logs
```sql
SELECT number, is_sent FROM orders ORDER BY date DESC LIMIT 10;
```
- **Notes**: ________________________________________________

---

## Phase 5: Error Handling Tests (P1 - High)

### 5.1 Network Loss
- [ ] With WebSocket connected, enable airplane mode
- [ ] **Expected**: State changes to `Error` or `Reconnecting`
- [ ] **Expected log**: `WebSocket: Connection lost`
- **Notes**: ________________________________________________

### 5.2 Network Restore
- [ ] Disable airplane mode
- [ ] **Expected**: Automatic reconnection
- [ ] **Expected**: State returns to `Connected`
- [ ] **Expected log**: `WebSocket: Reconnected successfully`
- **Notes**: ________________________________________________

### 5.3 Message Retry Logic
- [ ] Create order while connected
- [ ] Kill backend server before ACK
- [ ] **Expected**: Message stays in pending queue
- [ ] **Expected**: Retry up to 3 times
- [ ] Restore server
- [ ] **Expected**: Order eventually syncs
- **Notes**: ________________________________________________

### 5.4 Offline Document Creation
- [ ] Disconnect network
- [ ] Create order
- [ ] **Expected**: Order saved locally with `is_sent = 0`
- [ ] Restore network and reconnect
- [ ] Trigger sync
- [ ] **Expected**: Order uploads successfully
- **Notes**: ________________________________________________

---

## Phase 6: Background & Lifecycle Tests (P2 - Medium)

### 6.1 App Background (No Pending Data)
- [ ] Ensure no unsent documents
- [ ] Background the app (home button)
- [ ] Wait 5+ minutes
- [ ] **Expected**: WebSocket disconnects after grace period
- **Notes**: ________________________________________________

### 6.2 App Background (With Pending Data)
- [ ] Create order but don't sync
- [ ] Background the app
- [ ] Wait 5 minutes
- [ ] **Expected**: WebSocket stays connected
- **Notes**: ________________________________________________

### 6.3 WorkManager Background Sync
- [ ] Background app
- [ ] Wait 15+ minutes (WorkManager interval)
- [ ] **Expected**: Worker checks for pending data
- [ ] **Expected log**: `WebSocketSyncWorker: Checking pending data`
- **Notes**: ________________________________________________

### 6.4 App Foreground Reconnection
- [ ] Background app until disconnected
- [ ] Resume app
- [ ] **Expected**: Immediate reconnection
- **Notes**: ________________________________________________

---

## Phase 7: HTTP Backward Compatibility (P0 - Critical)

### 7.1 Full Sync via HTTP
- [ ] Set `useWebSocket = false`
- [ ] Trigger full sync (Settings → Sync → Full Sync)
- [ ] **Expected**: All catalogs downloaded via HTTP
- [ ] **Expected log**: `GET /get/<type>/<token>`
- [ ] **Database check**: Catalogs populated
- **Notes**: ________________________________________________

### 7.2 Token Refresh on 401
- [ ] Let HTTP token expire (or manually invalidate)
- [ ] Trigger sync
- [ ] **Expected**: 401 response triggers token refresh
- [ ] **Expected log**: `TokenRefresh: Refreshing token`
- [ ] **Expected**: Sync completes after refresh
- **Notes**: ________________________________________________

### 7.3 HTTP Pagination
- [ ] Download large catalog (many clients/products)
- [ ] **Expected**: Multiple requests with "more" parameter
- [ ] **Expected log**: `makeDataRequest: more=<element_id>`
- **Notes**: ________________________________________________

---

## Quick Commands Reference

### View Logs
```bash
adb logcat -s NetworkRepo:* WebSocket:* DataExchange:*
```

### Database Query (via ADB)
```bash
adb shell "sqlite3 /data/data/ua.com.programmer.agentventa/databases/app_database '<SQL>'"
```

### Check Current Account Mode
```bash
adb shell "sqlite3 /data/data/ua.com.programmer.agentventa/databases/app_database 'SELECT description, use_websocket, license FROM user_accounts WHERE is_current=1'"
```

### Toggle WebSocket Mode
```bash
# Enable WebSocket
adb shell "sqlite3 /data/data/ua.com.programmer.agentventa/databases/app_database 'UPDATE user_accounts SET use_websocket=1 WHERE is_current=1'"

# Disable WebSocket (HTTP mode)
adb shell "sqlite3 /data/data/ua.com.programmer.agentventa/databases/app_database 'UPDATE user_accounts SET use_websocket=0 WHERE is_current=1'"
```

### Check Unsent Documents
```bash
adb shell "sqlite3 /data/data/ua.com.programmer.agentventa/databases/app_database 'SELECT COUNT(*) FROM orders WHERE is_sent=0 AND is_processed=1'"
```

---

## Test Results Summary

| Phase | Tests | Passed | Failed | Notes |
|-------|-------|--------|--------|-------|
| 1. Core Connection | 5 | | | |
| 2. Document Upload | 5 | | | |
| 3. Catalog Download | 3 | | | |
| 4. Mode Switching | 3 | | | |
| 5. Error Handling | 4 | | | |
| 6. Background/Lifecycle | 4 | | | |
| 7. HTTP Compatibility | 3 | | | |
| **TOTAL** | **27** | | | |

---

## Issues Found

| # | Phase | Test | Description | Severity | Status |
|---|-------|------|-------------|----------|--------|
| 1 | | | | | |
| 2 | | | | | |
| 3 | | | | | |

---

## Sign-off

- [ ] All P0 tests passed
- [ ] All P1 tests passed
- [ ] P2 tests completed (or documented blockers)
- [ ] No critical issues remaining

**Tested by**: ____________
**Date**: ____________
