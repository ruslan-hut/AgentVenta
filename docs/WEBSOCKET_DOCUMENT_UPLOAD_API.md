# WebSocket Document Upload API Specification

This document describes the WebSocket message protocol for uploading documents from the Android app to the backend server.

## Connection

WebSocket URL: `wss://lic.nomadus.net/ws/device?uuid={device_guid}`

## Message Format

All messages follow this envelope structure:

```json
{
  "type": "message_type",
  "message_id": "unique-id",
  "timestamp": "2025-01-15T10:30:00Z",
  "payload": { /* message-specific data */ }
}
```

### Fields

| Field | Type | Description |
|-------|------|-------------|
| `type` | string | Message type identifier |
| `message_id` | string | Unique message ID (format: `{timestamp}-{random}`, e.g., `1766783206439-5926`) |
| `timestamp` | string | ISO 8601 UTC timestamp |
| `payload` | object | Message-specific payload |

---

## Document Upload Messages

The Android app sends the following document types. **The backend must implement handlers for each.**

### 1. Upload Order (`upload_order`)

**Message sent by Android:**

```json
{
  "type": "upload_order",
  "message_id": "1766783206439-5926",
  "timestamp": "2025-12-26T10:30:00Z",
  "payload": {
    "order": {
      "type": "order",
      "userID": "device-uuid-here",
      "date": "2025-12-26",
      "time": 1766783206000,
      "time_saved": 1766783200000,
      "number": 3,
      "guid": "0dedc01e-ee57-41a9-a217-4b1cb424315a",
      "delivery_date": "2025-12-27",
      "notes": "Order notes",
      "company_guid": "company-uuid",
      "company": "Company Name",
      "store_guid": "store-uuid",
      "store": "Store Name",
      "client_guid": "client-uuid",
      "client_id": "C001",
      "client_description": "Client Name",
      "discount": 0.0,
      "price_type": "retail",
      "payment_type": "cash",
      "is_fiscal": 0,
      "status": "",
      "price": 1500.00,
      "quantity": 10.0,
      "weight": 5.5,
      "discount_value": 0.0,
      "next_payment": 0.0,
      "latitude": 50.4501,
      "longitude": 30.5234,
      "distance": 150,
      "location_time": 1766783100000,
      "rest_type": "",
      "is_return": 0,
      "is_processed": 1,
      "is_sent": 0,
      "items": [
        {
          "order_guid": "0dedc01e-ee57-41a9-a217-4b1cb424315a",
          "item_guid": "product-uuid-1",
          "code1": "P001",
          "code2": "P001",
          "description": "Product Name",
          "unit_code": "pcs",
          "quantity": 5.0,
          "weight": 2.5,
          "price": 100.00,
          "sum": 500.00,
          "sum_discount": 0.0,
          "is_demand": false,
          "is_packed": false
        }
      ]
    },
    "content": [
      {
        "order_guid": "0dedc01e-ee57-41a9-a217-4b1cb424315a",
        "item_guid": "product-uuid-1",
        "code1": "P001",
        "code2": "P001",
        "description": "Product Name",
        "unit_code": "pcs",
        "quantity": 5.0,
        "weight": 2.5,
        "price": 100.00,
        "sum": 500.00,
        "sum_discount": 0.0,
        "is_demand": false,
        "is_packed": false
      }
    ],
    "document_guid": "0dedc01e-ee57-41a9-a217-4b1cb424315a"
  }
}
```

---

### 2. Upload Cash Receipt (`upload_cash`)

**Message sent by Android:**

```json
{
  "type": "upload_cash",
  "message_id": "1766783300000-1234",
  "timestamp": "2025-12-26T10:35:00Z",
  "payload": {
    "cash": {
      "type": "cash",
      "userID": "device-uuid-here",
      "date": "2025-12-26",
      "time": 1766783300000,
      "number": 1,
      "guid": "cash-uuid-here",
      "company_guid": "company-uuid",
      "company": "Company Name",
      "client_guid": "client-uuid",
      "client": "Client Name",
      "reference_guid": "order-uuid-if-linked",
      "sum": 500.00,
      "notes": "Payment for order",
      "fiscal_number": 0,
      "is_fiscal": 0
    },
    "document_guid": "cash-uuid-here"
  }
}
```

