# WebSocket Document Synchronization Implementation Plan

## Current Status
✅ **Completed:**
- WebSocket connection management
- Settings upload/download via WebSocket
- Manual configuration switch (`useWebSocket` field)
- UI integration in UserAccountFragment

## Goal
Implement document synchronization (Orders, Cash, Images, Location) via WebSocket relay server instead of direct HTTP to 1C.

---

## Architecture Overview

### Current HTTP Sync Flow:
```
Android App → HTTP → 1C Server
   ↓
[Orders, Cash, Images] → POST to 1C
[Catalogs, Debts] ← GET from 1C
```

### New WebSocket Sync Flow:
```
Android App → WebSocket → Relay Server → Queue → 1C Plugin
   ↓                                              ↓
[Orders, Cash, Images] → WebSocket → Queue → 1C reads queue
[Catalogs, Debts] ← WebSocket ← Queue ← 1C pushes to queue
```

---

## Key Principles

1. **Use `useWebSocket` field** - Check `UserAccount.shouldUseWebSocket()` to determine sync method
2. **Reuse existing data models** - Orders, Cash, etc. already have the right structure
3. **Maintain compatibility** - HTTP sync must continue to work for manual mode
4. **Same Room entities** - No changes to database schema for documents
5. **Leverage existing NetworkRepository** - Add WebSocket path alongside HTTP

---

## Implementation Steps

### Phase 1: Extend WebSocket Message Types

**File**: `app/src/main/java/ua/com/programmer/agentventa/utility/Constants.kt`

Add new message type constants:

```kotlin
// Document sync message types
const val WS_MSG_UPLOAD_ORDER = "upload_order"
const val WS_MSG_UPLOAD_CASH = "upload_cash"
const val WS_MSG_UPLOAD_IMAGE = "upload_image"
const val WS_MSG_UPLOAD_LOCATION = "upload_location"
const val WS_MSG_DOWNLOAD_CATALOGS = "download_catalogs"
const val WS_MSG_SYNC_COMPLETE = "sync_complete"
```

---

### Phase 2: Create WebSocket Sync Models

**File**: Create `app/src/main/java/ua/com/programmer/agentventa/data/websocket/SyncModels.kt`

```kotlin
package ua.com.programmer.agentventa.data.websocket

import ua.com.programmer.agentventa.data.local.entity.Order
import ua.com.programmer.agentventa.data.local.entity.Cash

// Document upload wrapper
data class DocumentUpload(
    val document_type: String, // "order", "cash", "image", "location"
    val guid: String,
    val data: Any // Will be serialized Order, Cash, etc.
)

// Sync status response
data class SyncStatusResponse(
    val status: String, // "queued", "processing", "completed", "error"
    val document_guid: String,
    val message: String? = null
)

// Catalog update notification
data class CatalogUpdate(
    val catalog_type: String, // "clients", "products", "debts", etc.
    val update_type: String, // "full", "incremental"
    val data: List<Any>
)
```

---

### Phase 3: Add WebSocket Methods to NetworkRepository

**File**: `app/src/main/java/ua/com/programmer/agentventa/domain/repository/NetworkRepository.kt`

Add new methods to the interface:

```kotlin
// WebSocket document sync
suspend fun uploadOrderViaWebSocket(order: Order, orderContent: List<OrderContent>): Flow<Result<SyncResult>>
suspend fun uploadCashViaWebSocket(cash: Cash): Flow<Result<SyncResult>>
suspend fun uploadImagesViaWebSocket(images: List<ProductImage>): Flow<Result<SyncResult>>
suspend fun uploadLocationsViaWebSocket(locations: List<LocationHistory>): Flow<Result<SyncResult>>
suspend fun downloadCatalogsViaWebSocket(): Flow<Result<CatalogData>>
```

---

### Phase 4: Implement WebSocket Sync in NetworkRepositoryImpl

**File**: `app/src/main/java/ua/com/programmer/agentventa/data/repository/NetworkRepositoryImpl.kt`

Modify existing sync methods to route based on `useWebSocket`:

```kotlin
override suspend fun uploadDocuments(): Flow<Result<SyncResult>> = flow {
    val account = userAccountRepository.getCurrentAccount() ?: run {
        emit(Result.Error("No account"))
        return@flow
    }

    // Check connection mode
    if (account.shouldUseWebSocket()) {
        // Use WebSocket sync
        uploadDocumentsViaWebSocket(account)
    } else {
        // Use existing HTTP sync
        uploadDocumentsViaHttp(account)
    }
}

private suspend fun uploadDocumentsViaWebSocket(account: UserAccount) = flow {
    emit(Result.Progress("Starting WebSocket sync..."))

    // Get unsent orders
    val orders = dataExchangeDao.getOrdersForUpload(account.guid)

    for (order in orders) {
        val content = dataExchangeDao.getOrderContentForUpload(order.guid)

        // Upload via WebSocket
        val payload = mapOf(
            "document_type" to "order",
            "guid" to order.guid,
            "order" to serializeOrder(order),
            "content" to serializeOrderContent(content)
        )

        val result = webSocketRepository.sendMessage(
            type = Constants.WS_MSG_UPLOAD_ORDER,
            payload = payload
        )

        when (result) {
            is Result.Success -> {
                // Mark as sent
                orderDao.markAsSent(order.guid)
                emit(Result.Progress("Order ${order.number} uploaded"))
            }
            is Result.Error -> {
                emit(Result.Error("Failed to upload order: ${result.message}"))
            }
        }
    }

    emit(Result.Success(SyncResult(ordersUploaded = orders.size)))
}
```

