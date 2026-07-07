# Archive — deprecated documentation

These files are **historical** and no longer describe the current integration. They are
kept for reference only. For the current API see [../README.md](../README.md).

## Deprecated WebSocket transport

The app used a WebSocket transport between device and relay; it has been replaced by the
REST device API (`/api/v1/device/*`). The 1C-facing `push`/`pull` contract is unchanged.

- `API_DEVICE_CONNECTION.md` — WebSocket device connection API reference
- `WEBSOCKET_DOCUMENT_UPLOAD_API.md` — WebSocket document upload protocol
- `WEBSOCKET_UNIFIED_PAYLOAD_FORMAT.md` — WebSocket unified payload format
- `websocket-reliability-fixes.md` — WebSocket reliability fix checklist
- `WEBSOCKET_TEST_CHECKLIST.md` / `WEBSOCKET_TEST_CHECKLIST_FULL.md` — manual test checklists
- `WEBSOCKET_TESTING_GUIDE.md` — WebSocket testing guide

## Old implementation plans

- `ANDROID_WEBSOCKET_IMPLEMENTATION_PLAN.md`
- `WEBSOCKET_API_KEY_AUTH_PLAN.md`
- `WEBSOCKET_DOCUMENT_SYNC_PLAN.md`
- `IMPLEMENTATION_PLAN_AUTH_SYNC.md`
- `refactoring_plan.md`
- `relay-hub-plan.md` / `relay-hub.md`
