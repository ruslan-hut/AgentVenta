# WebSocket Document Sync - Testing Guide

## Overview
This guide provides step-by-step instructions for testing the WebSocket document synchronization implementation completed in Phases 4 and 5.

## Prerequisites

### 1. Backend Server Setup
- WebSocket relay server must be running and accessible
- Server must have the following endpoints configured:
  - WebSocket connection: `wss://<backend_host>/ws?uuid=<device_uuid>`
  - Message handling for: `upload_order`, `upload_cash`, `upload_image`, `upload_location`, `download_catalogs`

### 2. App Configuration
- **local.properties** must contain:
  ```properties
  WEBSOCKET_API_KEY=<your_api_key>
  KEY_HOST=<backend_host>
  ```

### 3. Database State
- At least one UserAccount configured in the app
- Test data: orders, cash receipts, images, locations ready for sync

---

## Test Setup: Enable WebSocket Mode

### Option A: Via UI (UserAccountFragment)
1. Open app → Settings → User Account
2. Find the "WebSocket Connection" section
3. Toggle **"Use WebSocket"** switch to ON
4. The account field `useWebSocket` is now set to `true`

### Option B: Via Database (for testing)
```sql
UPDATE user_accounts
SET useWebSocket = 1
WHERE is_current = 1;
```

### Verify WebSocket Mode is Active
Check logs for:
```
NetworkRepo: Using WebSocket sync for account: <account_description>
```

---

## Test Cases

## Test 1: WebSocket Document Upload ✅

**Objective:** Verify documents (orders) are uploaded via WebSocket instead of HTTP

**Steps:**
1. **Setup**
   - Enable WebSocket mode for current account (`useWebSocket = true`)
   - Create a new order in the app with at least 1 product
   - Note the order number/GUID

2. **Execute Sync**
   - Go to Settings → Sync
   - Tap "Differential Sync" button
   - Observe the sync process

3. **Validation Points**
   - [ ] **Logs show WebSocket routing:**
     ```
     NetworkRepo: Using WebSocket sync for account: <account>
     NetworkRepo: Uploading 1 orders via WebSocket...
     WebSocket: Message sent: type=upload_order, id=<message_id>
     WebSocket: Ack received: <message_id> (status: queued)
     NetworkRepo: Order <number> uploaded successfully via WebSocket
     ```

   - [ ] **Backend receives message:**
     - Check server logs/queue for incoming message type: `upload_order`
     - Verify payload contains order data and content

   - [ ] **Database updated:**
     ```sql
     SELECT isSent, isProcessed FROM orders WHERE guid = '<order_guid>';
     -- Expected: isSent = 1 (marked as sent after server ACK)
     ```

   - [ ] **No HTTP calls made:**
     - Check network logs - should NOT see POST to `/post/<token>`

**Expected Result:** Order successfully uploaded via WebSocket, ACK received, order marked as sent

---

## Test 2: HTTP Document Upload (Manual Mode) ✅

**Objective:** Verify HTTP sync still works when WebSocket is disabled

**Steps:**
1. **Setup**
   - Disable WebSocket mode (`useWebSocket = false`)
   - Create a new order in the app
   - Note the order number/GUID

2. **Execute Sync**
   - Go to Settings → Sync
   - Tap "Differential Sync" button

3. **Validation Points**
   - [ ] **Logs show HTTP routing:**
     ```
     NetworkRepo: Using HTTP sync for account: <account>
     ```

   - [ ] **HTTP POST request made:**
     - Check network logs for: `POST https://<1c_server>/simple/hs/venta/post/<token>`
     - Verify order JSON in request body

   - [ ] **Server response processed:**
     ```
     NetworkRepo: send: ok with warn: <status>
     ```

   - [ ] **Database updated:**
     ```sql
     SELECT isSent, status FROM orders WHERE guid = '<order_guid>';
     -- Expected: isSent = 1, status = from server response
     ```

**Expected Result:** Order uploaded via traditional HTTP POST, existing flow unchanged

---

## Test 3: WebSocket Catalog Download ✅

**Objective:** Verify catalog updates received via WebSocket are saved to database

