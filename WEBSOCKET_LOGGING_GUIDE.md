# WebSocket Message Flow Logging Guide

## Overview

Comprehensive logging has been added to debug WebSocket message flow. All incoming messages, parsing steps, and processing are logged to Android's system log with detailed information about payload structure, errors, and processing steps.

---

## How to View Logs

### Using adb logcat

```bash
# Filter for WebSocket logs only
adb logcat -s WebSocket WSMessageFactory

# More detailed with timestamps
adb logcat -v time -s WebSocket WSMessageFactory

# Save to file
adb logcat -v time -s WebSocket WSMessageFactory > websocket_debug.log
```

### Using Android Studio

1. Open **Logcat** panel (bottom of IDE)
2. Filter by tag: `WebSocket` or `WSMessageFactory`
3. Select log level: **Debug** (shows all levels)

---

## Log Structure

### 1. Incoming Message

When a WebSocket message arrives, you'll see:

```
D/WebSocket: === INCOMING WEBSOCKET MESSAGE ===
D/WebSocket: Payload length: 523 chars
D/WebSocket: Payload preview: {"type":"data","message_id":"msg-001",...
D/WebSocket: Payload continues... (23 more chars)
```

**What this shows:**
- Total message size
- First 500 characters of payload
- Remaining character count

---

### 2. Message Parsing

```
D/WSMessageFactory: Parsing message: {"type":"data","message_id":"msg-001",...
D/WSMessageFactory: Parse success - type: data, messageId: msg-001
```

**Or if parsing fails:**
```
E/WSMessageFactory: JSON PARSE ERROR: Unterminated object at line 1...
E/WSMessageFactory: Failed text: {"type":"data","message_id":...
E/WSMessageFactory: Exception: com.google.gson.JsonSyntaxException...
```

**What this shows:**
- Raw message being parsed
- Success with message type and ID
- Detailed error if JSON is invalid

---

### 3. Message Structure Analysis

```
D/WebSocket: Message type: data
D/WebSocket: Message ID: msg-001
D/WebSocket: Timestamp: 2025-11-30T20:00:00Z
D/WebSocket: Status: approved
D/WebSocket: --- DATA MESSAGE PROCESSING ---
D/WebSocket: Payload type: ARRAY
D/WebSocket: Array size: 3
D/WebSocket: Payload content: [{"value_id":"options"...
```

**What this shows:**
- Message envelope details
- Whether payload is ARRAY or OBJECT
- Array size if applicable
- Payload content

---

### 4. Data Message Parsing

```
D/WSMessageFactory: --- parseDataMessage ---
D/WSMessageFactory: SIMPLIFIED FORMAT detected: payload is JsonArray
D/WSMessageFactory: Array size: 3
D/WSMessageFactory: Created IncomingDataMessage with data_type: catalog
```

**Or for legacy format:**
```
D/WSMessageFactory: LEGACY FORMAT detected: payload is JsonObject
D/WSMessageFactory: Legacy data_type: settings
D/WSMessageFactory: Legacy data is array (size: 1)
D/WSMessageFactory: Created IncomingDataMessage with data_type: settings
```

**Or if format is invalid:**
```
E/WSMessageFactory: Legacy format missing 'data_type' field
E/WSMessageFactory: Payload: {"data":[...]}
```

**What this shows:**
- Format detection (simplified vs legacy)
- Data type identification
- Parsing errors with payload details

---

### 5. Routing Decision

```
D/WebSocket: Parsed data_type: catalog
D/WebSocket: Routing to handleUnifiedPayload (simplified array format)
```

**What this shows:**
- Which handler will process the message
- Simplified vs legacy routing

---

### 6. Unified Payload Processing

```
D/WebSocket: === HANDLING UNIFIED PAYLOAD ===
D/WebSocket: Message ID: msg-001
D/WebSocket: Data array size: 3
D/WebSocket: Processing 3 items...
```

**For each item:**
```
D/WebSocket: Item 0: {"value_id":"options","write":true...
D/WebSocket: Item 0 value_id: options
D/WebSocket: Detected OPTIONS item
```

**Summary:**
```
D/WebSocket: Item counts by value_id: {options=1, clients=2}
D/WebSocket: Options items: 1
D/WebSocket: Catalog items: 2
```

**What this shows:**
- Number of items being processed
- Each item's value_id
- Item type detection
- Summary counts by type

---

### 7. Options Processing

```
D/WebSocket: Processing 1 options items...
D/WebSocket: --- processOptionsItems ---
D/WebSocket: Number of options items: 1
D/WebSocket: Current account: device-uuid-12345
D/WebSocket: Original options object: {"value_id":"options","write":true...
D/WebSocket: Removed value_id field
D/WebSocket: Options JSON length: 523 chars
D/WebSocket: Options JSON: {"write":true,"read":false,...}
D/WebSocket: Saving account to database...
D/WebSocket: SUCCESS: Options saved to database
D/WebSocket: Sending ACK for options update
```

**Or if error:**
```
E/WebSocket: ERROR: No current account available
E/WebSocket: DATABASE SAVE ERROR: database is locked
E/WebSocket: Exception: android.database.sqlite.SQLiteDatabaseLockedException...
```

**What this shows:**
- Options being processed
- JSON transformation
- Database save success/failure
- Detailed error stack traces

---

### 8. Catalog Processing

```
D/WebSocket: Processing 2 catalog items...
D/WebSocket: --- processCatalogItems ---
D/WebSocket: Number of catalog items: 2
D/WebSocket: Items by type: {clients=2}
D/WebSocket: Saving to database via DataExchangeRepository...
D/WebSocket: SUCCESS: Catalog items saved to database
D/WebSocket: Sending ACK for catalog update
```

