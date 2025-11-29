# Android WebSocket Implementation Plan - Corrections Summary

## Critical Architecture Fix Applied

### Original (Incorrect) Approach:
- Planned to create a separate `DeviceUuidManager` class
- Would generate one UUID per physical device
- Store in SharedPreferences
- Add `device_uuid` field to `UserAccount` entity
- All accounts would share same device UUID

### Corrected Approach:
- **Use existing `UserAccount.guid`** as the device UUID
- Each account has its own unique identifier
- No additional UUID management needed
- Aligns with existing multi-account architecture

---

## Why This Correction Was Necessary

### Multi-Account Architecture Requirements:
The app is designed to support **multiple independent connections**:

1. **Each UserAccount** = One connection to a 1C database
2. **`db_guid` field** in all entities separates data per account
3. **Composite primary keys** like `["guid", "db_guid"]` in catalog tables
4. **Only one current account** active at a time

### WebSocket Server Expectations:
- Server tracks devices by **unique UUID per connection**
- Each device UUID has its own message queue
- Different accounts should appear as **different devices** to the server
- This enables:
  - Separate message queues per account
  - Independent license management
  - Proper data routing

### Incorrect Approach Would Cause:
1. **Server confusion**: All accounts from same device appear as one
2. **Message routing errors**: Can't distinguish which account gets which message
3. **Queue conflicts**: Messages for different accounts mixed together
4. **License issues**: Server can't properly track device limits per license

---

## Changes Made to Implementation Plan

### Removed Steps:
- ~~Step 1.5: Generate Device UUID~~ (DeviceUuidManager class)
- ~~Step 1.6: Add Device UUID to Hilt Module~~

### Modified Steps:

#### Database Schema (Step 1.2):
**Before**:
```kotlin
@ColumnInfo(name = "relay_server") val relayServer: String = "",
@ColumnInfo(name = "device_uuid") val deviceUuid: String = ""  // REMOVED
```

**After**:
```kotlin
@ColumnInfo(name = "relay_server") val relayServer: String = ""
// guid field already exists and serves as device UUID
```

#### Migration (Step 1.3):
**Before**: Add 2 fields (relay_server, device_uuid)
**After**: Add 1 field (relay_server only)

#### Extension Functions (Step 1.4):
**Before**:
```kotlin
fun UserAccount.getWebSocketUrl(): String {
    return "$host/ws/device?uuid=$deviceUuid&license=$license"
}
```

**After**:
```kotlin
// NOTE: License is NOT sent - backend links device UUIDs to licenses server-side
fun UserAccount.getWebSocketUrl(): String {
    return "$host/ws/device?uuid=$guid"
    // Use existing guid as device UUID
    // License number is NOT used for authorization - only for display
}
```

#### UI Changes (Step 3.2):
**Before**: Device UUID field with "Generate" button
**After**: Read-only Account GUID display field

#### ViewModel (Step 3.1):
**Before**: `deviceUuid` LiveData + generate/load methods
**After**: Just `relayServer` LiveData (guid already managed)

---

## Architecture Diagram (Corrected)

```
┌─────────────────────────────────────────────┐
│        Physical Android Device              │
├─────────────────────────────────────────────┤
│                                             │
│  UserAccount Table:                         │
│  ┌────────────────────────────────────┐    │
│  │ guid: "aaa-111" (Account A)        │    │
│  │ license: "license-key-1"           │    │
│  │ relay_server: "relay.example.com"  │    │
│  │ is_current: 1                      │    │
│  └────────────────────────────────────┘    │
│             ↓                               │
│  WebSocket Connection:                      │
│  wss://relay.example.com/ws/device?         │
│      uuid=aaa-111&license=license-key-1     │
│             ↓                               │
│  Server sees: device_uuid = "aaa-111"       │
│                                             │
│  ┌────────────────────────────────────┐    │
│  │ guid: "bbb-222" (Account B)        │    │
│  │ license: "license-key-2"           │    │
│  │ relay_server: "relay.example.com"  │    │
│  │ is_current: 0                      │    │
│  └────────────────────────────────────┘    │
│             ↓                               │
│  (When switched to current)                 │
│  wss://relay.example.com/ws/device?         │
│      uuid=bbb-222&license=license-key-2     │
│             ↓                               │
│  Server sees: device_uuid = "bbb-222"       │
│                                             │
└─────────────────────────────────────────────┘

Local Database Data Separation:
┌──────────────────────────────────────┐
│ Orders Table:                        │
│  - Order 1 (db_guid="aaa-111")      │
│  - Order 2 (db_guid="aaa-111")      │
│  - Order 3 (db_guid="bbb-222")      │
│                                      │
│ Clients Table:                       │
│  - Client A (guid="c1", db_guid="aaa-111") │
│  - Client B (guid="c2", db_guid="bbb-222") │
└──────────────────────────────────────┘
```