**Steps:**
1. **Setup**
   - Enable WebSocket mode
   - Ensure WebSocket connection is established
   - Note current client count in database:
     ```sql
     SELECT COUNT(*) FROM clients WHERE db_guid = '<account_guid>';
     ```

2. **Trigger Catalog Request**
   - In app, trigger differential sync
   - Or manually call: `networkRepository.downloadCatalogsViaWebSocket(fullSync = false)`

3. **Server Action**
   - Server should push catalog update message:
     ```json
     {
       "type": "download_catalogs",
       "message_id": "12345",
       "payload": {
         "catalog_type": "clients",
         "data": [
           { "guid": "client1", "description": "Test Client 1", ... },
           { "guid": "client2", "description": "Test Client 2", ... }
         ]
       }
     }
     ```

4. **Validation Points**
   - [ ] **Logs show catalog reception:**
     ```
     WebSocket: Received catalog update: clients (X items)
     WebSocket: Saved X items for catalog: clients
     WebSocket: Sent ACK for catalog clients (X items)
     ```

   - [ ] **Database updated:**
     ```sql
     SELECT COUNT(*) FROM clients WHERE db_guid = '<account_guid>';
     -- Expected: Count increased by number of items in update

     SELECT * FROM clients WHERE guid = 'client1' AND db_guid = '<account_guid>';
     -- Expected: New client record exists
     ```

   - [ ] **UI reflects changes:**
     - Navigate to Clients list
     - Verify new clients appear (may need to refresh/reopen screen)

   - [ ] **ACK sent to server:**
     - Server logs should show ACK received for message_id

**Expected Result:** Catalog data received, saved to database, ACK sent, UI updated

---

## Test 4: Mode Switching ✅

**Objective:** Verify seamless switching between WebSocket and HTTP modes

**Steps:**
1. **Start in WebSocket Mode**
   - Enable WebSocket (`useWebSocket = true`)
   - Create Order #1
   - Sync → Verify uploaded via WebSocket (check logs)

2. **Switch to HTTP Mode**
   - Disable WebSocket (`useWebSocket = false`)
   - Create Order #2
   - Sync → Verify uploaded via HTTP (check logs)

3. **Switch Back to WebSocket**
   - Enable WebSocket (`useWebSocket = true`)
   - Create Order #3
   - Sync → Verify uploaded via WebSocket

4. **Validation Points**
   - [ ] **All 3 orders marked as sent:**
     ```sql
     SELECT number, isSent FROM orders WHERE number IN (<order1>, <order2>, <order3>);
     -- All should have isSent = 1
     ```

   - [ ] **Logs show correct routing for each:**
     - Order #1: "Using WebSocket sync"
     - Order #2: "Using HTTP sync"
     - Order #3: "Using WebSocket sync"

   - [ ] **No data loss:**
     - All orders present in database
     - All orders successfully synced

   - [ ] **No sync errors:**
     - Check logs for errors or warnings

**Expected Result:** App correctly routes sync based on current mode setting, no data loss

---

## Test 5: Error Handling ✅

**Objective:** Verify robust error handling and retry logic

### Test 5a: WebSocket Disconnection During Upload

**Steps:**
1. Enable WebSocket mode
2. Disconnect network (airplane mode) or stop WebSocket server
3. Create new order
4. Attempt sync

**Validation Points:**
- [ ] **Error logged:**
  ```
  WebSocket: Not connected
  NetworkRepo: Failed to upload order: Not connected
  ```

- [ ] **Document NOT marked as sent:**
  ```sql
  SELECT isSent FROM orders WHERE guid = '<order_guid>';
  -- Expected: isSent = 0
  ```

- [ ] **UI shows error message:**
  - Sync UI should display error/warning

- [ ] **Reconnection attempt:**
  ```
  WebSocket: Reconnecting in Xms (attempt Y)
  ```

- [ ] **Retry after reconnection:**
  - Re-enable network
  - Wait for reconnection
  - Trigger sync again
  - Verify order now uploads successfully

**Expected Result:** Graceful error handling, document preserved, can retry after reconnection

