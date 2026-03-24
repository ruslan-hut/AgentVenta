# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

AgentVenta is an Android mobile application for field sales agents and retail points in Ukraine. It enables offline-first order taking, client management, product catalog browsing, cash receipt processing, location tracking, and fiscal receipt generation (Checkbox PRRO integration). The app synchronizes with 1C accounting systems.

**Key Features:**
- Offline-first architecture with bidirectional sync
- Multi-account support (multiple 1C databases)
- Location tracking and map visualization
- Fiscal receipt generation via Checkbox API
- Bluetooth printer support
- Barcode scanner integration
- Product catalog with pricing and stock levels
- Client debt tracking
- Order management with return support

## Build Commands

### Basic Build
```bash
# Clean build
./gradlew clean

# Build debug APK
./gradlew assembleDebug

# Build release APK (minified)
./gradlew assembleRelease

# Build specific flavor
./gradlew assembleStandartDebug
./gradlew assembleBetaDebug
```

### Testing
```bash
# Run unit tests
./gradlew testStandartDebugUnitTest

# Run all unit tests
./gradlew test
```

### Installation
```bash
# Install debug build to connected device
./gradlew installDebug

# Install specific flavor
./gradlew installStandartDebug
./gradlew installBetaDebug
```

### Version Management
Version is auto-incremented on build via `app/version.properties`. Format: `3.0.PATCH` where PATCH increments each build.

### Product Flavors
- **standart**: Standard production build (targetSdk 35)
- **beta**: Beta testing build with β suffix (targetSdk 35)

Both flavors support minSdk 23, compileSdk 36, Java 21.

### Build Config Fields
Both debug and release builds expose:
- `WEBSOCKET_API_KEY` — from `local.properties`
- `KEY_HOST` — from `local.properties`

## Architecture

### Clean Architecture (MVVM + Use Cases)

The project follows **Clean Architecture** with four layers:

- **Presentation Layer** (`/presentation/`): Fragments, ViewModels, adapters, UI state
- **Domain Layer** (`/domain/`): Repository interfaces, Use Cases, Result types, domain models
- **Data Layer** (`/data/`): Repository implementations, Room DAOs/entities, Retrofit API, WebSocket models
- **Infrastructure Layer** (`/infrastructure/`): Android platform services (location, camera, printer, WebSocket workers, logging, config)

### MVVM Pattern
- **View Layer**: Fragments with ViewBinding/DataBinding
- **ViewModel Layer**: HiltViewModel-annotated ViewModels with StateFlow/LiveData
- **Domain Layer**: Use Cases encapsulate business logic, return `domain.result.Result<T>`
- **Data Layer**: Repository pattern with Room + Retrofit + WebSocket

### Domain Layer

#### Use Case Pattern
Base classes in `/domain/usecase/UseCase.kt`:
- `UseCase<P, R>` — interface returning `Result<R>`
- `FlowUseCase<P, R>` — interface returning `Flow<R>`
- `SuspendUseCase<P, R>` — abstract class with error handling and dispatcher
- `FlowUseCaseBase<P, R>` — abstract class with error handling for flows
- `NoParamUseCase<R>` / `NoParamFlowUseCase<R>` — parameterless variants

#### Domain Result Types
Located in `/domain/result/Result.kt`:
- `Result<T>` sealed class: `Success<T>`, `Error(DomainException)`
- `DomainException` sealed class: `NetworkError`, `DatabaseError`, `ValidationError`, `AuthenticationError`, `BusinessError`, `NotFoundError`
- Extension functions: `onSuccess`, `onError`, `map`, `flatMap`

**Note:** There are TWO Result types in the codebase:
- `data.remote.Result` — legacy HTTP sync result (Progress/Success/Error states for Flow<Result>)
- `domain.result.Result<T>` — domain layer result for use cases

#### Current Use Cases
**Order:** GetOrdersUseCase, GetOrderUseCase, GetOrderWithContentUseCase, CreateOrderUseCase, SaveOrderUseCase, ValidateOrderUseCase, DeleteOrderUseCase, EnableOrderEditUseCase, CopyOrderUseCase, GenerateOrderPrintUseCase, GetProductDiscountUseCase
**Cash:** CreateCashUseCase, SaveCashUseCase, ValidateCashUseCase, DeleteCashUseCase, EnableCashEditUseCase
**Task:** CreateTaskUseCase, SaveTaskUseCase, ValidateTaskUseCase, DeleteTaskUseCase, MarkTaskDoneUseCase

### Dependency Injection (Hilt)

**Module Locations:** `/di/` package

**GlobalModule** (`SingletonComponent`):
- AppDatabase (Room)
- SharedPreferences
- Glide RequestManager
- GeocodeHelper
- ImageLoadingManager (GlideImageLoadingManager)
- UtilsInterface

**CoroutineModule** (`SingletonComponent`):
- `@IoDispatcher` Dispatchers.IO qualifier

**ResourceProviderModule** (`SingletonComponent`):
- ResourceProvider → ResourceProviderImpl binding

