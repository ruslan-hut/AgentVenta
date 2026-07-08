# Data Management API

This document describes the **data exchange** part of the Sphynx relay API: how the 1C
accounting system pushes catalog data to devices and pulls documents created on
devices. For device/license lifecycle (registration, approval, settings) see
[API_DEVICE_MANAGEMENT.md](API_DEVICE_MANAGEMENT.md).

## Architecture Overview

```
┌─────────────┐         ┌─────────────────┐         ┌─────────────────┐
│             │  REST   │                 │  REST   │                 │
│  1C System  │◄───────►│  Sphynx Relay   │◄───────►│  Android Device │
│             │  API    │     Server      │         │                 │
└─────────────┘         └─────────────────┘         └─────────────────┘
      │                         │
      │   POST /api/v1/push     │
      │   GET  /api/v1/pull     │
      └─────────────────────────┘
```

**Key Points:**
- Both 1C and the device act as REST clients of the relay server — neither exposes an HTTP service.
- The relay stores messages in per-device queues; delivery is store-and-forward, not real-time.
- Data is pushed to devices via `POST /api/v1/push`.
- Data from devices is retrieved via `GET /api/v1/pull`.

> **Transport note.** WebSocket transport is **deprecated** and removed from the app.
> All device ↔ relay traffic now uses the REST device API (the device polls
> `GET /api/v1/device/pull` and posts to `/api/v1/device/upload`). From the 1C side
> nothing changes — the `push`/`pull` contract below is unchanged. Legacy WebSocket
> specs are kept under [archive/](archive/).

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

The API key is issued together with the license. It identifies the license (and
therefore the target 1C base) on the relay server. See
[API_DEVICE_MANAGEMENT.md](API_DEVICE_MANAGEMENT.md) for the device/license endpoints
that share the same authentication.

---

## Data Flow

### Sending Data to Device (1C → Device)

1. 1C composes data in the required format
2. 1C calls `POST /api/v1/push` with device UUID and data array
3. Relay server queues the message in the device's incoming queue
4. Device polls `GET /api/v1/device/pull` and receives the message
5. Device acknowledges delivery via `POST /api/v1/device/ack`

### Receiving Data from Device (Device → 1C)

1. Device creates document (order, cash, etc.)
2. Device uploads it via `POST /api/v1/device/upload` to the relay server
3. Relay server stores it in the outgoing queue
4. 1C polls `GET /api/v1/pull` periodically
5. 1C processes received documents

---

## Sync Modes

The mobile app supports two sync modes: **differential** (incremental) and **full** (complete replacement). Both modes use the same `POST /api/v1/push` endpoint with the same data format — the difference is in **what** 1C sends and **how** the app handles it.

### Differential Sync (default)

1C sends only changed data since the last sync. The app upserts received items into the local database. No items are deleted — only new or updated records are applied.

**Use case:** Regular periodic sync (every few minutes).

### Full Sync (complete catalog replacement)

1C sends **all** catalog data for the device. The timestamp-based mechanism ensures stale items are removed:

1. 1C generates a UTC millisecond timestamp `T` before starting the batch
2. 1C embeds `T` as the `timestamp` field in every data element it sends
3. The app saves each item with the `timestamp` value from the data (does not overwrite it)
4. When 1C finishes, it calls `POST /api/v1/push/complete` with the same `T`
5. The app receives the `batch_complete` sentinel and deletes all catalog items where `timestamp < T`

This means any item that was **not** included in the full sync batch gets automatically removed from the device.

**Use case:** Initial setup, periodic full refresh, data corrections.

### What 1C Must Do for Full Sync

1. Generate a UTC millisecond timestamp (`T`) before starting the batch
2. Include `"timestamp": T` in every data element sent via `POST /api/v1/push`
3. Send **all** active catalog records for the device in one or more `POST /api/v1/push` calls
4. If all pushes succeed, call `POST /api/v1/push/complete` with `{"device_uuid": "...", "timestamp": T}`

The critical requirement is **completeness** — every item that should remain on the device must be included. The `push/complete` call tells the device the batch is finished so it can delete items not refreshed in this sync.

**Required data types to send:**