---

## Server-Side Implications

### MongoDB Collections Mapping:

**devices collection**:
```javascript
{
  uuid: "aaa-111",        // From UserAccount A
  license_id: ObjectId("license-1"),
  connection_state: "online",
  db_guid: "aaa-111"      // Same as uuid
}

{
  uuid: "bbb-222",        // From UserAccount B
  license_id: ObjectId("license-2"),
  connection_state: "offline",
  db_guid: "bbb-222"      // Same as uuid
}
```

**queue collection** (incoming messages):
```javascript
{
  device_uuid: "aaa-111",  // Routes to Account A
  data_type: "order",
  data: { /* order for Account A */ }
}

{
  device_uuid: "bbb-222",  // Routes to Account B
  data_type: "catalog",
  data: { /* catalog for Account B */ }
}
```

**outgoing_queue collection** (device → accounting):
```javascript
{
  device_uuid: "aaa-111",
  data_type: "order",
  data: {
    db_guid: "aaa-111",   // Matches device UUID
    /* order data from Account A */
  }
}
```

---

## Benefits of Corrected Approach

### 1. Architectural Consistency
✅ Aligns with existing multi-account pattern
✅ Reuses established `db_guid` concept
✅ No new UUID management logic needed

### 2. Simplified Implementation
✅ ~300 fewer lines of code
✅ No SharedPreferences management
✅ No DeviceUuidManager class
✅ Fewer UI components

### 3. Server-Side Clarity
✅ Each connection clearly mapped to an account
✅ Message routing unambiguous
✅ License enforcement per connection
✅ Independent device limits per account

### 4. Data Integrity
✅ `db_guid` in entities matches WebSocket device UUID
✅ No mismatch between local and remote identifiers
✅ Easier debugging and troubleshooting

### 5. User Experience
✅ Natural account switching (disconnect old, connect new)
✅ Clear separation of different business connections
✅ Each 1C database connection treated independently

---

## Migration Path (Updated)

### Database Version: 20 → 21
**Single Migration**:
```sql
ALTER TABLE user_accounts ADD COLUMN relay_server TEXT NOT NULL DEFAULT ''
```

No device_uuid column needed!

---

## Testing Implications

### New Test: Multi-Account WebSocket (Test 5.10)
```
Scenario: Two accounts on same device
├── Create Account A with guid "aaa-111"
├── Create Account B with guid "bbb-222"
├── Set Account A as current
├── Connect WebSocket → Server sees device "aaa-111"
├── Send test message → Routes to Account A queue
├── Switch to Account B
├── Disconnect and reconnect → Server sees device "bbb-222"
└── Send test message → Routes to Account B queue
```

### Verification Points:
- [ ] Server logs show two distinct device UUIDs
- [ ] MongoDB has two device records
- [ ] Message queues are separate
- [ ] Local database has separate data (different db_guid)

---

## Code Impact Comparison

### Original Plan:
- New files: 8
- Modified files: 10
- New code: ~1500 lines
- Migration: 2 columns

### Corrected Plan:
- New files: 7 (-1 DeviceUuidManager)
- Modified files: 9 (-1 GlobalModule)
- New code: ~1200 lines (-300 lines)
- Migration: 1 column (-1 device_uuid)

**Reduction**: 20% less code, simpler architecture!

---

## Conclusion

The corrected implementation:
1. ✅ Properly supports multi-account architecture
2. ✅ Aligns with existing database patterns
3. ✅ Simplifies code and reduces complexity
4. ✅ Provides clear server-side device separation
5. ✅ Maintains data integrity across local and remote systems

**Key Takeaway**: Always leverage existing architectural patterns before adding new abstractions!