**NetworkModule** (`SingletonComponent`):
- OkHttpClient with HttpAuthInterceptor + TokenRefresh authenticator
- `@WebSocketClient` OkHttpClient (no auth interceptor, 30s ping interval)
- Retrofit.Builder with GsonConverterFactory
- HttpClientApi service
- TokenManager → TokenManagerImpl
- ApiKeyProvider
- WebSocketRepository → WebSocketRepositoryImpl
- Gson instance

**DomainModule** (`SingletonComponent + ViewModelComponent + ServiceComponent`):
- All DAO providers (from AppDatabase)
- `RepositoryBindModule`: Binds all repository interfaces to implementations:
  OrderRepository, UserAccountRepository, ProductRepository, ClientRepository,
  NetworkRepository, LogRepository, DataExchangeRepository, CashRepository,
  CommonRepository, TaskRepository, LocationRepository, FilesRepository

**RepositoryModule** (`ViewModelComponent`):
- DocumentRepository<Order> → OrderRepositoryImpl
- DocumentRepository<Cash> → CashRepositoryImpl
- DocumentRepository<Task> → TaskRepositoryImpl

**UseCaseModule** (`ViewModelComponent`, `@ViewModelScoped`):
- All order, cash, task use cases

**PrintModule** (`SingletonComponent`):
- BluetoothAdapter (nullable, from BluetoothManager)

**Qualifiers** (`/di/Qualifiers.kt`):
- `@WebSocketClient` — for WebSocket-specific OkHttpClient

**Scope Strategy:**
- Singleton: Database, network clients, preferences, utilities, WebSocket, logging
- SingletonComponent + ViewModelComponent + ServiceComponent: DAOs and repository bindings (available across all scopes)
- ViewModelComponent: DocumentRepository<T> providers, use cases (`@ViewModelScoped`)

### Database (Room)

**Database:** AppDatabase (version 26)

**Key Entity Categories:**
- **Documents**: Order, Cash, Task (with isSent, isProcessed flags)
- **Catalogs**: Product, Client, Company, Store, PaymentType, PriceType
- **Content**: OrderContent (order lines)
- **Financial**: Debt, Rest (stock levels), ProductPrice, Discount
- **Location**: LocationHistory, ClientLocation
- **Media**: ProductImage, ClientImage (with isLocal flag for sync)
- **System**: UserAccount, LogEvent

**DAOs:** OrderDao, ProductDao, ClientDao, LocationDao, UserAccountDao, LogDao, DataExchangeDao, DiscountDao, TaskDao, CashDao, CommonDao, CompanyDao, StoreDao, RestDao

**UserAccount Fields (current):**
guid, is_current, extended_id, description, license, data_format, db_server, db_name, db_user, db_password, token, options, relay_server, use_websocket

**Multi-Account Architecture:**
Most entities use `db_guid` in composite primary keys. Each UserAccount represents a connection to a different 1C database. Current account marked with `is_current=1`. DAOs automatically filter by current account.

**Primary Key Patterns (important nuance):**
- **Composite `db_guid` PKs**: Product, Client, Task, Company, Store, PriceType, PaymentType, ProductPrice, ProductImage, ClientImage, ClientLocation, Rest, Debt, Discount
- **Autoincrement `_id` PKs**: Order, Cash, OrderContent, LocationHistory, LogEvent — these have `db_guid` as a regular indexed field, NOT part of the primary key
- **Single PK**: UserAccount (guid only — its guid IS the `db_guid` for other tables)

**Migration Strategy:**
Manual migrations defined: MIGRATION_13_14 through MIGRATION_25_26. Schema location: `app/schemas/`

Key migrations since v20:
- **20→21**: Added performance indexes on orders, clients, products, order_content tables
- **21→22**: Added `relay_server` field to UserAccount for WebSocket support
- **22→23**: Added `sync_email` field to UserAccount
- **23→24**: Added `use_websocket` flag to UserAccount (default: 1)
- **24→25**: Removed `sync_email` column (table recreation)
- **25→26**: Added `discounts` table for complex discount system (PK: db_guid, client_guid, product_guid)

### Repository Pattern

**Base Interface:** `DocumentRepository<T>` for generic document operations (Order, Cash, Task) — methods: getDocument, newDocument, getDocuments, updateDocument, deleteDocument, getDocumentListTotals.

**Specific Repository Interfaces** (in `/domain/repository/`):
- **OrderRepository**: Extends DocumentRepository<Order> + order-specific operations (content, pricing, company/store)
- **CashRepository**: Cash-specific operations
- **TaskRepository**: Task-specific operations
- **ClientRepository**: Client catalog browsing, search
- **ProductRepository**: Product catalog with filtering, stock levels
- **NetworkRepository**: Data synchronization (differential and full)
- **LocationRepository**: GPS tracking history
- **UserAccountRepository**: Account management with Flow-based current account
- **DataExchangeRepository**: Sync data transformation between Room entities and network models
- **WebSocketRepository**: WebSocket connection management and messaging
- **LogRepository**: Logging operations
- **FilesRepository**: File operations (images, cache)
- **CommonRepository**: Cross-cutting queries (payment types, price types, companies, stores)