| Data type | value_id | Required | Condition |
|-----------|----------|----------|-----------|
| Options | `options` | Yes | Always — controls device permissions and features |
| Clients | `client` | Yes | All active clients for the agent |
| Products | `item` | Yes | All active products |
| Prices | `price` | Yes | All price records for available price types |
| Debts | `debt` | Yes | All current debt documents |
| Payment types | `payment_type` | Yes | All available payment types |
| Companies | `company` | Conditional | When `useCompanies: true` in options |
| Stores | `store` | Conditional | When `useStores: true` in options |
| Stock levels | `rest` | Conditional | When `useStores: true` in options |
| Client locations | `client_location` | Conditional | When `clientsLocations: true` in options |
| Client directions | `client_direction` | Conditional | When `clientsDirections: true` in options |
| Client products | `client_goods_item` | Conditional | When `clientsProducts: true` in options |
| Product images | `image` | Conditional | When `loadImages: true` in options |
| Discounts | `discount` | Conditional | When `complexDiscounts: true` in options |

**Key parameters in options object:**

| Parameter | Type | Effect |
|-----------|------|--------|
| `write` | boolean | Device can create/send documents |
| `read` | boolean | Device can receive catalog data |
| `useCompanies` | boolean | Send company data |
| `useStores` | boolean | Send store and stock level data |
| `clientsLocations` | boolean | Send client GPS coordinates |
| `clientsDirections` | boolean | Send client address lists |
| `clientsProducts` | boolean | Send per-client product lists |
| `loadImages` | boolean | Send product images |
| `complexDiscounts` | boolean | Send per-client per-product discount rules (when false, only `Client.discount` is used) |

**Example: full sync push sequence**

```
0. T = current UTC time in milliseconds (e.g. 1710583200000)
1. POST /push → options + clients (each item has "timestamp": T)
2. POST /push → products + prices (each item has "timestamp": T)
3. POST /push → debts + payment_types + companies + stores + rests (each item has "timestamp": T)
4. POST /push → discounts (each item has "timestamp": T) — only if complexDiscounts: true
5. POST /push/complete → { "device_uuid": "...", "timestamp": T }
```

Splitting into multiple push calls is fine — 1C embeds the same timestamp in every data element, so they are consistent regardless of how many push calls are used. The relay server queues each push and delivers them in order. The final `push/complete` call sends a `batch_complete` sentinel to the device so it knows the batch is finished.

**Important:** Options should be sent first or together with the first batch, because `useCompanies`, `useStores`, and other flags determine which catalog types the app expects.

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

Retrieve documents uploaded by devices (orders, cash receipts, …). `limit` is
1–100 (default 50). Pull the queue in a loop until `count` is 0 (or `< limit`)
to drain everything in one run.

**Response Fields:**

| Field | Type | Description |
|-------|------|-------------|
| success | boolean | Operation result |
| data.count | number | Number of items returned |
| data.fetch_token | string | Batch/reservation token; pass it to `pull/ack`. Absent when `count` is 0. |
| data.items | array | Array of document objects |
| data.items[].id | string | Stable queue-row id (used to ack rows that have no `document_guid`) |
| data.items[].document_guid | string | Primary ack key — the document's app guid (present for order/cash) |
| data.items[].device_uuid | string | Source device UUID |
| data.items[].data_type | string | Document type (order, cash, location, etc.) |
| data.items[].data | object | Document payload |
| data.items[].created_at | string | ISO 8601 timestamp |

### `POST /api/v1/pull/ack`

Delivery is **at-least-once**. `GET /pull` does not consume items — it *leases*
them for a while and returns a `fetch_token`. After 1C durably writes/posts a
document, it must confirm it, otherwise the relay re-delivers it on a later pull
(and, after a fixed number of unacknowledged attempts, drops it as a dead-letter).

Because re-delivery is possible, 1C must be **idempotent**: key documents by their
app `guid` and skip ones already posted.

**Request body:**

| Field | Type | Description |
|-------|------|-------------|
| fetch_token | string | Required. The token from the matching `pull` response. |
| document_guids | string[] | Guids of processed documents (primary key, for order/cash). |
| message_ids | string[] | Row `id`s, for items without a `document_guid`. |