---

### 3. Upload Client Image (`upload_image`)

**Message sent by Android:**

```json
{
  "type": "upload_image",
  "message_id": "1766783400000-5678",
  "timestamp": "2025-12-26T10:40:00Z",
  "payload": {
    "image": {
      "db_guid": "device-uuid-here",
      "client_guid": "client-uuid",
      "image_guid": "image-uuid-here",
      "url": "base64-encoded-image-data-or-url",
      "description": "Client photo",
      "is_default": 0,
      "timestamp": 1766783400000,
      "type": "client_image"
    },
    "document_guid": "image-uuid-here"
  }
}
```

---

### 4. Upload Client Location (`upload_location`)

**Message sent by Android:**

```json
{
  "type": "upload_location",
  "message_id": "1766783500000-9012",
  "timestamp": "2025-12-26T10:45:00Z",
  "payload": {
    "location": {
      "db_guid": "device-uuid-here",
      "client_guid": "client-uuid",
      "latitude": 50.4501,
      "longitude": 30.5234,
      "address": "123 Main Street, Kyiv",
      "type": "client_location"
    },
    "document_guid": "client-uuid"
  }
}
```

---

## Required Backend Response

For each uploaded document, the backend **MUST** respond with an ACK message containing the **same `message_id`**:

### ACK Response Format

```json
{
  "type": "ack",
  "message_id": "1766783206439-5926",
  "timestamp": "2025-12-26T10:30:01Z",
  "status": "approved",
  "payload": {
    "status": "received"
  }
}
```

### Critical Requirements

1. **`message_id` MUST match** the original message's `message_id`
2. **`type` MUST be `"ack"`**
3. Without the ACK, the document remains `is_sent=0` and will be re-uploaded on next sync

---

## Error Response Format

If processing fails, send an error message:

```json
{
  "type": "error",
  "message_id": "1766783206439-5926",
  "timestamp": "2025-12-26T10:30:01Z",
  "status": "error",
  "payload": {
    "error": "Error description",
    "reason": "optional detailed reason"
  }
}
```

---

## Backend Implementation Checklist

The backend WebSocket handler must implement:

- [ ] Handle `upload_order` message type
  - Parse order header and items from payload
  - Forward to 1C system
  - Send ACK with matching `message_id`

- [ ] Handle `upload_cash` message type
  - Parse cash receipt from payload
  - Forward to 1C system
  - Send ACK with matching `message_id`

- [ ] Handle `upload_image` message type
  - Parse image data from payload
  - Store image or forward to 1C
  - Send ACK with matching `message_id`

- [ ] Handle `upload_location` message type
  - Parse location data from payload
  - Update client location in 1C
  - Send ACK with matching `message_id`

---

## Flow Diagram

```
Android App                         Backend Server                    1C System
    |                                    |                                |
    |-- upload_order (msg_id: X) ------->|                                |
    |                                    |-- Forward order data --------->|
    |                                    |<-- Confirmation ---------------|
    |<-- ack (msg_id: X) ----------------|                                |
    |                                    |                                |
    | [Mark order as is_sent=1]          |                                |
```

---

## Current Issue

The backend is responding with:
```json
{
  "type": "error",
  "status": "approved",
  "payload": {
    "error": "Unknown message type: upload_order"
  }
}
```

This indicates the backend WebSocket handler doesn't have a case for the `upload_order` message type. The handler needs to be extended to recognize and process these document upload message types.

---

## Message Type Constants

| Constant | Value | Direction | Description |
|----------|-------|-----------|-------------|
| `upload_order` | `"upload_order"` | App → Server | Upload sales order |
| `upload_cash` | `"upload_cash"` | App → Server | Upload cash receipt |
| `upload_image` | `"upload_image"` | App → Server | Upload client image |
| `upload_location` | `"upload_location"` | App → Server | Upload client GPS location |
| `ack` | `"ack"` | Server → App | Acknowledge receipt |
| `error` | `"error"` | Server → App | Error response |