### Test 5b: Invalid Document Format

**Steps:**
1. Modify code temporarily to send invalid JSON
2. Attempt sync

**Validation Points:**
- [ ] **Error logged on server side**
- [ ] **Client receives error message:**
  ```
  WebSocket: Server error: <error_message>
  ```

- [ ] **Document remains unsent:**
  ```sql
  SELECT isSent FROM orders WHERE guid = '<order_guid>';
  -- Expected: isSent = 0
  ```

**Expected Result:** Invalid data rejected, error logged, document can be corrected and retried

### Test 5c: Server ACK Timeout

**Steps:**
1. Configure server to delay ACK response (if possible)
2. Upload document
3. Wait for timeout

**Validation Points:**
- [ ] **Message remains in pending state**
- [ ] **Retry logic triggers**
- [ ] **Eventually succeeds or fails gracefully**

**Expected Result:** Pending messages tracked, retries attempted, eventual success or clear failure

---

## Logging & Monitoring

### Key Log Tags to Watch:
```
NetworkRepo - Sync routing decisions and document upload
WebSocket - Connection state, message send/receive
DataExchange - Database operations
```

### Enable Verbose Logging:
Add to your test device:
```bash
adb shell setprop log.tag.NetworkRepo VERBOSE
adb shell setprop log.tag.WebSocket VERBOSE
```

### View Real-Time Logs:
```bash
adb logcat -s NetworkRepo:* WebSocket:* DataExchange:*
```

---

## Database Queries for Validation

### Check WebSocket Mode:
```sql
SELECT description, useWebSocket, license
FROM user_accounts
WHERE is_current = 1;
```

### Check Unsent Documents:
```sql
SELECT number, date, isSent, isProcessed
FROM orders
WHERE db_guid = '<account_guid>' AND isSent = 0;
```

### Check Sync Results:
```sql
SELECT * FROM send_results
WHERE account = '<account_guid>'
ORDER BY timestamp DESC
LIMIT 10;
```

### Check Catalog Update Timestamp:
```sql
SELECT COUNT(*), MAX(timestamp)
FROM clients
WHERE db_guid = '<account_guid>';
```

---

## Troubleshooting

### WebSocket Not Connecting:
1. Verify `local.properties` has correct `WEBSOCKET_API_KEY` and `KEY_HOST`
2. Check server is running and accessible
3. Verify device UUID is approved on server
4. Check logs for: `WebSocket: Device is pending approval`

### Documents Not Uploading:
1. Verify `useWebSocket = true` in database
2. Check WebSocket connection state: `WebSocket: Connected successfully`
3. Verify documents have `isSent = 0`
4. Check for errors in logs

### Catalogs Not Updating:
1. Verify server is pushing catalog messages
2. Check logs for: `Received catalog update`
3. Verify `DataExchangeRepository.saveData()` is called
4. Check database timestamp on catalog items

### Mode Switch Not Working:
1. Restart app after changing `useWebSocket` flag
2. Verify logs show correct routing
3. Check `NetworkRepositoryImpl.sendDocuments()` is checking flag correctly

---

## Success Criteria

✅ **All tests pass:**
- [ ] Test 1: WebSocket Document Upload
- [ ] Test 2: HTTP Document Upload (Manual Mode)
- [ ] Test 3: WebSocket Catalog Download
- [ ] Test 4: Mode Switching
- [ ] Test 5: Error Handling

✅ **Performance acceptable:**
- WebSocket upload completes in < 2 seconds
- Catalog updates process in < 5 seconds
- No UI freezes during sync

✅ **No regressions:**
- HTTP sync still works perfectly
- Existing features unaffected
- Database integrity maintained

---

## Next Steps After Testing

1. **If tests pass:**
   - Document any configuration requirements for production
   - Update user documentation
   - Deploy to beta testers

2. **If tests fail:**
   - Document failure scenarios
   - Review logs and fix issues
   - Re-test until all scenarios pass

3. **Production considerations:**
   - Monitor server load with WebSocket connections
   - Set up alerting for failed sync operations
   - Plan for gradual rollout (feature flag approach)