At least one of `document_guids` / `message_ids` must be non-empty. Items left
unacknowledged are re-delivered after the lease expires. Confirm only what was
durably processed (a genuinely unprocessable item may also be acked to stop it
from looping). Response: `{ "success": true, "data": { "acked": N } }`.

---

## Data Formats

### Outgoing Data Types (1C → Device)

Data is sent as a **flat array of objects** in the `data` field of the push request. Each object is identified by its `value_id` field. Different data types can be mixed in a single array.

**Example push request:**
```json
{
  "device_uuid": "550e8400-e29b-41d4-a716-446655440000",
  "data": [
    { "value_id": "options", "token": "abc123", "write": true, "read": true, "currency": "грн" },
    { "value_id": "item", "guid": "prod-001", "description": "Молоко 2.5%", "price": 45.50 },
    { "value_id": "item", "guid": "prod-002", "description": "Хліб білий", "price": 22.00 },
    { "value_id": "client", "guid": "client-001", "description": "ТОВ Продукти", "phone": "+380501234567" },
    { "value_id": "price", "item_guid": "prod-001", "price_type": "1", "price_name": "Роздріб", "price": 45.50 },
    { "value_id": "debt", "client_guid": "client-001", "doc_id": "ЗАМ-001", "sum": 1250.75 },
    { "value_id": "discount", "client_guid": "client-001", "item_guid": "prod-001", "discount": -5.0 }
  ]
}
```

**Important:** There are no nested wrappers like `catalog_type` + `items`. Each object is a standalone item with its own `value_id`.

---

#### Options (`value_id: "options"`)

Push user settings to device.

| Field | Type | Description |
|-------|------|-------------|
| value_id | string | Value: `"options"` |
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
| complexDiscounts | boolean | Enable per-client per-product discount system |

---

#### Product (`value_id: "item"`)

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

#### Price (`value_id: "price"`)

| Field | Type | Description |
|-------|------|-------------|
| value_id | string | Value: `"price"` |
| item_guid | string | Product identifier |
| price_type | string | Price type number |
| price_name | string | Price type name |
| price | number | Product price |

---

#### Client (`value_id: "client"`)

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

#### Company (`value_id: "company"`)

| Field | Type | Description |
|-------|------|-------------|
| value_id | string | Value: `"company"` |
| guid | string | Company identifier |
| description | string | Company name |
| is_default | number | Default flag (1/0) |

---

#### Store (`value_id: "store"`)

| Field | Type | Description |
|-------|------|-------------|
| value_id | string | Value: `"store"` |
| guid | string | Store identifier |
| description | string | Store name |
| is_default | number | Default flag (1/0) |

---

#### Stock Level (`value_id: "rest"`)

| Field | Type | Description |
|-------|------|-------------|
| value_id | string | Value: `"rest"` |
| company_guid | string | Company identifier |
| store_guid | string | Store identifier |
| product_guid | string | Product identifier |
| quantity | number | Stock quantity |

---

#### Debt (`value_id: "debt"`)

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

#### Client Location (`value_id: "client_location"`)

| Field | Type | Description |
|-------|------|-------------|
| value_id | string | Value: `"client_location"` |
| client_guid | string | Client identifier |
| latitude | number | Latitude coordinate |
| longitude | number | Longitude coordinate |

---

#### Client Direction (`value_id: "client_direction"`)

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

#### Client Product (`value_id: "client_goods_item"`)

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

#### Product Image (`value_id: "image"`)

| Field | Type | Description |
|-------|------|-------------|
| value_id | string | Value: `"image"` |
| item_guid | string | Product identifier |
| image_guid | string | Image identifier |
| default | string | Default image flag ("1"/"0") |
| time | number | File timestamp |
| url | string | Image file URL (absolute; used verbatim by the device, already versioned via `?v=`) |
| description | string | File description |
| type | string | File type |

The `url` is produced by first uploading the image bytes to the relay (below);
the relay returns the ready-to-use absolute URL that goes into this record.

##### Image upload — `PUT /api/v1/images/{image_guid}`

1C stores image bytes in its own database, so it uploads them to the relay, which
serves them to devices.