---

### Phase 5: Handle Incoming WebSocket Messages for Catalogs

**File**: `app/src/main/java/ua/com/programmer/agentventa/data/repository/WebSocketRepositoryImpl.kt`

Extend message handling to process catalog updates:

```kotlin
override fun onMessage(webSocket: WebSocket, text: String) {
    scope.launch {
        val message = gson.fromJson(text, WebSocketMessage::class.java)

        when (message.type) {
            Constants.WS_MSG_DOWNLOAD_CATALOGS -> {
                // Parse catalog data
                val catalogUpdate = gson.fromJson(
                    message.payload,
                    CatalogUpdate::class.java
                )

                // Save to database based on catalog type
                when (catalogUpdate.catalog_type) {
                    "clients" -> {
                        val clients = parseCatalogData<Client>(catalogUpdate.data)
                        clientDao.insertAll(clients)
                    }
                    "products" -> {
                        val products = parseCatalogData<Product>(catalogUpdate.data)
                        productDao.insertAll(products)
                    }
                    // ... other catalog types
                }

                sendAck(message.message_id)
            }
        }
    }
}
```

---

### Phase 6: Update SyncFragment UI

**File**: `app/src/main/java/ua/com/programmer/agentventa/presentation/features/settings/SyncFragment.kt`

The sync UI should work transparently - no changes needed! The NetworkRepository will automatically route based on `useWebSocket`.

Optional enhancement: Add indicator showing sync method:

```kotlin
viewModel.currentAccount.observe(viewLifecycleOwner) { account ->
    if (account.shouldUseWebSocket()) {
        binding.syncMethodText.text = "Sync via Relay Server (WebSocket)"
    } else {
        binding.syncMethodText.text = "Direct HTTP Sync"
    }
}
```

---

## Testing Plan

### Test 1: WebSocket Document Upload
- [ ] Create new order in app
- [ ] Ensure account is in WebSocket mode (`useWebSocket = true`)
- [ ] Trigger sync
- [ ] Verify order sent via WebSocket (check logs)
- [ ] Verify server receives message in queue
- [ ] Verify order marked as `isSent = 1` in database

### Test 2: HTTP Document Upload (Manual Mode)
- [ ] Switch account to manual mode (`useWebSocket = false`)
- [ ] Create new order
- [ ] Trigger sync
- [ ] Verify order sent via HTTP directly to 1C
- [ ] Verify existing HTTP flow still works

### Test 3: WebSocket Catalog Download
- [ ] Connect via WebSocket
- [ ] Trigger sync
- [ ] Server pushes catalog update
- [ ] Verify client receives catalog data
- [ ] Verify database updated with new catalog items
- [ ] Verify UI shows updated data

### Test 4: Mode Switching
- [ ] Start in WebSocket mode, sync document
- [ ] Switch to HTTP mode
- [ ] Sync another document
- [ ] Verify correct sync method used for each
- [ ] Verify no data loss during switch

### Test 5: Error Handling
- [ ] Test WebSocket disconnection during upload
- [ ] Verify documents remain `isSent = 0`
- [ ] Verify retry logic
- [ ] Test invalid document format
- [ ] Verify error messages shown to user

---

## File Changes Summary

### Modified Files:
1. `Constants.kt` - Add WebSocket message type constants
2. `NetworkRepository.kt` - Add WebSocket sync methods
3. `NetworkRepositoryImpl.kt` - Implement WebSocket sync routing
4. `WebSocketRepositoryImpl.kt` - Add catalog message handling
5. `SyncFragment.kt` - Optional: Add sync method indicator

### New Files:
1. `SyncModels.kt` - WebSocket sync data models

### Total Impact:
- ~500 lines of new code
- 5 existing files modified
- 1 new file created
- No database schema changes
- Fully backward compatible with HTTP sync

---

## Benefits

1. **Offline-First**: Works better with intermittent connectivity
2. **Unified Backend**: All devices communicate through relay server
3. **Real-Time**: Catalog updates pushed instantly
4. **Scalable**: Relay server handles connection management
5. **Secure**: Single point of authentication (relay server)
6. **Flexible**: Easy to switch between HTTP and WebSocket

---

## Next Steps

1. Implement Phase 1-2 (message types and models)
2. Implement Phase 3-4 (WebSocket upload logic)
3. Implement Phase 5 (catalog download handling)
4. Test thoroughly with both modes
5. Monitor and optimize performance
6. Document API for 1C plugin developers
