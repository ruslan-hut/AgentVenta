# AgentVenta — Integration Documentation

AgentVenta is an offline-first Android app for field sales agents. It exchanges data
with a 1C accounting system through the **Sphynx relay server** over a REST API.

This folder documents that server API for **integrators** — 1C developers and admins
who connect their accounting system and manage devices. The app itself is public; the
relay backend is private, so these documents are the source of truth for the API
contract.

## Architecture at a glance

```
┌─────────────┐   REST    ┌─────────────────┐   REST    ┌─────────────────┐
│  1C System  │◄─────────►│  Sphynx Relay   │◄─────────►│  Android Device │
│  (your code)│  api_key  │     Server      │  api_key  │   (AgentVenta)  │
└─────────────┘           └─────────────────┘  + uuid   └─────────────────┘
```

- 1C is a **REST client** of the relay — it never exposes an HTTP service.
- The relay is a **store-and-forward queue**: 1C pushes catalog data, devices pull it;
  devices upload documents, 1C pulls them.
- Everything is authenticated by a per-license **API key** (`Authorization: Bearer …`).

## The API, in two parts

| Document | Scope |
|----------|-------|
| **[API_DEVICE_MANAGEMENT.md](API_DEVICE_MANAGEMENT.md)** | License info, device registration/approval, listing, deletion, per-device settings. |
| **[API_DATA_MANAGEMENT.md](API_DATA_MANAGEMENT.md)** | Catalog push to devices, document pull from devices, sync modes, and all data formats (`value_id` catalog types, order/cash/location document types). |

Start with **API_DEVICE_MANAGEMENT** to get a device registered and approved, then use
**API_DATA_MANAGEMENT** to move catalog and document data.

## App internals

- [LOCATION_TRACKING.md](LOCATION_TRACKING.md) — how the app records GPS (foreground-only,
  no background service). Relevant for understanding the `location` document type.

## Archive

The [archive/](archive/) folder holds **deprecated** material — chiefly the legacy
**WebSocket transport** specs and old implementation plans. WebSocket has been removed
from the app in favour of the REST device API; these files are kept only for historical
reference and should not be used for new integrations.

## Quick reference

**Base URL:** `https://lic.nomadus.net`
**Auth (1C):** `Authorization: Bearer {api_key}`

| Method | Path | Part |
|--------|------|------|
| GET | `/api/v1/license` | Device management |
| GET | `/api/v1/devices` | Device management |
| POST | `/api/v1/devices/register` | Device management |
| DELETE | `/api/v1/devices/{uuid}` | Device management |
| GET/POST | `/api/v1/devices/{uuid}/settings` | Device management |
| POST | `/api/v1/push` | Data management |
| POST | `/api/v1/push/complete` | Data management |
| GET | `/api/v1/pull` | Data management |