**Location:** Interfaces in `/domain/repository/`, implementations in `/data/repository/` and `/infrastructure/location/`

### Presentation Layer

#### Base ViewModels
- **DocumentViewModel<T>** (`/presentation/common/document/`): Base ViewModel for Order/Cash/Task with StateFlow-based document observation, CRUD operations via DocumentRepository<T>, and EventChannel for one-time UI events
- **DocumentListViewModel<T>** (`/presentation/common/document/`): Base list ViewModel with filtering and date-based queries
- **SharedViewModel** (`/presentation/common/viewmodel/`): Cross-fragment state sharing for document GUID selections, barcode scans, progress messages, image loading, file operations
- **AccountStateViewModel** (`/presentation/common/viewmodel/`): Exposes account state (current account, options, price types, payment types, companies, stores) via StateFlows from AccountStateManager singleton
- **AccountStateManager** (`/presentation/common/viewmodel/`): Singleton managing reactive account state, shared across ViewModels
- **SyncManager** (`/presentation/common/viewmodel/`): Manages sync state and operations

#### Feature ViewModels
Order: OrderViewModel, OrderListViewModel
Cash: CashViewModel, CashListViewModel
Task: TaskViewModel, TaskListViewModel
Client: ClientViewModel, ClientListViewModel, ClientImageViewModel
Product: ProductViewModel, ProductListViewModel, ProductImageViewModel
Maps: ClientsMapViewModel, LocationHistoryViewModel
Settings: SyncViewModel, UserAccountViewModel, UserAccountListViewModel, OptionsViewModel
Other: DebtViewModel, FiscalViewModel, LogViewModel, PrinterViewModel, PickerViewModel, WebSocketTestViewModel, LocationPickupViewModel
Company/Store: ListViewModel (shared name, separate packages)

#### UI State
- `UiState` (`/presentation/common/viewmodel/UiState.kt`): UI state management
- `SharedParameters` (`/presentation/common/viewmodel/SharedParameters.kt`): Shared parameter definitions
- `DocumentEvent` / `EventChannel`: One-time event delivery pattern
- `ImageLoadingManager` interface with `GlideImageLoadingManager` implementation

### Network Layer

**API Service:** HttpClientApi (Retrofit) at `/data/remote/api/`

**Endpoints:**
- `GET check/{id}` - Token validation/refresh
- `GET get/{type}/{token}{more}` - Data download with pagination. Types: `clients`, `goods`, `debts`, `payment_types`, `companies`, `stores`, `rests`, `images`, `clients_locations`, `clients_directions`, `clients_goods`, `discounts`
- `POST post/{token}` - Document upload
- `GET document/{type}/{guid}/{token}` - Document content fetch
- `GET print/{guid}` - PDF receipt generation

**Authentication** (`/data/remote/interceptor/`):
- HttpAuthInterceptor: Adds Basic Auth headers from UserAccount credentials
- TokenRefresh: Automatic token renewal on 401/403 responses

**Token Management** (`/data/remote/`):
- TokenManager interface + TokenManagerImpl: Manages token lifecycle with UserAccountRepository

**HTTP Sync Models** (`/data/remote/`):
- `Result` sealed class (legacy): Progress/Success/Error for sync Flow tracking
- `SendResult`: Upload result handling
- `HttpClient`: HTTP client wrapper

**WebSocket Layer** (`/data/websocket/` + `/infrastructure/websocket/`):
- **Data models** (`/data/websocket/`): WebSocketMessage, WebSocketState, PendingMessage, SyncModels, WebSocketMessageFactory
- **Infrastructure** (`/infrastructure/websocket/`): WebSocketConnectionManager, WebSocketSyncWorker, PendingDataChecker, NetworkConnectivityMonitor
- **Repository**: WebSocketRepository interface (`/domain/repository/`) + WebSocketRepositoryImpl (`/data/repository/`)
- Uses `@WebSocketClient` OkHttpClient (no auth interceptor, Bearer token format)
- ApiKeyProvider (`/infrastructure/config/`) manages WebSocket API key from BuildConfig

**Sync Strategy (HTTP mode):**
1. **Full Sync**: Download all catalogs (clients, goods, debts, payment_types, companies, stores, rests, images, discounts) based on UserOptions. Optional catalogs added to sync queue conditionally (e.g., `discounts` only when `complexDiscounts=true`, `companies` only when `useCompanies=true`). Cleans old data via timestamp comparison.
2. **Differential Sync**: Upload unsent documents (orders, cash, images, locations), receive sync results.

