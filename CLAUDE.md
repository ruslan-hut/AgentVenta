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

Both flavors support minSdk 23, compileSdk 36.

## Architecture

### MVVM Pattern
- **View Layer**: Fragments with ViewBinding/DataBinding
- **ViewModel Layer**: HiltViewModel-annotated ViewModels with LiveData/Flow
- **Model Layer**: Repository pattern with Room + Retrofit

### Dependency Injection (Hilt)

**Module Locations:** `/di/` package

**GlobalModule** (Singleton scope):
- AppDatabase (Room)
- SharedPreferences
- Glide RequestManager
- GeocodeHelper
- LicenseManager
- Coroutine Dispatchers (IoDispatcher)

**NetworkModule** (Singleton):
- OkHttpClient with HttpAuthInterceptor
- Retrofit with Gson/Moshi converters
- TokenRefresh authenticator (auto token renewal)
- HttpClientApi service

**RepositoryModule** (ViewModelComponent scope):
- DocumentRepository implementations (Order, Cash, Task)
- Scoped to ViewModel lifecycle

**Scope Strategy:**
- Singleton: Database, network clients, preferences, utilities
- ViewModelComponent: Repositories (tied to ViewModel lifecycle)

### Database (Room)

**Database:** AppDatabase (version 20)

**Key Entity Categories:**
- **Documents**: Order, Cash, Task (with isSent, isProcessed flags)
- **Catalogs**: Product, Client, Company, Store, PaymentType, PriceType
- **Content**: OrderContent (order lines)
- **Financial**: Debt, Rest (stock levels), ProductPrice
- **Location**: LocationHistory, ClientLocation
- **Media**: ProductImage, ClientImage (with isLocal flag for sync)
- **System**: UserAccount, LogEvent

**Multi-Account Architecture:**
All entities have `db_guid` field (composite primary keys). Each UserAccount represents a connection to a different 1C database. Current account marked with `is_current=1`. DAOs automatically filter by current account.

**Migration Strategy:**
Manual migrations defined (MIGRATION_13_14, etc.). Schema location: `app/schemas/`

### Repository Pattern

**Base Interface:** `DocumentRepository<T>` for generic document operations (Order, Cash, Task).

**Key Repositories:**
- **OrderRepository**: Order CRUD, content management, price types, company/store selection
- **ClientRepository**: Client catalog browsing, search
- **ProductRepository**: Product catalog with filtering, stock levels
- **NetworkRepository**: Data synchronization (differential and full)
- **LocationRepository**: GPS tracking history
- **UserAccountRepository**: Account management with Flow-based current account
- **DataExchangeRepository**: Sync data transformation between Room entities and network models

**Location:** Interfaces in `/repository/`, implementations in `/dao/impl/`, `/geo/`, `/http/`

### Network Layer

**API Service:** HttpClientApi (Retrofit)

**Endpoints:**
- `GET check/{id}` - Token validation/refresh
- `GET get/{type}/{token}{more}` - Data download with pagination
- `POST post/{token}` - Document upload
- `GET document/{type}/{guid}/{token}` - Document content fetch
- `GET print/{guid}` - PDF receipt generation

**Authentication:**
- HttpAuthInterceptor: Adds Basic Auth headers from UserAccount credentials
- TokenRefresh: Automatic token renewal on 401/403 responses

**Sync Strategy:**
1. **Full Sync**: Download all catalogs (clients, goods, debts, payment_types, companies, stores, rests, images) based on UserOptions. Cleans old data (60-day retention).
2. **Differential Sync**: Upload unsent documents (orders, cash, images, locations), receive sync results.

**Progress Tracking:** Flow<Result> with Progress/Success/Error states.

### Navigation

**Pattern:** Single Activity (MainActivity) with NavHostFragment

**Navigation Graph:** `/res/navigation/navigation.xml`

**Start Destination:** OrderListFragment (main screen)

**Safe Args:** Enabled for type-safe argument passing between fragments

**Main Navigation Flows:**
- Order management: OrderList → Order → ProductList/ClientList → Fiscal
- Client management: ClientList → Client → Order/Cash/Debt/Camera/Location
- Product browsing: ProductList (recursive for groups) → Product details
- Settings: Sync → UserAccount configuration
- Maps: ClientsMap, LocationHistory

### Key Business Logic Areas

#### Multi-Account System
Each UserAccount represents a connection to a 1C database with its own data partition. Switch accounts via `is_current=1` flag. All DAOs filter by current account automatically.

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
All data stored locally in Room. Documents created offline marked with `isSent=0`. Sync uploads unsent documents and downloads catalog updates. Timestamp-based change tracking ensures data consistency.

#### Document-Content Pattern
Orders use header-lines structure: Order (header) + OrderContent (lines). Cascade delete operations. Totals calculated via DAO aggregations with real-time Flow<DocumentTotals>.

#### Location Tracking
Foreground service (LocationUpdatesService) continuously tracks GPS at 10-second intervals. Filtering by accuracy threshold and minimum distance. History stored in LocationHistory table. Addresses resolved via GeocodeHelper (Geocoding API).

**Location Constants:**
- LOCATION_MIN_ACCURACY: Max acceptable GPS error
- LOCATION_MIN_DISTANCE: Min distance between tracked points

#### Fiscal Receipt Integration
Checkbox PRRO system integration for Ukrainian fiscal compliance. Order marked with `isFiscal=1` triggers receipt creation. Flow: Order saved → FiscalFragment → Checkbox API (cashier login, shift open, receipt create) → Store fiscal number.

**Fiscal Options:** Provider ID, Device ID, Cashier PIN stored in UserAccount.options.

#### User Options System
Server-controlled feature flags in UserAccount.options JSON. Parsed to UserOptions data class. Controls UI visibility and features: locations tracking, image loading, company/store selection, fiscal provider, etc.

