# AgentVenta Refactoring Plan

> Updated: November 2024

## Progress Summary

| Phase | Status | Progress |
|-------|--------|----------|
| Phase 1: Critical Fixes | In Progress | 60% |
| Phase 2: Architecture | In Progress | 70% |
| Phase 3: Testing | Not Started | 0% |
| Phase 4: Performance | Partial | 30% |
| Phase 5: Code Organization | Partial | 20% |

---

## Phase 1: Critical Fixes

### Completed
- [x] Remove `runBlocking` from NetworkRepositoryImpl - moved to TokenManagerImpl with dedicated thread executor
- [x] Replace deprecated `onRequestPermissionsResult` with ActivityResultContracts

### Remaining
- [ ] Implement EncryptedSharedPreferences for credentials
- [ ] Add database encryption with SQLCipher
- [ ] Add network security config (certificate pinning)
- [ ] Secure credential storage with Android Keystore

---

## Phase 2: Architecture Improvements

### Completed
- [x] Extract AccountStateManager from SharedViewModel
- [x] Extract ImageLoadingManager from SharedViewModel
- [x] Extract SyncManager from SharedViewModel
- [x] Create DocumentViewModel base class (reduces ~100 lines duplication per ViewModel)
- [x] Migrate core ViewModels to StateFlow (OrderViewModel, OrderListViewModel, SharedViewModel)
- [x] Create domain layer structure (`/domain/result/`, `/domain/usecase/`)
- [x] Implement Result wrapper with DomainException types
- [x] Create order use cases (GetOrdersUseCase, SaveOrderUseCase, ValidateOrderUseCase, etc.)
- [x] Implement use cases in OrderViewModel and OrderListViewModel
- [x] Create UseCaseModule for Hilt DI

### Remaining
- [ ] Migrate remaining ViewModels to StateFlow (CashViewModel, TaskViewModel, ClientViewModel, ProductViewModel)
- [ ] Create use cases for Cash and Task documents
- [ ] Implement CashViewModel and TaskViewModel with use cases
- [ ] Extract Utils to interface for DI (currently direct instantiation blocks testing)
- [ ] Create EventChannel for one-time UI events in remaining ViewModels

---

## Phase 3: Testing Infrastructure

### Not Started
- [ ] Add test dependencies (JUnit, Mockito-Kotlin, Turbine, Truth)
- [ ] Create MainDispatcherRule for coroutine testing
- [ ] Write unit tests for ViewModels (target: 80%)
- [ ] Write unit tests for use cases (target: 90%)
- [ ] Write integration tests for repositories with in-memory Room database
- [ ] Write network tests with MockWebServer
- [ ] Write UI tests for critical flows (order creation, sync)

---

## Phase 4: Performance Optimization

### Completed
- [x] Add database indexes to Order, Client, Product, OrderContent entities
- [x] Create MIGRATION_20_21 for existing installations

### Remaining
- [ ] Cache current account GUID to eliminate subqueries in DAOs
- [ ] Implement Paging 3 for large lists (orders, clients, products)
- [ ] Add Full-Text Search (FTS) for client/product search
- [ ] Optimize Glide configuration (memory/disk cache limits)
- [ ] Add image compression before upload (max 1024px, 85% JPEG quality)

---

## Phase 5: Code Organization

### Completed
- [x] SharedViewModel split into focused managers

### Remaining
- [ ] Reorganize package structure to Clean Architecture:
  ```
  /data/local/dao, /data/local/entity
  /data/remote/api, /data/remote/dto
  /data/repository (implementations)
  /domain/model, /domain/repository (interfaces), /domain/usecase
  /presentation/features/{feature}/
  ```
- [ ] Standardize naming conventions (Entity suffix for Room, Dto for network)
- [ ] Extract BaseDocumentRepository to reduce repository duplication
- [ ] Extract BaseListAdapter for common RecyclerView patterns
- [ ] Remove `L` prefix from UI models (LClient, LProduct) - use clear naming

---

## Remaining High-Priority Tasks

1. **Security** - Encrypted storage for credentials (EncryptedSharedPreferences, Keystore)
2. **Testing** - Establish test infrastructure and achieve basic coverage
3. **Cash/Task ViewModels** - Apply same patterns as Order (use cases, StateFlow)
4. **Paging** - Large lists cause performance issues

---

## Estimated Remaining Effort

| Phase | Hours |
|-------|-------|
| Phase 1 (remaining) | 10-12 |
| Phase 2 (remaining) | 12-15 |
| Phase 3 (all) | 40-50 |
| Phase 4 (remaining) | 15-18 |
| Phase 5 (remaining) | 20-25 |
| **Total** | **97-120** |

---

## Files Modified in Current Session

- `/shared/SyncManager.kt` - NEW: Extracted sync operations
- `/shared/SharedViewModel.kt` - Delegates to SyncManager
- `/di/UseCaseModule.kt` - Added CopyOrderUseCase
- `/domain/usecase/order/CopyOrderUseCase.kt` - NEW
- `/documents/order/OrderViewModel.kt` - Uses ValidateOrderUseCase, SaveOrderUseCase
- `/documents/order/OrderListViewModel.kt` - Uses CopyOrderUseCase
- `/MainActivity.kt` - ActivityResultContracts for permissions
- `/dao/entity/Order.kt`, `Client.kt`, `Product.kt`, `OrderContent.kt` - Database indexes
- `/dao/AppDatabase.kt` - MIGRATION_20_21, version 21