- **Auth:** `Authorization: Bearer {license_api_key}` (same as other 1C endpoints).
- **Body:** raw image bytes. `Content-Type` must be `image/jpeg`, `image/png` or
  `image/webp` (otherwise 415). Max size 2 MB (otherwise 413).
- **`X-Image-Time`** header (optional): image version in ms; changes the `?v=` in
  the returned URL so devices re-fetch when the image changes.
- Uploading the same `image_guid` again overwrites it (new version). A separate,
  higher rate limit applies to image endpoints.
- **Response:** standard envelope; the payload is in `data`:
  ```json
  { "success": true, "data": { "stored": true, "url": "https://.../api/v1/images/{guid}?v={time}" } }
  ```
  1C takes `data.url` **as is** for the `url` field of the `image` record above.

Devices download the image by that URL with their own device auth
(`Authorization: Bearer {app_key}:{device_uuid}`); the relay keeps images with a
TTL and refreshes it on upload/access.

---

#### Payment Type (`value_id: "payment_type"`)

| Field | Type | Description |
|-------|------|-------------|
| value_id | string | Value: `"payment_type"` |
| payment_type | string | Payment type identifier |
| description | string | Payment type name |
| is_fiscal | number | Fiscal flag (1/0) |
| is_default | number | Default flag (1/0) |

---

#### Discount (`value_id: "discount"`)

Per-client per-product discount rules. Only sent when `complexDiscounts: true` in options. Replaces the simple `Client.discount` field with a priority-based lookup system.

| Field | Type | Description |
|-------|------|-------------|
| value_id | string | Value: `"discount"` |
| client_guid | string | Client identifier (empty string `""` = applies to all clients) |
| item_guid | string | Product or product group identifier (empty string `""` = applies to all products) |
| discount | number | Discount percentage. Negative = discount (price reduction), positive = surcharge (price increase) |

**Wildcard convention:** Empty string `""` in `client_guid` or `item_guid` means "any". This allows defining discounts at different specificity levels.

**Priority resolution on device** (highest to lowest):

| Priority | client_guid | item_guid | Meaning |
|----------|------------|-----------|---------|
| 1 | exact client | exact product | Product-specific discount for this client |
| 2 | exact client | product group GUID | Group-level discount for this client |
| 3 | exact client | `""` | Client-wide discount (all products) |
| 4 | `""` | exact product | Product-wide discount (all clients) |
| 5 | `""` | product group GUID | Group-wide discount (all clients) |

The device selects the **first matching** rule by priority. If no rule matches, no discount is applied (0%).

**Examples:**

```json
[
  { "value_id": "discount", "client_guid": "client-001", "item_guid": "prod-001", "discount": -10.0, "timestamp": 1710583200000 },
  { "value_id": "discount", "client_guid": "client-001", "item_guid": "group-dairy", "discount": -5.0, "timestamp": 1710583200000 },
  { "value_id": "discount", "client_guid": "client-001", "item_guid": "", "discount": -3.0, "timestamp": 1710583200000 },
  { "value_id": "discount", "client_guid": "", "item_guid": "prod-expensive", "discount": 2.0, "timestamp": 1710583200000 }
]
```

In this example:
- Client "client-001" gets 10% off product "prod-001" (negative value = discount, priority 1)
- Client "client-001" gets 5% off all dairy products in group "group-dairy" (priority 2)
- Client "client-001" gets 3% off everything else (priority 3)
- All other clients pay 2% **more** for product "prod-expensive" (positive value = surcharge, priority 4)

**Note:** `item_guid` can reference either a product GUID or a product group GUID (products with `is_group: 1`). The device uses the product's `group_guid` field to match group-level rules.

---

### Incoming Data Types (Device → 1C)

Retrieved via `GET /api/v1/pull`. The `data_type` field indicates the document type.

#### Order - `data_type: "order"`

The field list below matches what the app actually serializes (`Order.toMap`).
Booleans are sent as integers `1`/`0`. Timestamps are numbers in the app's own
epoch base — the reference 1C module treats `time`/`time_saved` as seconds.

