# Device Management API

This document describes the **device & license lifecycle** part of the Sphynx relay
API: how the 1C accounting system inspects its license, registers devices, lists and
removes them, and pushes per-device settings. For the actual catalog/document data
exchange see [API_DATA_MANAGEMENT.md](API_DATA_MANAGEMENT.md).

## Concepts

- **License** — identifies one 1C base on the relay. It carries a device limit, an
  expiration date, and the `c1_base` connection descriptor. The API key authenticates
  as a license.
- **Device** — one Android installation, identified by a UUID generated on the device
  (`UserAccount.guid`). A device belongs to exactly one license.
- **Device status** — `pending`, `approved`, or `denied`. Unknown devices that appear
  on the API are auto-registered as `pending` and must be approved before they can
  exchange data. Approval is done from the admin console, not this API.

> **License numbers are a server-side concept.** The app never sends a license number
> for authentication — it authenticates devices by API key + UUID. License numbers map
> `device → 1C base` on the relay only.

## Base URL

```
https://lic.nomadus.net
```

## Authentication

All endpoints below use the license API key, identical to the data endpoints:

```
Authorization: Bearer {api_key}
```

The key is issued when the license is created and scopes every request to that license
— you can only see and manage devices belonging to your own license.

---

## Endpoints

| Method | Path | Purpose |
|--------|------|---------|
| GET | `/api/v1/license` | License info + live device counts |
| GET | `/api/v1/devices` | List devices for this license |
| POST | `/api/v1/devices/register` | Register (or look up) a device |
| DELETE | `/api/v1/devices/{uuid}` | Remove a device and its queued messages |
| GET | `/api/v1/devices/{uuid}/settings` | Read a device's `app_params` |
| POST | `/api/v1/devices/{uuid}/settings` | Write a device's `app_params` (and optional name) |

All responses share the standard envelope:

```json
{
  "success": true,
  "status_message": "Success",
  "timestamp": "2026-07-07T10:30:00Z",
  "data": { /* endpoint-specific */ },
  "license": { /* present on some endpoints */ }
}
```

---

### `GET /api/v1/license`

Returns the license descriptor and current device usage. Use it as a health/identity
check and to enforce the device limit before registering new devices.

**`license` object:**

| Field | Type | Description |
|-------|------|-------------|
| license_number | string | License identifier |
| device_limit | number | Maximum number of devices allowed |
| connected_devices | number | Devices currently connected (live) |
| total_devices | number | Total devices registered under this license |
| expiration_date | string | ISO 8601 license expiration |

---

### `GET /api/v1/devices`

Lists all devices registered under the authenticated license.

**`data.items[]` fields:**

| Field | Type | Description |
|-------|------|-------------|
| uuid | string | Device UUID |
| name | string | Human-readable device name (optional) |
| status | string | `pending` \| `approved` \| `denied` |
| connection_state | string | Current connection state |
| last_seen | string | ISO 8601 timestamp of last activity |
| first_connected | string | ISO 8601 timestamp of first registration |

---

### `POST /api/v1/devices/register`

Registers a device under the license, or returns the existing record if it is already
known. Unknown devices seen on other endpoints are auto-registered as `pending`, so
explicit registration is optional — it is mainly used to attach device info and read
back the `c1_base` descriptor.

**Request:**

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| device_uuid | string (UUID) | Yes | Device UUID |
| device_info | object | No | Device metadata (model, OS, app version, …) |

`license_number` is taken from the authenticated context — do **not** send it in the body.

**Response (`data`):**

| Field | Type | Description |
|-------|------|-------------|
| registered | boolean | True if a record now exists |
| device_uuid | string | Echoed device UUID |
| c1_base | object | 1C base connection descriptor for this license (optional) |

A newly registered device starts as `pending` and cannot exchange data until approved
in the admin console.

---

### `DELETE /api/v1/devices/{uuid}`

Removes a device and purges any messages queued for it.

**Response (`data`):**

| Field | Type | Description |
|-------|------|-------------|
| deleted | boolean | True if the device was removed |

---

### `GET /api/v1/devices/{uuid}/settings`

Reads the `app_params` object last stored for the device. `app_params` is the same
options object the device consumes as `value_id: "options"` (see
[API_DATA_MANAGEMENT.md](API_DATA_MANAGEMENT.md) — Options) — it controls the device's
permissions and enabled features.

---

### `POST /api/v1/devices/{uuid}/settings`

Stores per-device settings. This is the persistent home for the options the device
reads on startup; pushing options through the data channel and storing them here keeps
them consistent across reconnects.

**Request:**

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| app_params | object | Yes | Options object (see Options data type) |
| name | string | No | Human-readable device name |

---

## Device-facing endpoints (for reference)

The Android app authenticates with `Bearer {api_key}:{device_uuid}` and calls a
separate `/api/v1/device/*` subtree. Integrators do **not** call these — they are
listed here to complete the picture of the data flow:

| Method | Path | Purpose |
|--------|------|---------|
| GET | `/api/v1/device/status` | Approval/license status polling |
| GET | `/api/v1/device/pull` | Receive queued catalog data from 1C |
| POST | `/api/v1/device/ack` | Acknowledge delivered messages |
| POST | `/api/v1/device/upload` | Upload documents (orders, cash, images, locations) |
| POST | `/api/v1/device/logs` | Upload debug logs |

The `status` response tells the device its `status` (`pending`/`approved`/`denied`),
whether it may transfer data (`can_transfer`), and any `license_error`
(`license_expired`, `license_not_active`, `device_limit_reached`).

---

## Related Documentation

- [README.md](README.md) - Documentation index and integration overview
- [API_DATA_MANAGEMENT.md](API_DATA_MANAGEMENT.md) - Catalog push / document pull, data formats
- [archive/](archive/) - Deprecated WebSocket transport specs

---

## Changelog

| Version | Date | Changes |
|---------|------|---------|
| 1.0 | 2026-07-07 | Initial device management API reference, split out from the integration guide. |
