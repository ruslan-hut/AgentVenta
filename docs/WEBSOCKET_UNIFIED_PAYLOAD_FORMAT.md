# WebSocket Unified Payload Format

## Overview

All WebSocket data messages now use a unified array-based payload format where each item is identified by a `value_id` field. This provides consistency between catalog data, options updates, and other data types.

## Unified Message Structure

```json
{
  "type": "data",
  "message_id": "unique-message-id",
  "timestamp": "2025-11-30T20:19:28.566Z",
  "status": "approved",
  "payload": {
    "data_type": "catalog|options|settings",
    "data": [
      {
        "value_id": "clients|goods|debts|options|...",
        // ... object-specific fields
      }
    ]
  }
}
```

## Key Principles

1. **`data` is always an array** - Even for single items
2. **`value_id` identifies object type** - Each item must have a `value_id` field
3. **Mixed types allowed** - Single message can contain multiple object types
4. **Backwards compatible** - Legacy single-object format still supported

---

## 1. Options Update

### Backend → Android

Send updated user options to update the `UserAccount.options` field.

```json
{
  "type": "data",
  "message_id": "692ca6d0fbcb25cda2220848",
  "timestamp": "2025-11-30T20:19:28.566Z",
  "status": "approved",
  "payload": {
    "data_type": "options",
    "data": [
      {
        "value_id": "options",
        "allowPriceTypeChoose": false,
        "showClientPriceOnly": false,
        "token": "",
        "editLocations": false,
        "setClientPrice": true,
        "useCompanies": false,
        "useStores": false,
        "fiscalProvider": "",
        "fiscalNumber": "",
        "fiscalDeviceId": "",
        "fiscalCashier": "",
        "write": false,
        "read": false,
        "allowReturn": true,
        "checkOrderLocation": false,
        "defaultClient": "",
        "locations": false,
        "loadImages": true,
        "license": "",
        "differentialUpdates": false,
        "requireDeliveryDate": false,
        "currency": "грн.",
        "clientsLocations": true,
        "clientsDirections": false,
        "clientsProducts": false,
        "useDemands": false,
        "usePackageMark": false,
        "lastLocationTime": 0,
        "printingEnabled": true,
        "sendPushToken": false,
        "watchList": ""
      }
    ]
  }
}
```

**Android Processing:**
1. Receives message with `data_type: "options"`
2. Finds item with `value_id: "options"` in data array
3. Removes `value_id` field
4. Converts remaining object to JSON string
5. Updates `UserAccount.options = json`
6. Saves to database
7. Sends ACK

---

## 2. Catalog Data (Unified Format)

### Backend → Android

Send catalog items (clients, products, debts, etc.) in a single message or mixed together.

#### Single Catalog Type

```json
{
  "type": "data",
  "message_id": "cat-001",
  "timestamp": "2025-11-30T20:20:00Z",
  "status": "approved",
  "payload": {
    "data_type": "catalog",
    "data": [
      {
        "value_id": "clients",
        "guid": "550e8400-e29b-41d4-a716-446655440000",
        "description": "ТОВ \"Продукти Україна\"",
        "code1": "00001234",
        "code2": "CLIENT-001",
        "phone": "+380501234567",
        "address": "вул. Хрещатик, 1, Київ",
        "discount": 5.5,
        "bonus": 100.0,
        "price_type": "wholesale",
        "is_active": 1,
        "is_group": 0
      },
      {
        "value_id": "clients",
        "guid": "client-002",
        "description": "Магазин \"Продукти\"",
        "code1": "00005678",
        "code2": "CLIENT-002",
        "phone": "+380509876543",
        "address": "вул. Шевченка, 10, Київ",
        "discount": 3.0,
        "bonus": 50.0,
        "price_type": "retail",
        "is_active": 1,
        "is_group": 0
      }
    ]
  }
}
```

#### Mixed Catalog Types

```json
{
  "type": "data",
  "message_id": "cat-mixed-001",
  "timestamp": "2025-11-30T20:25:00Z",
  "status": "approved",
  "payload": {
    "data_type": "catalog",
    "data": [
      {
        "value_id": "clients",
        "guid": "client-001",
        "description": "Client Name",
        "code1": "C001",
        "is_active": 1
      },
      {
        "value_id": "goods",
        "guid": "prod-001",
        "description": "Молоко 2.5% 1л",
        "code1": "P001",
        "barcode": "4820000001234",
        "price": 45.50,
        "is_active": 1
      },
      {
        "value_id": "debts",
        "company_guid": "comp-001",
        "client_guid": "client-001",
        "doc_guid": "doc-001",
        "doc_id": "ЗАМ-001",
        "doc_type": "order",
        "sum": 1250.75
      },
      {
        "value_id": "payment_types",
        "payment_type": "cash",
        "is_fiscal": 1,
        "is_default": 1,
        "description": "Готівка"
      }
    ]
  }
}
```

