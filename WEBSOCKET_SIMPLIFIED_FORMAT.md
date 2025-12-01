# WebSocket Simplified Payload Format

## Overview

**SIMPLIFIED**: The `payload` is always a direct array of objects. Each object is identified by its `value_id` field. No wrapper, no `data_type` field - just a simple array.

## Message Structure

```json
{
  "type": "data",
  "message_id": "unique-message-id",
  "timestamp": "2025-11-30T20:19:28.566Z",
  "status": "approved",
  "payload": [
    {
      "value_id": "options|clients|goods|...",
      // ... object fields
    }
  ]
}
```

## Key Principles

1. ✅ **`payload` is always an array** - Direct array, no wrapper object
2. ✅ **Each item has `value_id`** - Identifies the object type
3. ✅ **Mixed types allowed** - Single message can contain different types
4. ✅ **Simple routing** - Android routes items based on `value_id`

---

## Examples

### 1. Options Update

Send user options to update the `UserAccount.options` field.

```json
{
  "type": "data",
  "message_id": "692ca6d0fbcb25cda2220848",
  "timestamp": "2025-11-30T20:19:28.566Z",
  "status": "approved",
  "payload": [
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
```

**Android Processing:**
1. Receives array payload
2. Finds item with `value_id: "options"`
3. Removes `value_id` field
4. Saves remaining object to `UserAccount.options`
5. Sends ACK

---

### 2. Catalog Data - Single Type

Send clients catalog data.

```json
{
  "type": "data",
  "message_id": "cat-clients-001",
  "timestamp": "2025-11-30T20:20:00Z",
  "status": "approved",
  "payload": [
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
```

---

### 3. Mixed Catalog Types

Send multiple catalog types in one message.

```json
{
  "type": "data",
  "message_id": "cat-mixed-001",
  "timestamp": "2025-11-30T20:25:00Z",
  "status": "approved",
  "payload": [
    {
      "value_id": "clients",
      "guid": "client-001",
      "description": "Клієнт 1",
      "code1": "C001",
      "is_active": 1,
      "is_group": 0
    },
    {
      "value_id": "goods",
      "guid": "prod-001",
      "description": "Молоко 2.5% 1л",
      "code1": "P001",
      "barcode": "4820000001234",
      "price": 45.50,
      "is_active": 1,
      "is_group": 0
    },
    {
      "value_id": "debts",
      "company_guid": "comp-001",
      "client_guid": "client-001",
      "doc_guid": "doc-001",
      "doc_id": "ЗАМ-001",
      "doc_type": "order",
      "sum": 1250.75,
      "sum_in": 500.00,
      "sum_out": 1750.75
    },
    {
      "value_id": "payment_types",
      "payment_type": "cash",
      "is_fiscal": 1,
      "is_default": 1,
      "description": "Готівка"
    },
    {
      "value_id": "companies",
      "guid": "comp-001",
      "description": "ТОВ \"АгентВента\"",
      "is_default": 1
    },
    {
      "value_id": "stores",
      "guid": "store-main",
      "description": "Основний склад",
      "is_default": 1
    },
    {
      "value_id": "rests",
      "company_guid": "comp-001",
      "store_guid": "store-main",
      "product_guid": "prod-001",
      "quantity": 150.0
    }
  ]
}
```

**Android Processing:**
1. Receives array payload
2. Separates items by `value_id`:
   - `"options"` → Updates UserAccount
   - Everything else → Saves to catalog tables
3. Converts catalog items to XMap
4. Saves to database
5. Sends single ACK for entire message

---

### 4. Options + Catalog Combined

Send options and catalog data together.

```json
{
  "type": "data",
  "message_id": "combined-001",
  "timestamp": "2025-11-30T20:30:00Z",
  "status": "approved",
  "payload": [
    {
      "value_id": "options",
      "write": true,
      "read": true,
      "loadImages": true,
      "currency": "грн."
    },
    {
      "value_id": "clients",
      "guid": "client-001",
      "description": "Test Client",
      "code1": "TC001",
      "is_active": 1
    },
    {
      "value_id": "goods",
      "guid": "prod-001",
      "description": "Test Product",
      "code1": "TP001",
      "price": 100.00,
      "is_active": 1
    }
  ]
}
```

---

## Supported value_id Types