| Field | Type | Description |
|-------|------|-------------|
| type | string | Value: `"order"` |
| guid | string | Document identifier (idempotency / ack key) |
| userID | string | Device/account identifier |
| number | number | Document number |
| date | string | Human-readable date-time (locale-formatted; not machine-parsable) |
| time | number | Created timestamp |
| time_saved | number | Last-saved timestamp (used as the document date by the reference impl) |
| delivery_date | string | Requested delivery date (locale-formatted string) |
| company_guid | string | Company identifier (empty unless companies are exchanged) |
| company | string | Company name |
| store_guid | string | Store/warehouse identifier (empty unless stores are exchanged) |
| store | string | Store name |
| client_guid | string | Client identifier |
| client_id | string | Client code (source: client `code2`) |
| client_description | string | Client name |
| discount | number | Document/client discount, percent |
| discount_value | number | Discount amount (money) |
| next_payment | number | Promised next-payment sum |
| price_type | string | Price-type identifier |
| payment_type | string | `"CASH"` or `"CREDIT"` |
| is_fiscal | number | Fiscal flag (1/0) |
| is_return | number | Return flag (1/0) |
| is_processed | number | Locally finalized/locked flag (1/0) — device-local meaning, not "accepted by 1C" |
| is_sent | number | Sent flag (1/0) |
| status | string | Display status |
| price | number | Document total sum |
| quantity | number | Total quantity |
| weight | number | Total weight |
| latitude | number | Capture latitude |
| longitude | number | Capture longitude |
| distance | number | Distance to client (m) |
| location_time | number | Location capture timestamp |
| rest_type | string | Balance/stock selector |
| items | array | Array of line items |

**Order Line Item Fields** (`items[]`):

| Field | Type | Description |
|-------|------|-------------|
| order_guid | string | Parent order guid |
| item_guid | string | Product identifier |
| code1 | string | Product code — currently carries the same value as `code2` |
| code2 | string | Product code2 |
| description | string | Product name |
| unit_code | string | Unit — currently the unit **display name**, not a numeric code |
| quantity | number | Quantity |
| weight | number | Line weight |
| price | number | Unit price |
| sum | number | Line sum |
| sum_discount | number | Line discount amount |
| is_demand | number | Demand/backorder flag (1/0) |
| is_packed | number | Packed flag (1/0) |

---

#### Cash - `data_type: "cash"`

A cash receipt (payment from a client). Header-only, no line items. Field list
matches `Cash.toMap`.

| Field | Type | Description |
|-------|------|-------------|
| type | string | Value: `"cash"` |
| guid | string | Document identifier (idempotency / ack key) |
| userID | string | Device/account identifier |
| number | number | Document number |
| date | string | Human-readable date-time |
| time | number | Timestamp |
| company_guid | string | Company identifier |
| company | string | Company name |
| client_guid | string | Client (payer) identifier |
| client | string | Client name |
| reference_guid | string | Linked/paid document guid (e.g. the order being paid); empty for an advance |
| sum | number | Payment amount |
| notes | string | Notes |
| fiscal_number | number | Fiscal receipt number |
| is_fiscal | number | Fiscal flag (1/0) |

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

- [README.md](README.md) - Documentation index and integration overview
- [API_DEVICE_MANAGEMENT.md](API_DEVICE_MANAGEMENT.md) - Device & license lifecycle (register, list, settings, status)
- [archive/](archive/) - Deprecated WebSocket transport specs

---

## Changelog

| Version | Date | Changes |
|---------|------|---------|
| 3.3 | 2026-07-07 | Renamed to Data Management API; device ↔ relay transport switched from WebSocket to REST device API; fixed cross-links. |
| 3.2 | 2026-03-24 | Added Discount data type (`value_id: "discount"`) with priority-based lookup, `complexDiscounts` option. |
| 3.1 | 2026-03-16 | Added Sync Modes section: full sync flow, required data types, key options parameters. |
| 3.0 | 2026-03-16 | Fixed data format: flat array with value_id, removed nested catalog_type+items wrappers. Added payment_type. |
| 2.1 | 2025-01-15 | Converted to table format with exact field specifications |
| 2.0 | 2025-01-15 | Rewritten for relay server architecture |
| 1.0 | 2025-01-15 | Initial HTTP service specification |