**Sync Strategy (WebSocket mode):**
1. **Document Upload**: App uploads unsent documents (orders, cash, images, locations) via WebSocket relay.
2. **Catalog Receipt**: Fully passive — 1C pushes catalog data (including discounts) through the relay server at its own initiative. Each data element contains a `value_id` field identifying the data type (e.g., `"discount"`, `"item"`, `"client"`) and a UTC millisecond `timestamp` field set by 1C. The app routes data to the correct loader via `DataExchangeRepositoryImpl.saveFilteredData()` based on `value_id`.
3. **Batch Complete**: When 1C finishes pushing all data, it sends `POST /api/v1/push/complete` with the same timestamp. The relay delivers a `batch_complete` sentinel to the app.
4. **Cleanup**: On receiving `batch_complete`, the app deletes all local catalog data where `timestamp < T` (the 1C timestamp), removing items not refreshed in the current batch. This includes discounts table cleanup.

**Progress Tracking:** Flow<Result> with Progress/Success/Error states (legacy `data.remote.Result`).

### Navigation

**Pattern:** Single Activity (MainActivity) with NavHostFragment

**Navigation Graph:** `/res/navigation/navigation.xml`

**Start Destination:** OrderListFragment (main screen)

**Safe Args:** Enabled for type-safe argument passing between fragments

**Main Navigation Flows:**
- Order management: OrderList → Order → ProductList/ClientList → Fiscal
- Client management: ClientList → Client → ClientInfo/ClientDebts/ClientImage/Order/Cash/Camera/Location
- Product browsing: ProductList (recursive for groups) → Product → ProductImage
- Settings: SettingsFragment → Sync/UserAccountList/UserAccount/Options/ScannerSettings/ScannerTest/ApplicationSettings/PrinterSettings
- Maps: ClientsMap, LocationHistory
- Picker: PickerFragment (reusable selection UI for price types, payment types, etc.)
- Company/Store: CompanyList, StoreList
- WebSocket: WebSocketTestFragment (diagnostics)
- Logger: LogFragment

### Key Business Logic Areas

#### Multi-Account System
Each UserAccount represents a connection to a 1C database with its own data partition. Switch accounts via `is_current=1` flag. All DAOs filter by current account automatically. `use_websocket` flag (default: 1) controls sync mode per account.

#### License Number Usage (IMPORTANT)
**License numbers are used on the backend to identify 1C bases, NOT for device authorization.**

**Architecture:**
- Backend Server: Maintains mapping `device_uuid → license_number → 1C_base`
- License numbers identify which 1C accounting database to use
- Device UUIDs (UserAccount.guid) identify individual devices/accounts

**Android App Behavior:**
- **Receives** license number from backend in UserAccount.options
- **Stores** license in `UserAccount.license` field (for display/reference only)
- **Displays** license number in settings UI (read-only)
- **Does NOT send** license number for authentication/authorization
- **WebSocket connections**: Use only device UUID (`?uuid={guid}`), NOT license

**Backend Behavior:**
- Links device UUIDs to license numbers server-side
- Uses license numbers to route data to/from correct 1C database
- Validates device access by checking if device UUID is linked to valid license

**Key Point:** Never use `UserAccount.license` for authorization. It's metadata received from backend for display purposes only.

#### Offline-First Sync
All data stored locally in Room. Documents created offline marked with `isSent=0`. Sync uploads unsent documents via HTTP or WebSocket.

**HTTP mode:** App pulls catalog data from 1C server, stamps items with local timestamp, cleans up stale data after download completes.

**WebSocket mode:** Catalog data is pushed by 1C through the relay server — the app does not request it. 1C generates a UTC millisecond timestamp, embeds it in every data element, and sends `batch_complete` with the same timestamp when done. The app saves items with the 1C timestamp (already in the data) and uses the `batch_complete` timestamp for cleanup (`DELETE WHERE timestamp < T`). The `batchComplete` flow in `WebSocketRepository` triggers cleanup in `NetworkRepositoryImpl`.

**WebSocket Infrastructure:**
- `WebSocketConnectionManager` (Singleton, `DefaultLifecycleObserver`): Lifecycle-aware WebSocket management via `ProcessLifecycleOwner`. Connects on foreground/network-available, disconnects on background after grace period. **Always connects for license/device status regardless of `use_websocket` flag** — the flag only controls whether data exchange uses WebSocket vs HTTP.
- `WebSocketSyncWorker` (`@HiltWorker` CoroutineWorker): Periodic background sync (min 15-minute interval per WorkManager). Checks pending data via `PendingDataChecker`, triggers `WebSocketConnectionManager.checkAndConnect()`. Exponential backoff on failure.
- `PendingDataChecker` (Singleton): Queries `DataExchangeDao` for counts of unsent orders, cash, images, locations. Exposes `PendingDataSummary`.
- `NetworkConnectivityMonitor` (Singleton): Observes network connectivity changes to trigger reconnection.

#### Document-Content Pattern
Orders use header-lines structure: Order (header) + OrderContent (lines). Cascade delete operations. Totals calculated via DAO aggregations with real-time Flow<DocumentTotals>.

#### Discount System

Two discount modes controlled by `UserOptions.complexDiscounts` (default: `false`):