| value_id | Description | Saved To |
|----------|-------------|----------|
| `options` | User options | `UserAccount.options` |
| `clients` | Client catalog | `clients` table |
| `goods` | Product catalog | `products` table |
| `debts` | Client debts | `debts` table |
| `payment_types` | Payment methods | `payment_types` table |
| `companies` | Companies | `companies` table |
| `stores` | Warehouses | `stores` table |
| `rests` | Stock levels | `rests` table |
| `clients_locations` | Client GPS | `client_locations` table |
| `clients_directions` | Client routes | (future) |
| `clients_goods` | Client products | (future) |
| `images` | Images | `product_images` table |

---

## Response Messages

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

## Processing Flow

### WebSocketRepositoryImpl

```kotlin
// 1. Parse message
val message = WebSocketMessageFactory.parseMessage(text)

// 2. Check if payload is array (new format)
if (message.payload.isJsonArray) {
    // Simplified format detected
    handleUnifiedPayload(message)
}

// 3. Process array items
for (item in payloadArray) {
    val valueId = item.get("value_id")

    when (valueId) {
        "options" -> processOptionsItems()
        else -> processCatalogItems()
    }
}

// 4. Send ACK
sendAck(messageId, "unified", totalCount)
```

---

## Comparison: Old vs New

### Old Format (Multiple Messages)

```json
// Message 1: Options
{
  "payload": {
    "data_type": "options",
    "data": [{ "value_id": "options", ... }]
  }
}

// Message 2: Clients
{
  "payload": {
    "data_type": "catalog",
    "data": [{ "value_id": "clients", ... }]
  }
}

// Message 3: Products
{
  "payload": {
    "data_type": "catalog",
    "data": [{ "value_id": "goods", ... }]
  }
}
```

### New Format (Single Message)

```json
{
  "payload": [
    { "value_id": "options", ... },
    { "value_id": "clients", ... },
    { "value_id": "goods", ... }
  ]
}
```

**Benefits:**
- ✅ **Simpler** - No nested structure
- ✅ **Fewer messages** - Batch updates in one message
- ✅ **Consistent** - Same structure for everything
- ✅ **Efficient** - Reduced WebSocket overhead

---

## Error Handling

### Missing value_id
- Android logs warning
- Skips the item
- Continues processing others
- Sends ACK with count of valid items

### Invalid value_id
- Treated as catalog item
- Saved to database (XMap preserves value_id)
- Database entity builder will handle based on value_id

### Database save failure
- Android sends error message
- Includes specific error details
- Backend should retry or notify

---

## Testing

### Test Command (Send via WebSocket)

```json
{
  "type": "data",
  "message_id": "test-001",
  "timestamp": "2025-11-30T20:00:00Z",
  "status": "approved",
  "payload": [
    {
      "value_id": "options",
      "write": true,
      "read": true,
      "currency": "грн."
    },
    {
      "value_id": "clients",
      "guid": "test-client",
      "description": "Test Client",
      "code1": "TC001",
      "is_active": 1,
      "is_group": 0
    }
  ]
}
```

### Expected Android Response

```json
{
  "type": "ack",
  "message_id": "test-001",
  "timestamp": "2025-11-30T20:00:01Z",
  "payload": {
    "status": "received"
  }
}
```

### Verify in Android Logs

```
WebSocket: Data received: catalog (status: approved)
WebSocket: Updating options for account xxx: {"write":true,"read":true...
WebSocket: Options saved to database successfully (45 chars)
WebSocket: Saved 1 catalog items: {clients=1}
WebSocket: Sent ACK for catalog (1 items)
```

---

## Implementation Status

✅ **COMPLETED**

- [x] WebSocketMessageFactory detects array payload
- [x] handleUnifiedPayload routes by value_id
- [x] processOptionsItems updates UserAccount
- [x] processCatalogItems saves to database
- [x] Single ACK for entire message
- [x] Error handling and logging
- [x] Backwards compatibility maintained

---

## Migration Guide

**For Backend Developers:**

**Before:**
```json
{
  "payload": {
    "data_type": "options",
    "data": [{ "value_id": "options", ... }]
  }
}
```

**After:**
```json
{
  "payload": [
    { "value_id": "options", ... }
  ]
}
```

**That's it!** Just send the array directly as payload.
