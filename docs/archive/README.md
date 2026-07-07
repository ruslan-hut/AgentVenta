# Archive — deprecated WebSocket protocol

These documents describe the **WebSocket transport** between the Android app and the
relay server. WebSocket has been replaced by the REST device API (`/api/v1/device/*`);
the 1C-facing `push`/`pull` contract is unchanged. The protocol is kept here because the
logic it describes is still present server-side and in older app builds.

For the current API see [../README.md](../README.md).

- `API_DEVICE_CONNECTION.md` — WebSocket device connection API reference
- `WEBSOCKET_DOCUMENT_UPLOAD_API.md` — WebSocket document upload protocol
- `WEBSOCKET_UNIFIED_PAYLOAD_FORMAT.md` — WebSocket unified payload format