**Android Processing:**
1. Receives message with `data_type: "catalog"`
2. Extracts data array
3. For each item:
   - Checks for `batch_complete` sentinel (item with `type: "batch_complete"`) — see section 5
   - Converts to `XMap` (preserves all fields including `value_id` and `timestamp` from 1C)
   - Adds `databaseId` (current account GUID)
   - **Does NOT overwrite `timestamp`** — uses the UTC millisecond value embedded by 1C
4. Saves all items via `DataExchangeRepository.saveData()`
5. Sends ACK with count summary
6. If `batch_complete` was found, emits `batchComplete(timestamp)` to trigger cleanup

**Supported `value_id` values:**
- `clients`
- `goods`
- `debts`
- `payment_types`
- `companies`
- `stores`
- `rests`
- `clients_locations`
- `clients_directions`
- `clients_goods`
- `images`

---

## 3. Batch Complete (Full Sync Finish Signal)

### Backend → Android

After 1C finishes pushing all catalog data for a device, it calls `POST /api/v1/push/complete` with a UTC millisecond timestamp. The relay server queues a sentinel item that arrives as part of a regular data message.

**Timestamp Convention:**
- 1C generates a UTC millisecond timestamp **before** starting the batch
- 1C embeds this same timestamp in every data element it pushes (as the `timestamp` field)
- 1C sends the same timestamp in the `push/complete` call
- The app saves data items with the timestamp already in the data (does NOT overwrite it)
- On `batch_complete`, the app deletes all local catalog items where `timestamp < T`

```json
{
  "type": "data",
  "message_id": "507f1f77bcf86cd799439011",
  "timestamp": "2026-03-16T10:00:00Z",
  "status": "approved",
  "payload": [
    {
      "type": "batch_complete",
      "timestamp": 1710583200000
    }
  ]
}
```

**Android Processing:**
1. `handleUnifiedPayload()` detects item with `type: "batch_complete"`
2. Extracts `timestamp` (UTC milliseconds from 1C)
3. Sends ACK to relay server
4. Emits `batchComplete(timestamp)` via `WebSocketRepository.batchComplete` SharedFlow
5. `NetworkRepositoryImpl` observes `batchComplete` and calls `DataExchangeRepository.cleanUp(accountGuid, timestamp)`
6. Cleanup deletes all catalog rows where `timestamp < T` for the current account

**Constants:**
```java
public static final String VALUE_ID_BATCH_COMPLETE = "batch_complete";
```

---

## 4. Legacy Format Support

The system still supports the old single-object format for backwards compatibility:

```json
{
  "type": "data",
  "message_id": "legacy-001",
  "timestamp": "2025-11-30T20:30:00Z",
  "status": "approved",
  "payload": {
    "data_type": "settings",
    "data": {
      "user_email": "user@example.com",
      "device_uuid": "device-123",
      "options": {
        "write": true,
        "read": true
      }
    }
  }
}
```

This is automatically converted internally to:
```json
{
  "data": {
    "data": { /* original object */ }
  }
}
```

---

## 5. Response Messages

### ACK (Android → Backend)

```json
{
  "type": "ack",
  "message_id": "692ca6d0fbcb25cda2220848",
  "timestamp": "2025-11-30T20:19:29Z",
  "payload": {
    "status": "received"
  }
}
```

### Error (Android → Backend)

```json
{
  "type": "error",
  "message_id": "692ca6d0fbcb25cda2220848",
  "timestamp": "2025-11-30T20:19:29Z",
  "payload": {
    "data_type": "options",
    "error": "Failed to save: Database locked"
  }
}
```

---

## Implementation Details

### Constants (Constants.java)