**Or if error:**
```
E/WebSocket: DATABASE SAVE ERROR (catalog): Cannot insert duplicate key
E/WebSocket: Exception: android.database.sqlite.SQLiteConstraintException...
```

**What this shows:**
- Catalog items being saved
- Breakdown by value_id type
- Database save success/failure
- Detailed error information

---

### 9. Processing Complete

```
D/WebSocket: === UNIFIED PAYLOAD PROCESSING COMPLETE ===
```

**Or if fatal error:**
```
E/WebSocket: FATAL ERROR in handleUnifiedPayload: NullPointerException
E/WebSocket: Exception: java.lang.NullPointerException at line 645...
```

---

## Common Error Patterns

### 1. JSON Parse Error
```
E/WSMessageFactory: JSON PARSE ERROR: Expected ':' at line 1 column 45
E/WSMessageFactory: Failed text: {"type":"data","message_id":"001"...
```

**Cause:** Invalid JSON syntax
**Solution:** Check backend JSON formatting

---

### 2. Missing value_id
```
W/WebSocket: Item 0: Missing value_id field
W/WebSocket: Item content: {"write":true,"read":false}
```

**Cause:** Item missing required `value_id` field
**Solution:** Add `value_id` to all array items

---

### 3. No Current Account
```
E/WebSocket: ERROR: No current account available
```

**Cause:** User not logged in or account not set
**Solution:** Ensure user has active account before sending data

---

### 4. Database Locked
```
E/WebSocket: DATABASE SAVE ERROR: database is locked
E/WebSocket: Exception: android.database.sqlite.SQLiteDatabaseLockedException
```

**Cause:** Database busy with another operation
**Solution:** Backend should retry after delay

---

### 5. Empty Payload
```
W/WebSocket: WARNING: Empty or null data array
```

**Cause:** Message payload is empty array `[]`
**Solution:** Don't send messages with empty data

---

## Full Example Log Flow

### Successful Options Update

```
D/WebSocket: === INCOMING WEBSOCKET MESSAGE ===
D/WebSocket: Payload length: 312 chars
D/WebSocket: Payload preview: {"type":"data","message_id":"opt-001"...
D/WSMessageFactory: Parsing message: {"type":"data","message_id":"opt-001"...
D/WSMessageFactory: Parse success - type: data, messageId: opt-001
D/WebSocket: Message type: data
D/WebSocket: Message ID: opt-001
D/WebSocket: Timestamp: 2025-11-30T20:00:00Z
D/WebSocket: Status: approved
D/WebSocket: --- DATA MESSAGE PROCESSING ---
D/WebSocket: Payload type: ARRAY
D/WebSocket: Array size: 1
D/WSMessageFactory: --- parseDataMessage ---
D/WSMessageFactory: SIMPLIFIED FORMAT detected: payload is JsonArray
D/WSMessageFactory: Array size: 1
D/WSMessageFactory: Created IncomingDataMessage with data_type: catalog
D/WebSocket: Parsed data_type: catalog
D/WebSocket: Routing to handleUnifiedPayload (simplified array format)
D/WebSocket: === HANDLING UNIFIED PAYLOAD ===
D/WebSocket: Message ID: opt-001
D/WebSocket: Data array size: 1
D/WebSocket: Processing 1 items...
D/WebSocket: Item 0: {"value_id":"options","write":true...
D/WebSocket: Item 0 value_id: options
D/WebSocket: Detected OPTIONS item
D/WebSocket: Item counts by value_id: {options=1}
D/WebSocket: Options items: 1
D/WebSocket: Catalog items: 0
D/WebSocket: Processing 1 options items...
D/WebSocket: --- processOptionsItems ---
D/WebSocket: Number of options items: 1
D/WebSocket: Current account: device-uuid-12345
D/WebSocket: Original options object: {"value_id":"options","write":true...
D/WebSocket: Removed value_id field
D/WebSocket: Options JSON length: 285 chars
D/WebSocket: Options JSON: {"write":true,"read":false,"loadImages":true...}
D/WebSocket: Saving account to database...
D/WebSocket: SUCCESS: Options saved to database
D/WebSocket: Sending ACK for options update
D/WebSocket: No catalog items to process
D/WebSocket: === UNIFIED PAYLOAD PROCESSING COMPLETE ===
```

---

## Troubleshooting Checklist

Use logs to verify each step:

- [ ] Message received (payload length shown)
- [ ] JSON parsing success (message type shown)
- [ ] Payload structure detected (ARRAY or OBJECT)
- [ ] Data type identified (catalog, settings, etc.)
- [ ] Routed to correct handler
- [ ] Items extracted from array
- [ ] value_id fields present on all items
- [ ] Items categorized (options vs catalog)
- [ ] Current account available
- [ ] Database save successful
- [ ] ACK sent back

If any step fails, the error will show where in this flow.

---

## Advanced Debugging

### Full Message Dump

To see the complete raw message:

```bash
adb logcat -v raw | grep "Payload preview:"
```

### Filter by Message ID

```bash
adb logcat | grep "msg-001"
```

### Only Show Errors

```bash
adb logcat -s WebSocket:E WSMessageFactory:E
```

### Performance Timing

Check timestamps to see processing duration:

```bash
adb logcat -v time -s WebSocket | grep "=== INCOMING\|=== COMPLETE"
```

---

## Log Tags

| Tag | Purpose |
|-----|---------|
| `WebSocket` | Main WebSocket operations |
| `WSMessageFactory` | Message parsing |

---

## Summary

With these logs, you can:
1. See exact payload received from backend
2. Track parsing through each step
3. Identify where errors occur
4. Verify data saved to database
5. Debug integration issues quickly

All errors include full stack traces and context for easy debugging.