**Simple Mode (complexDiscounts = false):**
- `Client.discount` value is copied to `Order.discount` when client is selected via `Order.setClient()`
- `OrderContent.discount` stays 0 — line sums are `price × quantity`
- Discount is stored at order header level only

**Complex Mode (complexDiscounts = true):**
- Uses `discounts` table synced from 1C with priority-based per-product per-client discount lookup
- Discount is a **percentage** (negative = discount/price reduction, positive = surcharge/price increase)
- Applied automatically when adding products to orders (`OrderViewModel.onProductClick()`)
- Also recalculated when price type changes (`OrderRepositoryImpl.recalculateContentPrices()`)
- NOT applied when copying from previous orders (those are historical snapshots)

**Discount Table Structure:**
```
Table: discounts
PK: (db_guid, client_guid, product_guid)
Fields: discount (REAL, percentage; negative = discount, positive = surcharge), timestamp (INTEGER)
Convention: empty string "" = wildcard (any client / any product)
```

**Lookup Priority** (resolved in single SQL query via ORDER BY + LIMIT 1):

| Priority | client_guid | product_guid | Meaning |
|----------|------------|-------------|---------|
| 1 (highest) | exact client | exact product | Product-specific for this client |
| 2 | exact client | product's group guid | Group-level for this client |
| 3 | exact client | "" (empty) | Client-wide discount |
| 4 | "" (empty) | exact product | Product-wide for all clients |
| 5 | "" (empty) | product's group guid | Group-wide for all clients |
| 6 | — | — | No discount (0.0) |

**Key Implementation Details:**
- `DiscountDao.getDiscount()` — single SQL query resolves all 5 priority levels
- `GetProductDiscountUseCase` — wraps DAO call, auto-resolves `groupGuid` via `product.group_guid` when not provided
- `OrderContent.discount` stores the **monetary adjustment** (not percentage): `lineSum × discountPercent / 100` (negative = price reduced, positive = price increased)
- `OrderContent.sum` = `calculateLineSum(price, quantity) + discount` (adding negative discount reduces the sum)
- `DocumentTotals.discount` = SUM of all `OrderContent.discount` values
- `Order.discountValue` stores the total discount amount across all lines
- Product groups identified by `Product.isGroup = 1`, linked via `Product.groupGuid` (single-level hierarchy only)

**Sync:** Discount data synced as `DATA_DISCOUNT = "discount"` constant. HTTP mode: added to sync queue when `complexDiscounts` enabled. WebSocket mode: handled automatically via `DataExchangeRepository` routing. Cleanup follows standard timestamp-based pattern.

#### Location Tracking
Foreground service (LocationUpdatesService) continuously tracks GPS at 10-second intervals. Filtering by accuracy threshold and minimum distance. History stored in LocationHistory table. Addresses resolved via GeocodeHelper interface (GeocodeHelperImpl using Geocoding API).

**Location Constants:**
- LOCATION_MIN_ACCURACY: Max acceptable GPS error
- LOCATION_MIN_DISTANCE: Min distance between tracked points

#### Fiscal Receipt Integration
Checkbox PRRO system integration for Ukrainian fiscal compliance. Order marked with `isFiscal=1` triggers receipt creation. Flow: Order saved → FiscalFragment → Checkbox API (cashier login, shift open, receipt create) → Store fiscal number.

**Fiscal Options:** Provider ID, Device ID, Cashier PIN stored in UserAccount.options.

#### User Options System
Server-controlled feature flags in UserAccount.options JSON. Parsed to UserOptions data class. Controls UI visibility and features: locations tracking, image loading, company/store selection, fiscal provider, complex discounts, etc.

#### SharedViewModel Pattern
Cross-fragment state sharing for document GUID selections, barcode scans, progress messages. Provides image loading via ImageLoadingManager, file operations via FilesRepository, and action callbacks for client/product selection.

**AccountStateManager** (singleton) centralizes reactive account state (current account, options, price/payment types, companies, stores) — exposed through AccountStateViewModel for fragments that need account state without depending on SharedViewModel.

#### Barcode Scanner Integration
Hardware scanner input captured in MainActivity.dispatchKeyEvent(). Time-based keystroke grouping (60ms threshold). Barcode broadcasts via SharedViewModel. ViewModels react to scans for product lookup.

#### Image Handling
Glide 5.x with custom auth headers via ImageLoadingManager abstraction. Local cache for captured images. Base64 encoding for upload. `isLocal=1` flag indicates not yet synced. Automatic cleanup after successful sync.

#### Printer Integration
Two independent printing mechanisms in `/infrastructure/printer/`:

**Bluetooth Printer:**
- `PrinterViewModel`, `PrinterSettingsFragment`
- Uses `BluetoothSocket` with SerialPortService UUID. Sends raw bytes to paired device.
- Printer address stored in SharedPreferences as `printer_address`
- `BluetoothModule` provides nullable BluetoothAdapter

