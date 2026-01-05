# 1C Integration Guide

This document describes how to integrate the 1C system with the Sphynx relay server for data exchange with Android mobile devices.

## Architecture Overview

```
┌─────────────┐         ┌─────────────────┐         ┌─────────────────┐
│             │  REST   │                 │WebSocket│                 │
│  1C System  │◄───────►│  Sphynx Relay   │◄───────►│  Android Device │
│             │  API    │     Server      │         │                 │
└─────────────┘         └─────────────────┘         └─────────────────┘
      │                         │
      │   POST /api/v1/push     │
      │   GET  /api/v1/pull     │
      └─────────────────────────┘
```

**Key Points:**
- 1C does NOT expose an HTTP service
- 1C acts as a REST API client, connecting to the Sphynx relay server
- Data is pushed to devices via `POST /api/v1/push`
- Data from devices is retrieved via `GET /api/v1/pull`
- All data formats remain consistent with the original HTTP service specification

---

## Connection to Relay Server

### Base URL

```
https://lic.nomadus.net
```

### Authentication

```
Authorization: Bearer {api_key}
```

The API key is provided when the license is created. See [API_1C_CONNECTION.md](API_1C_CONNECTION.md) for full API reference.

---

## Data Flow

### Sending Data to Device (1C → Device)

1. 1C composes data in the required format
2. 1C calls `POST /api/v1/push` with device UUID and data array
3. Relay server queues the message
4. When device connects via WebSocket, message is delivered
5. Device sends acknowledgment

### Receiving Data from Device (Device → 1C)

1. Device creates document (order, cash, etc.)
2. Device sends via WebSocket to relay server
3. Relay server stores in outgoing queue
4. 1C polls `GET /api/v1/pull` periodically
5. 1C processes received documents

---

## Push Endpoint

### `POST /api/v1/push`

Send data to a specific device.

**Request Fields:**

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| device_uuid | string | Yes | Target device UUID |
| message_uuid | string | No | For message replacement/deduplication |
| data | array | Yes | Array of data objects |

**Response Fields:**

| Field | Type | Description |
|-------|------|-------------|
| success | boolean | Operation result |
| data.queued | boolean | Message queued status |
| data.queue_id | string | MongoDB ObjectID of queued message |
| data.message_uuid | string | Message UUID (provided or auto-generated) |

---

## Pull Endpoint

### `GET /api/v1/pull?limit=50`

Retrieve data sent by devices.

**Response Fields:**

| Field | Type | Description |
|-------|------|-------------|
| success | boolean | Operation result |
| data.count | number | Number of items returned |
| data.items | array | Array of document objects |
| data.items[].device_uuid | string | Source device UUID |
| data.items[].data_type | string | Document type (order, cash, location, etc.) |
| data.items[].data | object | Document payload |
| data.items[].created_at | string | ISO 8601 timestamp |

---

## Data Formats

### Outgoing Data Types (1C → Device)

#### Settings/Options (CHECK response)

Push user settings to device.

| Field | Type | Description |
|-------|------|-------------|
| settings_type | string | Value: `"options"` |
| token | string | Session authentication token |
| read | boolean | Read permissions enabled |
| write | boolean | Write permissions enabled |
| differentialUpdates | boolean | Supports differential data sync |
| locations | boolean | GPS coordinate recording enabled |
| clientsProducts | boolean | Client product list usage |
| clientLocations | boolean | Load client coordinates |
| editLocations | boolean | Client coordinate editing allowed |
| clientsDirections | boolean | Additional client location list |
| lastLocationTime | number | Last received coordinate timestamp (seconds) |
| watchList | array | Observable device list with userID and name |
| currency | string | Display currency (e.g., "грн") |
| requireDeliveryDate | boolean | Mandatory delivery date field |
| checkOrderLocation | boolean | Validate location at order entry |
| license | string | License key |
| loadImages | boolean | Download product images |
| allowPriceTypeChoose | boolean | Allow price type selection |
| showClientPriceOnly | boolean | Display only client-specific prices |
| sendPushToken | boolean | Send push notification token |
| useDemands | boolean | Use "Need" flag in requests |
| usePackageMark | boolean | Use "Package" flag in requests |
| useCompanies | boolean | Multi-company operation |
| useStores | boolean | Multi-warehouse operation |

---

#### Catalog: Goods

| Field | Type | Description |
|-------|------|-------------|
| catalog_type | string | Value: `"goods"` |
| items | array | Array of product records |

**Item Fields:**

| Field | Type | Description |
|-------|------|-------------|
| value_id | string | Value: `"item"` |
| guid | string | Product identifier |
| description | string | Product name |
| code1 | string | User-visible code |
| code2 | string | 1C search code |
| sorting | number | Sort order |
| quantity | number | Stock quantity |
| is_group | number | Group flag (1/0) |
| group_guid | string | Parent product GUID |
| price | number | Sale price |
| min_price | number | Minimum sale price |
| base_price | number | Purchase cost |
| package_only | number | Package-only flag (1/0) |
| package_value | number | Items per package |
| weight | number | Weight in kg |
| unit | string | Unit of measure |