#### SharedViewModel Pattern
Cross-fragment state sharing for current UserAccount, document GUID for selections, price type, company/store, barcode scans, progress messages. Provides Glide integration with auth headers and action callbacks for client/product selection.

#### Barcode Scanner Integration
Hardware scanner input captured in MainActivity.dispatchKeyEvent(). Time-based keystroke grouping (60ms threshold). Barcode broadcasts via SharedViewModel. ViewModels react to scans for product lookup.

#### Image Handling
Glide with custom auth headers. Local cache for captured images. Base64 encoding for upload. `isLocal=1` flag indicates not yet synced. Automatic cleanup after successful sync.

## Project Structure

```
/agentventa
├── MainActivity.kt                 # Single activity with barcode scanner dispatch
├── AgentApplication.kt             # Hilt entry point
├── /di/                            # Hilt dependency injection modules
│   ├── GlobalModule                # Singleton components
│   ├── NetworkModule               # HTTP layer
│   ├── RepositoryModule            # ViewModelComponent repositories
│   ├── DomainModule                # Business logic
│   └── PrintModule                 # Printer dependencies
├── /dao/                           # Room DAOs + AppDatabase
│   ├── AppDatabase.kt              # Main database (v20)
│   ├── /entity/                    # Room entities with db_guid
│   ├── /impl/                      # Repository implementations
│   └── /cloud/                     # Network data models
├── /repository/                    # Repository interfaces
├── /http/                          # Network layer
│   ├── NetworkRepositoryImpl       # Sync orchestration
│   └── HttpClientApi               # Retrofit service
├── /documents/                     # Document management screens
│   ├── /order/                     # Order creation/editing
│   ├── /cash/                      # Cash receipts
│   └── /task/                      # Task documents
├── /catalogs/                      # Catalog browsing screens
│   ├── /client/                    # Client management
│   ├── /product/                   # Product catalog
│   ├── /map/                       # Client map visualization
│   ├── /debt/                      # Debt details
│   └── /locations/                 # Location picker
├── /shared/                        # SharedViewModel for cross-fragment state
├── /settings/                      # Settings screens
│   ├── SyncFragment                # Data synchronization UI
│   └── UserAccountFragment         # Account configuration
├── /fiscal/                        # Fiscal integration
│   └── /checkbox/                  # Checkbox PRRO implementation
├── /geo/                           # Location tracking
│   ├── LocationUpdatesService      # Foreground GPS service
│   └── LocationRepositoryImpl      # Location data persistence
├── /camera/                        # CameraX photo capture
├── /printer/                       # Bluetooth printing
├── /logger/                        # Logging infrastructure
├── /license/                       # License management
├── /extensions/                    # Kotlin extension functions
└── /utility/                       # Utilities and constants
```

## Development Conventions

### Threading
- Coroutines with Dispatchers.IO for database and network operations
- Dispatchers.Main for UI updates
- ViewModelScope for ViewModel-scoped coroutines

### State Management
- LiveData for UI state (one-time values)
- Flow for reactive data streams (continuous updates)
- MutableLiveData for UI events

### Error Handling
- Logger interface injected via Hilt
- LogDao for persistent logs
- Firebase Crashlytics for production crashes
- User ID tracking for support

### Database Queries
- Flow-based queries for real-time UI updates
- @Transaction for atomic operations
- Suspend functions for one-shot queries
- JOIN queries for complex data (e.g., Order with Client, Company)

### Code Style
- Kotlin coroutines preferred over RxJava
- Data classes for models
- Sealed classes for Result types (Success, Error, Progress)
- Extension functions in `/extensions/` for reusable logic

## Critical Implementation Notes

### When Adding New Document Types
1. Create entity with `db_guid` composite key
2. Create DAO with current account filtering
3. Implement DocumentRepository<T> interface
4. Add to RepositoryModule with ViewModelComponent scope
5. Update DataExchangeRepository for sync transformation
6. Add sync type to NetworkRepository
7. Update server API endpoint handling

### When Modifying Database Schema
1. Increment AppDatabase version
2. Create MIGRATION_X_Y in AppDatabase
3. Test migration on existing data
4. Update schema export in `app/schemas/`

### When Adding New Sync Data Types
1. Add type to NetworkRepository.updateAll()
2. Create cloud model in `/dao/cloud/`
3. Add transformation in DataExchangeRepository
4. Update HttpClientApi endpoint
5. Add progress reporting

### Security Considerations
- Never commit google-services.json with production credentials
- API credentials stored in UserAccount (encrypted database)
- ProGuard rules in app/proguard-rules.pro for release builds
- local.properties excluded from git (SDK paths, keys)

### Testing
Demo mode available for evaluation:
- Server address: "demo"
- Database name: "demo"
- Connects to test 1C instance at hoot.com.ua/simple

## Key Dependencies

- **Kotlin**: 2.2.0
- **Hilt (Dagger)**: 2.57
- **Room**: 2.7.2
- **Retrofit**: 3.0.0
- **Navigation Component**: 2.9.3
- **CameraX**: 1.4.2
- **Firebase BOM**: 34.1.0 (Messaging, Crashlytics, Firestore, Auth)
- **Glide**: 4.16.0
- **Google Play Services**: Maps 19.2.0, Location 21.3.0

## Useful File Locations

- **Constants**: `/utility/Constants.kt`
- **Extensions**: `/extensions/` package
- **Navigation graph**: `/res/navigation/navigation.xml`
- **Version config**: `app/version.properties`
- **Database migrations**: AppDatabase.kt (MIGRATION_X_Y)
- **ProGuard rules**: `app/proguard-rules.pro`
- **Gradle properties**: `gradle.properties`, `local.properties`
