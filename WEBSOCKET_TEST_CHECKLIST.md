# WebSocket Sync - Quick Test Checklist

## Pre-Testing Setup ✓

- [ ] Backend WebSocket server is running
- [ ] `local.properties` has `WEBSOCKET_API_KEY` and `KEY_HOST`
- [ ] Test account exists in app
- [ ] Device UUID is approved on backend (not pending)
- [ ] ADB connected for log monitoring

---

## Test 1: WebSocket Document Upload

- [ ] Enable WebSocket mode (`useWebSocket = true`)
- [ ] Create new order
- [ ] Trigger sync
- [ ] ✓ Log shows: "Using WebSocket sync"
- [ ] ✓ Log shows: "Message sent: type=upload_order"
- [ ] ✓ Log shows: "Ack received"
- [ ] ✓ Database: `isSent = 1`
- [ ] ✓ Server received message in queue

---

## Test 2: HTTP Document Upload

- [ ] Disable WebSocket mode (`useWebSocket = false`)
- [ ] Create new order
- [ ] Trigger sync
- [ ] ✓ Log shows: "Using HTTP sync"
- [ ] ✓ HTTP POST request in network logs
- [ ] ✓ Database: `isSent = 1`
- [ ] ✓ Server received HTTP request

---

## Test 3: WebSocket Catalog Download

- [ ] Enable WebSocket mode
- [ ] Note current client count
- [ ] Trigger sync
- [ ] Server pushes catalog update
- [ ] ✓ Log shows: "Received catalog update: clients"
- [ ] ✓ Log shows: "Saved X items for catalog"
- [ ] ✓ Log shows: "Sent ACK for catalog"
- [ ] ✓ Database: client count increased
- [ ] ✓ UI shows new clients

---

## Test 4: Mode Switching

- [ ] WebSocket ON → Create Order #1 → Sync
- [ ] ✓ Order #1 uploaded via WebSocket
- [ ] WebSocket OFF → Create Order #2 → Sync
- [ ] ✓ Order #2 uploaded via HTTP
- [ ] WebSocket ON → Create Order #3 → Sync
- [ ] ✓ Order #3 uploaded via WebSocket
- [ ] ✓ All 3 orders have `isSent = 1`
- [ ] ✓ No data loss

---

## Test 5: Error Handling

### 5a: Disconnection During Upload
- [ ] Enable airplane mode
- [ ] Create order → Sync
- [ ] ✓ Error logged: "Not connected"
- [ ] ✓ Database: `isSent = 0`
- [ ] ✓ UI shows error
- [ ] Disable airplane mode
- [ ] ✓ Reconnection logged
- [ ] Sync again
- [ ] ✓ Order uploads successfully

### 5b: Invalid Format
- [ ] Send invalid JSON (manual test)
- [ ] ✓ Server error logged
- [ ] ✓ Database: `isSent = 0`
- [ ] ✓ Document preserved for retry

---

## Logs to Monitor

```bash
# Real-time logging
adb logcat -s NetworkRepo:* WebSocket:* DataExchange:*

# Key log patterns to look for:
# "Using WebSocket sync" or "Using HTTP sync"
# "Message sent: type=upload_order"
# "Ack received"
# "Received catalog update"
# "Saved X items for catalog"
```

---

## Quick Database Checks

```sql
-- Check WebSocket mode
SELECT description, useWebSocket FROM user_accounts WHERE is_current = 1;

-- Check unsent documents
SELECT number, isSent FROM orders WHERE db_guid = '<account>' AND isSent = 0;

-- Check client count
SELECT COUNT(*) FROM clients WHERE db_guid = '<account>';
```

---

## Success Criteria

✅ All 5 test categories pass
✅ Logs show correct routing (WS vs HTTP)
✅ Documents marked as sent after ACK
✅ Catalogs saved to database
✅ No errors or crashes
✅ Mode switching works seamlessly

---

## If Tests Fail

1. Check logs for error messages
2. Verify server is running and accessible
3. Check database state (isSent flags)
4. Verify WebSocket connection established
5. Review WEBSOCKET_TESTING_GUIDE.md for troubleshooting