---

#### Catalog: Prices

| Field | Type | Description |
|-------|------|-------------|
| catalog_type | string | Value: `"prices"` |
| items | array | Array of price records |

**Item Fields:**

| Field | Type | Description |
|-------|------|-------------|
| value_id | string | Value: `"price"` |
| item_guid | string | Product identifier |
| price_type | number | Price type number |
| price_name | string | Price type name |
| price | number | Product price |

---

#### Catalog: Clients

| Field | Type | Description |
|-------|------|-------------|
| catalog_type | string | Value: `"clients"` |
| items | array | Array of client records |

**Item Fields:**

| Field | Type | Description |
|-------|------|-------------|
| value_id | string | Value: `"client"` |
| guid | string | Client identifier |
| code1 | string | Visible code |
| code2 | string | 1C search code |
| description | string | Client name |
| is_group | number | Group flag (1/0) |
| group_guid | string | Parent group GUID |
| phone | string | Phone number |
| address | string | Address |
| discount | number | Discount percentage |
| price_type | number | Price type number |
| sum | number | Account balance |

---

#### Catalog: Companies

| Field | Type | Description |
|-------|------|-------------|
| catalog_type | string | Value: `"companies"` |
| items | array | Array of company records |

**Item Fields:**

| Field | Type | Description |
|-------|------|-------------|
| value_id | string | Value: `"company"` |
| guid | string | Company identifier |
| description | string | Company name |
| is_default | number | Default flag (1/0) |

---

#### Catalog: Stores

| Field | Type | Description |
|-------|------|-------------|
| catalog_type | string | Value: `"stores"` |
| items | array | Array of store records |

**Item Fields:**

| Field | Type | Description |
|-------|------|-------------|
| value_id | string | Value: `"store"` |
| guid | string | Store identifier |
| description | string | Store name |
| is_default | number | Default flag (1/0) |

---

#### Catalog: Rests

| Field | Type | Description |
|-------|------|-------------|
| catalog_type | string | Value: `"rests"` |
| items | array | Array of stock records |

**Item Fields:**

| Field | Type | Description |
|-------|------|-------------|
| value_id | string | Value: `"rest"` |
| company_guid | string | Company identifier |
| store_guid | string | Store identifier |
| product_guid | string | Product identifier |
| quantity | number | Stock quantity |

---

#### Catalog: Debts

| Field | Type | Description |
|-------|------|-------------|
| catalog_type | string | Value: `"debts"` |
| items | array | Array of debt records |

**Item Fields:**

| Field | Type | Description |
|-------|------|-------------|
| value_id | string | Value: `"debt"` |
| client_guid | string | Client identifier |
| company_guid | string | Company identifier |
| doc_id | string | Document representation |
| doc_guid | string | Document identifier |
| doc_type | string | Document type |
| has_content | number | Content availability flag (0/1) |
| sum | number | Document sum |
| sorting | number | Display sort order |

---

#### Catalog: Clients Locations (coordinates)

| Field | Type | Description |
|-------|------|-------------|
| catalog_type | string | Value: `"clients_locations"` |
| items | array | Array of location records |

**Item Fields:**

| Field | Type | Description |
|-------|------|-------------|
| value_id | string | Value: `"client_location"` |
| client_guid | string | Client identifier |
| latitude | number | Latitude coordinate |
| longitude | number | Longitude coordinate |

---

#### Catalog: Clients Directions (addresses)

| Field | Type | Description |
|-------|------|-------------|
| catalog_type | string | Value: `"clients_directions"` |
| items | array | Array of direction records |

**Item Fields:**

| Field | Type | Description |
|-------|------|-------------|
| value_id | string | Value: `"client_direction"` |
| client_guid | string | Client identifier |
| direction_guid | string | Location list element identifier |
| description | string | Location name |
| is_group | number | Group flag (1/0) |
| area | string | Region/district name |
| city | string | City name |
| city_type | string | City type |
| notes | string | Notes |
| info | string | Additional information |

---

#### Catalog: Clients Goods

| Field | Type | Description |
|-------|------|-------------|
| catalog_type | string | Value: `"clients_goods"` |
| items | array | Array of client product records |

**Item Fields:**

| Field | Type | Description |
|-------|------|-------------|
| value_id | string | Value: `"client_goods_item"` |
| client_guid | string | Client identifier |
| item_guid | string | Product identifier |
| sort_order | number | Sort order |
| no_shipment | number | Days without shipment |
| shipment_date | string | Last shipment date |
| shipment_quantity | number | Last shipment quantity |

---

#### Catalog: Images

| Field | Type | Description |
|-------|------|-------------|
| catalog_type | string | Value: `"images"` |
| items | array | Array of image records |