**Webhook Printer:**
- `WebhookPrintService` (`@Singleton`, not an Android Service): HTTP-based printing via OkHttp
- Supports GET/POST with optional Basic Auth. Sends `OrderPrintData` as JSON payload.
- Configured via prefs: `webhook_print_enabled`, `webhook_print_url`, `webhook_print_method`, `webhook_print_use_auth`, `webhook_print_username`, `webhook_print_password`

**Print Formatter:**
- `OrderPrintFormatter`: Formats order data to fixed-width text for thermal printing. Width configurable via preference `print_area_width` (default 32 chars).

## Project Structure

```
/agentventa
├── /presentation/                       # UI Layer
│   ├── /main/                           # MainActivity, AgentApplication
│   ├── /common/                         # Shared presentation components
│   │   ├── /adapter/                    # Base/shared adapters
│   │   ├── /document/                   # DocumentViewModel<T>, DocumentListViewModel<T>
│   │   └── /viewmodel/                  # SharedViewModel, AccountStateViewModel,
│   │                                    #   AccountStateManager, SyncManager,
│   │                                    #   ImageLoadingManager, UiState, SharedParameters
│   └── /features/                       # Feature screens
│       ├── /order/                      # OrderFragment, OrderListFragment, OrderViewModel, OrderListViewModel
│       ├── /cash/                       # CashFragment, CashListFragment + ViewModels
│       ├── /task/                       # TaskFragment, TaskListFragment + ViewModels
│       ├── /client/                     # ClientFragment, ClientListFragment, ClientInfoFragment,
│       │                                #   ClientDebtsFragment, ClientImageFragment + ViewModels
│       ├── /product/                    # ProductFragment, ProductListFragment, ProductImageFragment + ViewModels
│       ├── /picker/                     # PickerFragment, PickerViewModel (reusable selection UI)
│       ├── /debt/                       # DebtFragment, DebtViewModel
│       ├── /fiscal/                     # FiscalFragment, FiscalViewModel
│       │   └── /checkbox/               # Checkbox PRRO implementation
│       ├── /map/
│       │   ├── /clients/                # ClientsMapFragment, ClientsMapViewModel
│       │   └── /history/                # LocationHistoryFragment, LocationHistoryViewModel
│       ├── /locations/
│       │   └── /pickup/                 # LocationPickupFragment, LocationPickupViewModel
│       ├── /company/                    # CompanyListFragment, ListViewModel
│       ├── /store/                      # StoreListFragment, ListViewModel
│       ├── /settings/                   # SettingsFragment, SyncFragment, UserAccountFragment,
│       │                                #   UserAccountListFragment, OptionsFragment,
│       │                                #   ScannerSettingsFragment, ScannerTestFragment,
│       │                                #   ApplicationSettingsFragment + ViewModels
│       ├── /websocket/                  # WebSocketTestFragment, WebSocketTestViewModel
│       └── /logger/                     # LogFragment, LogViewModel
│
├── /domain/                             # Domain Layer
│   ├── /repository/                     # Repository interfaces (DocumentRepository, OrderRepository,
│   │                                    #   CashRepository, TaskRepository, ClientRepository,
│   │                                    #   ProductRepository, NetworkRepository, WebSocketRepository,
│   │                                    #   UserAccountRepository, DataExchangeRepository,
│   │                                    #   LocationRepository, LogRepository, FilesRepository,
│   │                                    #   CommonRepository)
│   ├── /usecase/                        # Use case base classes + implementations
│   │   ├── UseCase.kt                  # Base interfaces and abstract classes
│   │   ├── /order/                      # Order use cases (10 use cases)
│   │   ├── /cash/                       # Cash use cases (5 use cases)
│   │   └── /task/                       # Task use cases (5 use cases)
│   └── /result/                         # Result<T> sealed class, DomainException hierarchy
│
├── /data/                               # Data Layer
│   ├── /local/
│   │   ├── /database/                   # AppDatabase (v26)
│   │   ├── /dao/                        # Room DAOs (14 DAOs)
│   │   └── /entity/                     # Room entities with db_guid (20 entities)
│   ├── /remote/
│   │   ├── /api/                        # HttpClientApi (Retrofit service)
│   │   ├── /dto/                        # Network DTOs (UserAccountDto, etc.)
│   │   ├── /interceptor/                # HttpAuthInterceptor, TokenRefresh
│   │   ├── HttpClient.kt               # HTTP client wrapper
│   │   ├── TokenManager.kt             # Token management interface
│   │   ├── TokenManagerImpl.kt          # Token management implementation
│   │   ├── Result.kt                    # Legacy sync Result (Progress/Success/Error)
│   │   └── SendResult.kt               # Upload result model
│   ├── /repository/                     # Repository implementations
│   │                                    #   (OrderRepositoryImpl, CashRepositoryImpl,
│   │                                    #   TaskRepositoryImpl, ClientRepositoryImpl,
│   │                                    #   ProductRepositoryImpl, NetworkRepositoryImpl,
│   │                                    #   UserAccountRepositoryImpl, DataExchangeRepositoryImpl,
│   │                                    #   WebSocketRepositoryImpl, LogRepositoryImpl,
│   │                                    #   FilesRepositoryImpl, CommonRepositoryImpl)
│   └── /websocket/                      # WebSocket data models
│       ├── WebSocketMessage.kt          # Message types
│       ├── WebSocketState.kt            # Connection states
│       ├── PendingMessage.kt            # Queued messages
│       ├── SyncModels.kt                # Sync-specific models
│       └── WebSocketMessageFactory.kt   # Message construction
│
├── /infrastructure/                     # Infrastructure Layer
│   ├── /location/                       # LocationUpdatesService, LocationRepositoryImpl,
│   │                                    #   GeocodeHelper, GeocodeHelperImpl
│   ├── /camera/                         # CameraFragment (CameraX photo capture)
│   ├── /printer/                        # PrinterViewModel, PrinterSettingsFragment,
│   │                                    #   OrderPrintFormatter, WebhookPrintService
│   ├── /websocket/                      # WebSocketConnectionManager, WebSocketSyncWorker,
│   │                                    #   PendingDataChecker, NetworkConnectivityMonitor
│   ├── /logger/                         # Logger interface
│   └── /config/                         # ApiKeyProvider
│
├── /di/                                 # Hilt DI modules
│   ├── GlobalModule.kt                  # Singleton: DB, prefs, Glide, geocoder, image loading
│   │                                    #   + CoroutineModule + ResourceProviderModule
│   ├── NetworkModule.kt                 # Singleton: HTTP client, Retrofit, WebSocket, API key
│   ├── DomainModule.kt                  # DAO providers + RepositoryBindModule
│   ├── RepositoryModule.kt              # ViewModelComponent: DocumentRepository<T> providers
│   ├── UseCaseModule.kt                 # ViewModelComponent: Use case providers
│   ├── PrintModule.kt                   # Singleton: BluetoothAdapter
│   └── Qualifiers.kt                    # @WebSocketClient qualifier
│
├── /extensions/                         # Kotlin extension functions
└── /utility/                            # Utilities, Constants, ResourceProvider
```