```java
// Data types
public static final String WEBSOCKET_DATA_TYPE_CATALOG = "catalog";
public static final String WEBSOCKET_DATA_TYPE_OPTIONS = "options";
public static final String WEBSOCKET_DATA_TYPE_SETTINGS = "settings";

// Value IDs
public static final String VALUE_ID_OPTIONS = "options";
public static final String VALUE_ID_CLIENTS = "clients";
public static final String VALUE_ID_GOODS = "goods";
public static final String VALUE_ID_DEBTS = "debts";
public static final String VALUE_ID_PAYMENT_TYPES = "payment_types";
public static final String VALUE_ID_COMPANIES = "companies";
public static final String VALUE_ID_STORES = "stores";
public static final String VALUE_ID_RESTS = "rests";
public static final String VALUE_ID_CLIENTS_LOCATIONS = "clients_locations";
public static final String VALUE_ID_CLIENTS_DIRECTIONS = "clients_directions";
public static final String VALUE_ID_CLIENTS_GOODS = "clients_goods";
public static final String VALUE_ID_IMAGES = "images";
public static final String VALUE_ID_BATCH_COMPLETE = "batch_complete";
```

### Processing Flow

**WebSocketRepositoryImpl.kt — handleIncomingMessage:**
```kotlin
when (dataMessage.dataType) {
    Constants.WEBSOCKET_DATA_TYPE_CATALOG -> {
        // Unified format: routes by value_id, detects batch_complete
        handleUnifiedPayload(dataMessage)
    }
    else -> {
        // Legacy format or specific handlers
        sendAck(dataMessage.messageId)
        _incomingMessages.emit(dataMessage)
    }
}
```

**handleUnifiedPayload** processes each item:
- `type == "batch_complete"` → extract timestamp, emit `batchComplete`
- `value_id == "options"` → update UserAccount.options
- Other `value_id` values → save as catalog data via `DataExchangeRepository`

---

## Migration Guide

### For Backend Developers

**Old way (catalog):**
```json
{
  "payload": {
    "catalog_type": "clients",
    "data": [ {...}, {...} ]
  }
}
```

**New way (unified):**
```json
{
  "payload": {
    "data_type": "catalog",
    "data": [
      { "value_id": "clients", ... },
      { "value_id": "clients", ... }
    ]
  }
}
```

**Old way (options - NOT SUPPORTED):**
```json
{
  "payload": {
    "options": { ... }
  }
}
```

**New way (options):**
```json
{
  "payload": {
    "data_type": "options",
    "data": [
      { "value_id": "options", ... }
    ]
  }
}
```

### Benefits

1. **Consistency** - All data uses same structure
2. **Flexibility** - Can mix different data types in one message
3. **Extensibility** - Easy to add new data types
4. **Type Safety** - `value_id` provides explicit typing
5. **Batch Updates** - Send multiple updates efficiently

---

## Examples for Testing

### Test Options Update

```bash
# Send to WebSocket
{
  "type": "data",
  "message_id": "test-001",
  "timestamp": "2025-11-30T20:00:00Z",
  "status": "approved",
  "payload": {
    "data_type": "options",
    "data": [
      {
        "value_id": "options",
        "write": true,
        "read": true,
        "loadImages": true,
        "currency": "грн."
      }
    ]
  }
}
```

### Test Mixed Catalog

```bash
# Send to WebSocket
{
  "type": "data",
  "message_id": "test-002",
  "timestamp": "2025-11-30T20:00:00Z",
  "status": "approved",
  "payload": {
    "data_type": "catalog",
    "data": [
      {
        "value_id": "clients",
        "guid": "test-client-1",
        "description": "Test Client",
        "code1": "TC001",
        "is_active": 1,
        "is_group": 0
      },
      {
        "value_id": "goods",
        "guid": "test-product-1",
        "description": "Test Product",
        "code1": "TP001",
        "price": 100.00,
        "is_active": 1,
        "is_group": 0
      }
    ]
  }
}
```

---

## Error Handling

### Invalid value_id
- Android logs warning and skips the item
- Continues processing other items
- Sends ACK with count of successfully processed items

### Missing value_id
- Item treated as invalid
- Logged and skipped
- Other items processed normally

### Database save failure
- Android sends error message back
- Includes specific error in payload
- Backend should retry or notify user

---

## Performance Considerations

1. **Batch Size**: Server chunks at 1000 items max per WebSocket frame
2. **Mixed Types**: Grouping same types together is more efficient for the app's separator logic
3. **Acknowledgment**: Wait for ACK before sending next batch
4. **Timestamps**: 1C generates a UTC millisecond timestamp and embeds it in every data element. The app preserves this value — does not overwrite it. The same timestamp is sent in `batch_complete` for cleanup.
5. **Message IDs**: Must be unique for tracking

---

## Status: ✅ Implemented

- [x] Constants defined
- [x] WebSocketMessageFactory supports array payloads
- [x] handleOptionsUpdate implemented
- [x] handleUnifiedCatalogUpdate implemented
- [x] UserAccountRepository integration
- [x] Backwards compatibility maintained
- [ ] Integration tests needed