**Item Fields:**

| Field | Type | Description |
|-------|------|-------------|
| value_id | string | Value: `"image"` |
| item_guid | string | Product identifier |
| image_guid | string | Image identifier |
| default | string | Default image flag ("1"/"0") |
| time | number | File timestamp |
| url | string | Image file URL |
| description | string | File description |
| type | string | File type |

---

### Incoming Data Types (Device → 1C)

Retrieved via `GET /api/v1/pull`. The `data_type` field indicates the document type.

#### Order - `data_type: "order"`

| Field | Type | Description |
|-------|------|-------------|
| guid | string | Document identifier |
| userID | string | Device identifier |
| server | string | Server address from settings |
| base | string | Database name from settings |
| number | string | Document number |
| date | string | Human-readable date-time |
| time | number | Timestamp (seconds) |
| status | string | Display status |
| company_guid | string | Company identifier |
| store_guid | string | Store identifier |
| delivery_date | string | Delivery date |
| client_id | string | Client code (code2) |
| client_description | string | Client name |
| client_guid | string | Client identifier |
| is_return | boolean | Return flag |
| is_processed | boolean | Posted flag (always true when sent) |
| is_sent | boolean | Sent flag |
| payment_type | string | "CASH" or "CREDIT" |
| notes | string | Notes |
| discount | number | Client discount |
| price_type | number | Price type number |
| price | number | Document sum |
| quantity | number | Total quantity |
| discount_value | number | Discount sum |
| next_payment | number | Promised payment sum |
| items | array | Array of line items |

**Order Line Item Fields:**

| Field | Type | Description |
|-------|------|-------------|
| lineNumber | number | Line number |
| code1 | string | Product code |
| code2 | string | Product code |
| item_guid | string | Product identifier |
| description | string | Product name |
| quantity | number | Quantity |
| price | number | Price |
| sum | number | Line sum |
| unit_code | string | Unit code |
| sum_discount | number | Line discount sum |
| is_packed | boolean | Packed flag |

---

#### Cash - `data_type: "cash"`

| Field | Type | Description |
|-------|------|-------------|
| guid | string | Document identifier |
| date | string | Human-readable date-time |
| time | number | Timestamp (seconds) |
| sum | number | Document sum |
| client_guid | string | Client identifier |
| client_description | string | Client name |
| parent_document | string | Parent document representation |
| notes | string | Notes |

---

#### Location history - `data_type: "location"`

| Field | Type | Description |
|-------|------|-------------|
| data | array | Array of coordinate records |

**Coordinate Record Fields:**

| Field | Type | Description |
|-------|------|-------------|
| time | number | Timestamp (milliseconds) |
| accuracy | number | Accuracy value |
| altitude | number | Altitude |
| bearing | number | Direction |
| latitude | number | Latitude |
| longitude | number | Longitude |
| speed | number | Speed |
| distance | number | Distance from previous point |
| provider | string | Provider name |
| point_name | string | Point name |

---

#### Clients Locations - `data_type: "clients_locations"`

| Field | Type | Description |
|-------|------|-------------|
| data | array | Array of coordinate records |

**Record Fields:**

| Field | Type | Description |
|-------|------|-------------|
| client_guid | string | Client identifier |
| client_id | string | Client code (code2) |
| latitude | number | Latitude |
| longitude | number | Longitude |

---

## 1C Implementation Requirements

### What 1C Needs to Implement

1. **REST Client Module** - HTTP client for calling relay server API
2. **Data Composer** - Functions to format catalog/order data as JSON
3. **Data Parser** - Functions to parse incoming documents from JSON
4. **Scheduled Jobs**:
   - Push catalog updates (on change or scheduled)
   - Pull device data (every 5-10 minutes recommended)
5. **Message Queue Handler** - Process incoming documents sequentially

### Processing Flow

```
┌─────────────────────────────────────────────────────────────┐
│                     1C Processing                           │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  ┌─────────────────┐                                        │
│  │ Catalog Changed │──► Compose JSON ──► POST /push         │
│  └─────────────────┘                                        │
│                                                             │
│  ┌─────────────────┐                                        │
│  │ Scheduled Timer │──► GET /pull ──► Parse JSON ──► Create │
│  │   (5-10 sec)    │                               Documents│
│  └─────────────────┘                                        │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

## Related Documentation

- [API_1C_CONNECTION.md](API_1C_CONNECTION.md) - Full REST API reference
- [API_DEVICE_CONNECTION.md](API_DEVICE_CONNECTION.md) - WebSocket API for devices
- [ADMIN_API.md](ADMIN_API.md) - Administration interface

---

## Changelog

| Version | Date | Changes |
|---------|------|---------|
| 2.1 | 2025-01-15 | Converted to table format with exact field specifications |
| 2.0 | 2025-01-15 | Rewritten for relay server architecture |
| 1.0 | 2025-01-15 | Initial HTTP service specification |