## Development Conventions

### Threading
- Coroutines with Dispatchers.IO for database and network operations (injectable via `@IoDispatcher`)
- Dispatchers.Main for UI updates
- ViewModelScope for ViewModel-scoped coroutines
- Use cases accept CoroutineDispatcher parameter for testability

### State Management
- **StateFlow** for reactive UI state (preferred for new code)
- **LiveData** for legacy UI state (some ViewModels still use asLiveData())
- **EventChannel** (Channel-based) for one-time UI events (navigation, snackbars)
- **MutableStateFlow** for mutable state in ViewModels

### Error Handling
- **Domain layer**: `domain.result.Result<T>` + `DomainException` hierarchy for typed errors
- **Data layer (legacy)**: `data.remote.Result` with Progress/Success/Error for sync operations
- Logger interface injected via Hilt
- LogDao for persistent logs
- Firebase Crashlytics for production crashes
- User ID tracking for support

### Database Queries
- Flow-based queries for real-time UI updates
- @Transaction for atomic operations
- Suspend functions for one-shot queries
- JOIN queries for complex data (e.g., Order with Client, Company)
- Performance indexes on frequently queried columns (since migration 20→21)

### Code Style
- Kotlin coroutines preferred over RxJava
- Data classes for models
- Sealed classes for Result types
- Extension functions in `/extensions/` for reusable logic
- Use Cases for business logic (validate, save, create, delete operations)
- Repository interfaces in domain layer, implementations in data layer

### Testing
- Unit tests in `app/src/test/` with Mockito, Truth, Turbine, Coroutines Test
- Test utilities: MainDispatcherRule, FlowTestExtensions, LiveDataTestExtensions, CoroutineTestExtensions
- Fake repositories in `/test/.../fake/` (FakeOrderRepository, FakeClientRepository, FakeProductRepository, FakeUserAccountRepository, FakeNetworkRepository, FakeTaskRepository, FakeCashRepository)
- Test fixtures in `/test/.../fixtures/TestFixtures.kt`
- Robolectric for Android framework simulation
- Hilt testing support (hilt-android-testing)

## Critical Implementation Notes

### When Adding New Document Types
1. Create entity with `db_guid` composite key in `/data/local/entity/`
2. Create DAO with current account filtering in `/data/local/dao/`
3. Define repository interface in `/domain/repository/` (extend DocumentRepository<T> if applicable)
4. Create repository implementation in `/data/repository/`
5. Create use cases in `/domain/usecase/{type}/`
6. Add DAO provider in DomainModule, repository binding in RepositoryBindModule
7. Add DocumentRepository<T> provider in RepositoryModule
8. Add use case providers in UseCaseModule
9. Update DataExchangeRepository for sync transformation
10. Add sync type to NetworkRepository
11. Create ViewModel (extend DocumentViewModel<T>) and Fragment in `/presentation/features/{type}/`

### When Modifying Database Schema
1. Increment AppDatabase version (currently 26)
2. Create MIGRATION_X_Y in AppDatabase
3. Test migration on existing data
4. Update schema export in `app/schemas/`

### When Adding New Sync Data Types
1. Add type to NetworkRepository.updateAll()
2. Create DTO in `/data/remote/dto/`
3. Add transformation in DataExchangeRepository
4. Update HttpClientApi endpoint if needed
5. For WebSocket: add message type in `/data/websocket/`, handle in WebSocketRepositoryImpl
6. Add progress reporting

### When Adding New Use Cases
1. Create use case class in `/domain/usecase/{feature}/`
2. Extend appropriate base class (SuspendUseCase, FlowUseCaseBase, NoParamUseCase, etc.)
3. Add provider in UseCaseModule with `@ViewModelScoped`
4. Inject into ViewModel constructor

### Security Considerations
- Never commit google-services.json with production credentials
- API credentials stored in UserAccount (encrypted database)
- ProGuard rules in app/proguard-rules.pro for release builds
- local.properties excluded from git (SDK paths, keys, WEBSOCKET_API_KEY, KEY_HOST)
- WebSocket API key loaded via BuildConfig, not hardcoded

### Testing (Demo Mode)
Demo mode available for evaluation:
- Server address: "demo"
- Database name: "demo"
- Connects to test 1C instance at hoot.com.ua/simple

## Key Dependencies

- **Kotlin**: 2.3.10
- **Hilt (Dagger)**: 2.59.2
- **Room**: 2.8.4
- **Retrofit**: 3.0.0
- **OkHttp Logging**: 5.3.2
- **Navigation Component**: 2.9.7
- **CameraX**: 1.5.3
- **Firebase BOM**: 34.9.0 (Messaging, Crashlytics, Firestore, Auth)
- **Glide**: 5.0.5
- **Google Play Services**: Maps 20.0.0, Location 21.3.0
- **WorkManager**: 2.11.1
- **Lifecycle**: 2.10.0
- **Material**: 1.13.0
- **KSP**: 2.3.4
- **AGP**: 9.1.0

### Test Dependencies
- JUnit 4.13.2, Mockito 5.21.0, Mockito-Kotlin 6.1.0
- Google Truth 1.4.5, Turbine 1.2.1
- Coroutines Test 1.10.2, Robolectric 4.16.1
- AndroidX Test (Core, JUnit, Espresso, Arch Core Testing)

## Key Constants (`Constants.java`)

**Location:** LOCATION_MIN_DISTANCE=30m, LOCATION_MIN_ACCURACY=50m

**Document Types:** `DOCUMENT_ORDER="order"`, `DOCUMENT_CASH="cash"`, `DOCUMENT_TASK="task"`

**Sync Formats:** `SYNC_FORMAT_FTP="FTP_server"` (legacy), `SYNC_FORMAT_WEB="Web_service"` (legacy), `SYNC_FORMAT_HTTP="HTTP_service"`, `SYNC_FORMAT_WEBSOCKET="WebSocket_relay"`

**WebSocket Timing:** Reconnect initial=1s, max=60s; Ping interval=30s; Sync worker intervals: default=15min, min=5min, max=60min

**WebSocket Message Types:** `data`, `ack`, `ping`, `pong`, `error`, `upload_order`, `upload_cash`, `upload_image`, `upload_location`, `download_catalogs`

**Sync Data Types:** `DATA_DISCOUNT="discount"`, `DATA_GOODS_ITEM="item"`, `DATA_PRICE="price"`, `DATA_CLIENT="client"`, `DATA_COMPANY="company"`, `DATA_STORE="store"`, `DATA_REST="rest"`, `DATA_PAYMENT_TYPE="payment_type"`, `DATA_IMAGE="image"`, `DATA_DEBT="debt"`

**Device Status Values:** `pending`, `approved`, `denied`

**License Error Codes:** `license_expired`, `license_not_active`, `device_limit_reached`

## Useful File Locations

- **Constants**: `/utility/Constants.java` (Java file, not Kotlin)
- **Extensions**: `/extensions/` package
- **Navigation graph**: `/res/navigation/navigation.xml`
- **Version config**: `app/version.properties`
- **Database migrations**: `/data/local/database/AppDatabase.kt` (MIGRATION_X_Y)
- **ProGuard rules**: `app/proguard-rules.pro`
- **Gradle properties**: `gradle.properties`, `local.properties`
- **Use case base classes**: `/domain/usecase/UseCase.kt`
- **Domain Result types**: `/domain/result/Result.kt`
- **DI modules**: `/di/` package (7 files)
- **Discount entity**: `/data/local/entity/Discount.kt`
- **Discount DAO**: `/data/local/dao/DiscountDao.kt` (priority-based lookup query)
- **Discount use case**: `/domain/usecase/order/GetProductDiscountUseCase.kt`
- **Test utilities**: `app/src/test/.../util/` (MainDispatcherRule, extensions)
- **Test fakes**: `app/src/test/.../fake/` (7 fake repositories)
