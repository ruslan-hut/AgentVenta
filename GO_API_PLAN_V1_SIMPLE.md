# AgentVenta Go API Server - Simplified V1 Implementation Plan

## Executive Summary

This is a simplified, pragmatic first version of the Go backend API server to replace the current 1C HTTP Data Exchange interface. The focus is on **minimal viable implementation** while preserving the Android app's existing architecture and maintaining the ability to scale later.

### Key Simplifications for V1:

1. **Single Server Deployment**: Ubuntu server with monolithic application
2. **Single Database**: PostgreSQL only (no Redis, no message queues initially)
3. **Simple HTTP Framework**: Chi router (lightweight, standard library-based)
4. **Built-in Logging**: Standard library `slog` for structured logging
5. **Reverse Integration**: 1C system pushes data updates to our server (not pull)
6. **No Multi-Tenancy (Yet)**: Single database with `company_guid` filtering
7. **Firebase Kept Initially**: Keep FCM/Crashlytics until Phase 2
8. **Focus on Data Bridge**: Primary goal is to replicate existing 1C HTTP exchange API

---

## 1. Architecture Overview

### System Design (Simplified V1)

```
┌─────────────────────────────────────────────────────────────────┐
│                      Android Application                         │
│  (AgentVenta - Offline-First with Room Database)                │
│         NO CHANGES REQUIRED IN V1                                │
└───────────────┬─────────────────────────────────────────────────┘
                │
                │ HTTPS/REST (same endpoints as 1C)
                │
┌───────────────▼─────────────────────────────────────────────────┐
│                    Go API Server (Monolith)                      │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │ Chi Router                                               │   │
│  │  - HTTP Handlers                                         │   │
│  │  - Basic Auth Middleware                                 │   │
│  │  - Request Logging (slog)                                │   │
│  └────────┬────────────────────────────────────────────────┘   │
│           │                                                      │
│  ┌────────▼────────────────────────────────────────────────┐   │
│  │ Service Layer                                            │   │
│  │  - UserService        - SyncService                      │   │
│  │  - DocumentService    - CatalogService                   │   │
│  │  - LicenseService                                        │   │
│  └────────┬────────────────────────────────────────────────┘   │
│           │                                                      │
│  ┌────────▼────────────────────────────────────────────────┐   │
│  │ Repository Layer (database/sql + pgx)                    │   │
│  │  - PostgreSQL (all data)                                 │   │
│  │  - Local file storage (images - initially)               │   │
│  └──────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────┘
                │
                │ (Webhook - 1C pushes updates)
                │
    ┌───────────▼───────────┐
    │  1C System (Legacy)   │
    │  Sends data updates   │
    │  via HTTP POST        │
    └───────────────────────┘
```

### Key Principles (V1)

1. **Drop-in Replacement**: API matches existing 1C endpoints exactly
2. **Zero Android Changes**: Android app works without modification
3. **Simple Deployment**: Single binary + PostgreSQL on Ubuntu
4. **Gradual Migration**: Can run in parallel with 1C system
5. **Growth Path**: Code structured for future enhancements (multi-tenancy, caching, etc.)

---

## 2. Technology Stack (Simplified)

### Core Framework
- **HTTP Framework**: Chi v5 (lightweight, stdlib-based, good performance)
- **Language**: Go 1.22+
- **Configuration**: Environment variables + simple config file

### Database
- **Primary DB**: PostgreSQL 16+ (single database, all tables in `public` schema)
- **No Redis** (V1): Session tokens stored in PostgreSQL
- **No Caching** (V1): Direct database queries (optimize later if needed)

### Storage
- **Files**: Local filesystem `/var/agentventa/uploads/` (initially)
- **Future**: MinIO or S3 migration path planned

### No Message Queues (V1)
- Synchronous processing for all operations
- Background tasks via simple goroutines + PostgreSQL job table

### Authentication
- **Tokens**: JWT (HS256 - symmetric key)
- **Password**: bcrypt
- **Basic Auth**: Replicate 1C HTTP Basic Auth for compatibility

### Observability
- **Logging**: `slog` (structured JSON logs)
- **Metrics**: Simple HTTP endpoint `/metrics` (Prometheus format - future)
- **Error Tracking**: File logs initially, Sentry in Phase 2

### DevOps
- **Containerization**: Single Docker container (optional)
- **Deployment**: SystemD service on Ubuntu
- **Migrations**: golang-migrate
- **Build**: Simple Makefile

---

## 3. Database Schema (Simplified - Single Database)

### PostgreSQL Schema Design (V1)

**Approach**: Single `public` schema with `company_guid` filtering in all queries.

**Why Simplified?**
- Easier to manage initially
- Faster development
- Migration to multi-tenant schemas is straightforward later
- PostgreSQL handles millions of rows efficiently with proper indexing

---

### Core Tables

#### `user_accounts`
Maps to Android `UserAccount` entity.

```sql
CREATE TABLE user_accounts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    guid VARCHAR(36) UNIQUE NOT NULL,
    company_guid VARCHAR(36) NOT NULL,           -- Tenant identifier

    -- Authentication
    username VARCHAR(100) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    api_token VARCHAR(255),                      -- Current JWT token (for revocation)
    token_expires_at TIMESTAMPTZ,

    -- User Details
    email VARCHAR(255),
    phone VARCHAR(50),
    full_name VARCHAR(255),
    description TEXT,

    -- License
    license_key VARCHAR(100),
    license_expires_at TIMESTAMPTZ,

    -- Settings (JSON)
    options JSONB DEFAULT '{}'::jsonb,

    -- Status
    is_active BOOLEAN DEFAULT true,
    is_demo BOOLEAN DEFAULT false,
    last_login_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW(),

    CONSTRAINT unique_username_company UNIQUE(company_guid, username)
);

CREATE INDEX idx_user_accounts_guid ON user_accounts(guid);
CREATE INDEX idx_user_accounts_company ON user_accounts(company_guid);
CREATE INDEX idx_user_accounts_token ON user_accounts(api_token);
CREATE INDEX idx_user_accounts_license ON user_accounts(license_key);
```

---

#### `companies`
Minimal company registry (for future multi-tenancy).

```sql
CREATE TABLE companies (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    guid VARCHAR(36) UNIQUE NOT NULL,
    name VARCHAR(255) NOT NULL,
    database_name VARCHAR(100) NOT NULL,         -- Android dbName
    is_active BOOLEAN DEFAULT true,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX idx_companies_guid ON companies(guid);
```

---

#### `clients`
Client catalog (within each company).

```sql
CREATE TABLE clients (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    guid VARCHAR(36) NOT NULL,
    company_guid VARCHAR(36) NOT NULL,           -- Tenant filter

    -- Basic Info
    code1 VARCHAR(50),
    code2 VARCHAR(50),
    description VARCHAR(255) NOT NULL,
    description_lc VARCHAR(255),                 -- Lowercase for search
    notes TEXT,
    phone VARCHAR(50),
    address TEXT,

    -- Financial
    discount NUMERIC(5,2) DEFAULT 0.00,
    bonus NUMERIC(12,2) DEFAULT 0.00,
    price_type VARCHAR(50),

    -- Status
    is_banned BOOLEAN DEFAULT false,
    ban_message TEXT,
    is_active BOOLEAN DEFAULT true,
    is_group BOOLEAN DEFAULT false,

    -- Hierarchy
    group_guid VARCHAR(36),

    -- Sync Metadata
    timestamp BIGINT NOT NULL,                   -- Unix timestamp (ms) - for sync
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW(),

    CONSTRAINT unique_client_company UNIQUE(company_guid, guid)
);

CREATE INDEX idx_clients_company_guid ON clients(company_guid, guid);
CREATE INDEX idx_clients_group ON clients(company_guid, group_guid);
CREATE INDEX idx_clients_description_lc ON clients(description_lc);
CREATE INDEX idx_clients_timestamp ON clients(company_guid, timestamp);
```

---

#### `products`

```sql
CREATE TABLE products (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    guid VARCHAR(36) NOT NULL,
    company_guid VARCHAR(36) NOT NULL,

    -- Codes & Identifiers
    code1 VARCHAR(50),
    code2 VARCHAR(50),
    vendor_code VARCHAR(50),
    barcode VARCHAR(50),

    -- Description
    description VARCHAR(255) NOT NULL,
    description_lc VARCHAR(255),
    vendor_status VARCHAR(100),
    status VARCHAR(100),

    -- Pricing
    price NUMERIC(12,2) DEFAULT 0.00,
    min_price NUMERIC(12,2) DEFAULT 0.00,
    base_price NUMERIC(12,2) DEFAULT 0.00,

    -- Inventory
    quantity NUMERIC(12,3) DEFAULT 0.000,
    weight NUMERIC(12,3) DEFAULT 0.000,
    unit VARCHAR(20),

    -- Packaging
    package_only BOOLEAN DEFAULT false,
    package_value NUMERIC(12,3) DEFAULT 1.000,
    indivisible BOOLEAN DEFAULT false,

    -- Organization
    sorting INT DEFAULT 0,
    is_active BOOLEAN DEFAULT true,
    is_group BOOLEAN DEFAULT false,
    group_guid VARCHAR(36),

    -- Sync Metadata
    timestamp BIGINT NOT NULL,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW(),

    CONSTRAINT unique_product_company UNIQUE(company_guid, guid)
);

CREATE INDEX idx_products_company_guid ON products(company_guid, guid);
CREATE INDEX idx_products_barcode ON products(company_guid, barcode);
CREATE INDEX idx_products_group ON products(company_guid, group_guid);
CREATE INDEX idx_products_description_lc ON products(description_lc);
CREATE INDEX idx_products_timestamp ON products(company_guid, timestamp);
```

---

#### `orders`

```sql
CREATE TABLE orders (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    guid VARCHAR(36) NOT NULL,
    company_guid VARCHAR(36) NOT NULL,

    -- Document Info
    date DATE NOT NULL,
    time BIGINT NOT NULL,                        -- Unix timestamp (ms)
    time_saved BIGINT,
    number INT NOT NULL,

    -- References
    company_entity_guid VARCHAR(36),             -- Company within account (if multi-company)
    company_name VARCHAR(255),
    store_guid VARCHAR(36),
    store_name VARCHAR(255),
    client_guid VARCHAR(36),
    client_code VARCHAR(50),
    client_description VARCHAR(255),

    -- Delivery
    delivery_date DATE,
    notes TEXT,

    -- Pricing
    price_type VARCHAR(50),
    payment_type VARCHAR(50),
    discount NUMERIC(5,2) DEFAULT 0.00,

    -- Totals
    total_price NUMERIC(12,2) DEFAULT 0.00,
    total_quantity NUMERIC(12,3) DEFAULT 0.000,
    total_weight NUMERIC(12,3) DEFAULT 0.000,
    discount_value NUMERIC(12,2) DEFAULT 0.00,

    -- Location
    latitude NUMERIC(10,7),
    longitude NUMERIC(10,7),
    location_time BIGINT,

    -- Flags
    is_fiscal BOOLEAN DEFAULT false,
    is_return BOOLEAN DEFAULT false,
    is_processed BOOLEAN DEFAULT false,
    is_sent BOOLEAN DEFAULT false,

    -- Status
    status VARCHAR(50) DEFAULT 'draft',

    -- Metadata
    created_by UUID REFERENCES user_accounts(id),
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW(),

    CONSTRAINT unique_order_company UNIQUE(company_guid, guid)
);

CREATE INDEX idx_orders_company_guid ON orders(company_guid, guid);
CREATE INDEX idx_orders_client ON orders(company_guid, client_guid);
CREATE INDEX idx_orders_date ON orders(company_guid, date DESC);
CREATE INDEX idx_orders_is_sent ON orders(company_guid, is_sent) WHERE is_sent = false;
```

---

#### `order_contents`

```sql
CREATE TABLE order_contents (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_guid VARCHAR(36) NOT NULL,
    company_guid VARCHAR(36) NOT NULL,
    product_guid VARCHAR(36) NOT NULL,

    -- Line Details
    unit_code VARCHAR(20),
    quantity NUMERIC(12,3) NOT NULL,
    weight NUMERIC(12,3) DEFAULT 0.000,
    price NUMERIC(12,2) NOT NULL,
    sum NUMERIC(12,2) NOT NULL,
    discount NUMERIC(12,2) DEFAULT 0.00,

    -- Flags
    is_demand BOOLEAN DEFAULT false,
    is_packed BOOLEAN DEFAULT false,

    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX idx_order_contents_order ON order_contents(company_guid, order_guid);
CREATE INDEX idx_order_contents_product ON order_contents(company_guid, product_guid);
```

---

#### Other Essential Tables

**cash_receipts**, **tasks**, **debts**, **rests** (stock levels), **product_prices**, **payment_types**, **price_types**, **stores**, **client_locations**, **client_images**, **product_images** - all follow same pattern with `company_guid` filtering.

(Full schema omitted for brevity - follow same pattern as above)

---

## 4. API Endpoints (Matching 1C Interface)

### Goal: 100% Backward Compatibility with Android App

The Android app currently uses these endpoints from 1C:

1. `GET /hs/dex/check/{token}` - Token validation
2. `GET /hs/dex/get/{type}/{token}{more}` - Data download
3. `POST /hs/dex/post/{token}` - Document upload
4. `GET /hs/dex/document/{type}/{guid}/{token}` - Document content
5. `GET /hs/dex/print/{guid}` - PDF receipt

**V1 Implementation**: Replicate these endpoints exactly.

---

### 4.1 Authentication Endpoints

#### `GET /hs/dex/check/{token}`
Validate user credentials and return token (or refresh existing token).

**Request Headers:**
```
Authorization: Basic base64(username:password)
```

**URL Parameters:**
- `{token}`: Current token (if any) - empty string `---` if first login

**Response (Success):**
```json
{
  "token": "new-jwt-token-abc123",
  "user": {
    "guid": "user-guid-123",
    "username": "agent123",
    "description": "John Doe",
    "options": {
      "write": true,
      "useCompanies": true,
      "useStores": true,
      "clientsLocations": true,
      "loadImages": true,
      "fiscalProvider": "checkbox",
      "fiscalProviderId": "provider-guid",
      "fiscalDeviceId": "device-id"
    }
  }
}
```

**Response (Error):**
```json
{
  "error": "Invalid credentials"
}
```

**HTTP Status:**
- 200 OK: Valid credentials, token returned
- 401 Unauthorized: Invalid credentials
- 403 Forbidden: Account disabled

---

### 4.2 Data Synchronization (Download)

#### `GET /hs/dex/get/{type}/{token}{more}`
Download catalog data (full or differential sync).

**URL Parameters:**
- `{type}`: Data type (clients, goods, debts, payment_types, companies, stores, rests, images, etc.)
- `{token}`: JWT authentication token
- `{more}`: Pagination cursor (empty if first page, e.g., `/clients/token-abc/page-2`)

**Query Parameters:**
- `timestamp`: Last sync timestamp (Unix ms) - for differential sync

**Example Requests:**
```
GET /hs/dex/get/clients/token-abc
GET /hs/dex/get/goods/token-abc?timestamp=1234567890000
GET /hs/dex/get/clients/token-abc/page-2
```

**Response Format:**
```json
{
  "type": "clients",
  "items": [
    {
      "guid": "client-guid-1",
      "code1": "C001",
      "code2": "CLIENT001",
      "description": "Client ABC",
      "phone": "+380501234567",
      "address": "Kyiv, Shevchenko St, 1",
      "discount": 5.0,
      "bonus": 150.50,
      "price_type": "retail",
      "is_banned": false,
      "is_active": true,
      "is_group": false,
      "group_guid": "group-guid-1",
      "timestamp": 1234567890000
    },
    // ... more items
  ],
  "has_more": true,
  "next_page": "page-2"
}
```

**Sync Types:**
- `clients` - Client catalog
- `goods` - Product catalog (called "goods" in 1C terminology)
- `debts` - Client debt records
- `payment_types` - Payment method dictionary
- `companies` - Company entities (if multi-company)
- `stores` - Warehouse/store entities
- `rests` - Stock levels (inventory)
- `images` - Product/client images (URLs)
- `price_types` - Price type dictionary
- `product_prices` - Product prices by type

---

### 4.3 Data Synchronization (Upload)

#### `POST /hs/dex/post/{token}`
Upload documents created offline (orders, cash receipts, images, locations).

**URL Parameters:**
- `{token}`: JWT authentication token

**Request Body:**
```json
{
  "orders": [
    {
      "guid": "order-guid-1",
      "date": "2025-01-15",
      "time": 1234567890000,
      "client_guid": "client-guid-1",
      "company_guid": "company-entity-guid-1",
      "store_guid": "store-guid-1",
      "price_type": "retail",
      "payment_type": "CASH",
      "total_price": 1500.00,
      "is_fiscal": true,
      "items": [
        {
          "product_guid": "product-guid-1",
          "quantity": 2.0,
          "price": 750.00,
          "sum": 1500.00
        }
      ]
    }
  ],
  "cash": [
    {
      "guid": "cash-guid-1",
      "date": "2025-01-15",
      "time": 1234567890000,
      "client_guid": "client-guid-1",
      "company_guid": "company-entity-guid-1",
      "sum": 1000.00,
      "is_fiscal": true
    }
  ],
  "client_images": [
    {
      "client_guid": "client-guid-1",
      "guid": "image-guid-1",
      "url": "data:image/jpeg;base64,/9j/4AAQSkZJRg...",
      "description": "Store front",
      "is_default": true,
      "timestamp": 1234567890000
    }
  ],
  "client_locations": [
    {
      "client_guid": "client-guid-1",
      "latitude": 50.4501,
      "longitude": 30.5234,
      "address": "Kyiv, Shevchenko St, 1"
    }
  ]
}
```

**Response:**
```json
{
  "success": true,
  "results": {
    "orders": [
      {
        "guid": "order-guid-1",
        "status": "ok",
        "number": 123,
        "is_processed": true
      }
    ],
    "cash": [
      {
        "guid": "cash-guid-1",
        "status": "ok",
        "number": 456
      }
    ],
    "client_images": [
      {
        "guid": "image-guid-1",
        "status": "ok",
        "url": "http://api.agentventa.com/uploads/images/client-guid-1/image-guid-1.jpg"
      }
    ],
    "client_locations": [
      {
        "client_guid": "client-guid-1",
        "status": "ok"
      }
    ]
  }
}
```

---

### 4.4 Document Content Retrieval

#### `GET /hs/dex/document/{type}/{guid}/{token}`
Get detailed document content (order with line items, debt details, etc.).

**URL Parameters:**
- `{type}`: Document type (order, cash, debt)
- `{guid}`: Document GUID
- `{token}`: JWT authentication token

**Example:**
```
GET /hs/dex/document/order/order-guid-1/token-abc
```

**Response:**
```json
{
  "guid": "order-guid-1",
  "date": "2025-01-15",
  "time": 1234567890000,
  "number": 123,
  "client": {
    "guid": "client-guid-1",
    "code": "C001",
    "description": "Client ABC"
  },
  "company": {
    "guid": "company-guid-1",
    "description": "Main Company"
  },
  "store": {
    "guid": "store-guid-1",
    "description": "Warehouse #1"
  },
  "total_price": 1500.00,
  "items": [
    {
      "product_guid": "product-guid-1",
      "product_code": "P001",
      "product_description": "Laptop Dell XPS 15",
      "quantity": 2.0,
      "price": 750.00,
      "sum": 1500.00
    }
  ]
}
```

---

### 4.5 Receipt PDF Generation

#### `GET /hs/dex/print/{guid}`
Generate PDF receipt for order or cash document.

**URL Parameters:**
- `{guid}`: Order or cash receipt GUID

**Response:**
- Content-Type: `application/pdf`
- Binary PDF data

**Use Case:** Used by Android app for Bluetooth printer output and user preview.

---

## 5. 1C Integration (Reverse Push)

### Webhook from 1C → Go API

Instead of Go API pulling data from 1C, **1C pushes updates** when catalog changes occur.

#### `POST /api/v1/1c/webhook/catalog-update`
Receive catalog updates from 1C system.

**Authentication:** API Key (shared secret between 1C and Go API)

**Request Headers:**
```
X-API-Key: shared-secret-key-123
Content-Type: application/json
```

**Request Body:**
```json
{
  "company_guid": "company-guid-abc",
  "update_type": "clients",
  "timestamp": 1234567890000,
  "items": [
    {
      "guid": "client-guid-1",
      "code1": "C001",
      "description": "Client ABC",
      // ... full client data
      "timestamp": 1234567890000
    },
    // ... more items
  ]
}
```

**Response:**
```json
{
  "success": true,
  "message": "Received 150 clients",
  "processed": 150,
  "errors": 0
}
```

**Update Types:**
- `clients` - Client catalog update
- `products` - Product catalog update (called "goods" in 1C)
- `debts` - Debt records update
- `rests` - Stock levels update
- `prices` - Price updates

**1C Configuration:**
1C administrator configures webhook URL in 1C settings.
When catalog is updated (manual or scheduled), 1C HTTP-service sends POST request to Go API.

---

## 6. Go Application Structure

### Project Layout

```
/agentventa-api
├── cmd/
│   └── server/
│       └── main.go                    # Entry point
├── internal/
│   ├── config/
│   │   └── config.go                  # Configuration loading
│   ├── handler/
│   │   ├── auth.go                    # Authentication handlers
│   │   ├── sync.go                    # Sync endpoints
│   │   ├── document.go                # Document handlers
│   │   └── webhook.go                 # 1C webhook handlers
│   ├── service/
│   │   ├── user_service.go            # User business logic
│   │   ├── sync_service.go            # Sync orchestration
│   │   ├── document_service.go        # Document processing
│   │   ├── catalog_service.go         # Catalog management
│   │   └── license_service.go         # License validation
│   ├── repository/
│   │   ├── user_repository.go         # User data access
│   │   ├── client_repository.go       # Client data access
│   │   ├── product_repository.go      # Product data access
│   │   ├── order_repository.go        # Order data access
│   │   └── ...
│   ├── model/
│   │   ├── user.go                    # User domain model
│   │   ├── client.go                  # Client domain model
│   │   ├── product.go                 # Product domain model
│   │   ├── order.go                   # Order domain model
│   │   └── ...
│   ├── middleware/
│   │   ├── auth.go                    # JWT authentication
│   │   ├── logging.go                 # Request logging
│   │   └── company_context.go         # Company filtering
│   ├── database/
│   │   ├── db.go                      # Database connection
│   │   └── migrations/                # SQL migrations
│   └── util/
│       ├── jwt.go                     # JWT utilities
│       ├── hash.go                    # Password hashing
│       └── response.go                # HTTP response helpers
├── migrations/
│   ├── 001_initial_schema.up.sql
│   ├── 001_initial_schema.down.sql
│   └── ...
├── go.mod
├── go.sum
├── Makefile
├── Dockerfile
└── README.md
```

---

### Key Code Examples

#### `cmd/server/main.go`

```go
package main

import (
    "context"
    "log/slog"
    "net/http"
    "os"
    "os/signal"
    "syscall"
    "time"

    "agentventa-api/internal/config"
    "agentventa-api/internal/database"
    "agentventa-api/internal/handler"
    "agentventa-api/internal/middleware"
    "agentventa-api/internal/service"
    "agentventa-api/internal/repository"

    "github.com/go-chi/chi/v5"
    chimiddleware "github.com/go-chi/chi/v5/middleware"
)

func main() {
    // Initialize structured logger
    logger := slog.New(slog.NewJSONHandler(os.Stdout, nil))
    slog.SetDefault(logger)

    // Load configuration
    cfg, err := config.Load()
    if err != nil {
        slog.Error("Failed to load config", "error", err)
        os.Exit(1)
    }

    // Connect to database
    db, err := database.Connect(cfg.DatabaseURL)
    if err != nil {
        slog.Error("Failed to connect to database", "error", err)
        os.Exit(1)
    }
    defer db.Close()

    // Run migrations
    if err := database.Migrate(db); err != nil {
        slog.Error("Failed to run migrations", "error", err)
        os.Exit(1)
    }

    // Initialize repositories
    userRepo := repository.NewUserRepository(db)
    clientRepo := repository.NewClientRepository(db)
    productRepo := repository.NewProductRepository(db)
    orderRepo := repository.NewOrderRepository(db)

    // Initialize services
    userService := service.NewUserService(userRepo, cfg)
    syncService := service.NewSyncService(clientRepo, productRepo, orderRepo, cfg)
    documentService := service.NewDocumentService(orderRepo, cfg)

    // Initialize handlers
    authHandler := handler.NewAuthHandler(userService)
    syncHandler := handler.NewSyncHandler(syncService)
    documentHandler := handler.NewDocumentHandler(documentService)

    // Setup router
    r := chi.NewRouter()

    // Middleware
    r.Use(chimiddleware.RequestID)
    r.Use(chimiddleware.RealIP)
    r.Use(middleware.Logger)
    r.Use(chimiddleware.Recoverer)
    r.Use(chimiddleware.Timeout(60 * time.Second))

    // Health check
    r.Get("/health", func(w http.ResponseWriter, r *http.Request) {
        w.Write([]byte("OK"))
    })

    // 1C-compatible endpoints (no auth middleware - uses Basic Auth in handlers)
    r.Get("/hs/dex/check/{token}", authHandler.CheckToken)
    r.Get("/hs/dex/get/{type}/{token}", syncHandler.GetData)
    r.Get("/hs/dex/get/{type}/{token}/{more}", syncHandler.GetDataPaginated)
    r.Post("/hs/dex/post/{token}", syncHandler.PostData)
    r.Get("/hs/dex/document/{type}/{guid}/{token}", documentHandler.GetDocument)
    r.Get("/hs/dex/print/{guid}", documentHandler.PrintDocument)

    // 1C webhook endpoints (API key authentication)
    r.Route("/api/v1/1c", func(r chi.Router) {
        r.Use(middleware.APIKeyAuth(cfg.WebhookAPIKey))
        r.Post("/webhook/catalog-update", syncHandler.CatalogUpdateWebhook)
    })

    // Start server
    srv := &http.Server{
        Addr:    ":" + cfg.Port,
        Handler: r,
    }

    // Graceful shutdown
    go func() {
        slog.Info("Starting server", "port", cfg.Port)
        if err := srv.ListenAndServe(); err != nil && err != http.ErrServerClosed {
            slog.Error("Server failed", "error", err)
            os.Exit(1)
        }
    }()

    // Wait for interrupt signal
    quit := make(chan os.Signal, 1)
    signal.Notify(quit, syscall.SIGINT, syscall.SIGTERM)
    <-quit

    slog.Info("Shutting down server...")

    ctx, cancel := context.WithTimeout(context.Background(), 30*time.Second)
    defer cancel()

    if err := srv.Shutdown(ctx); err != nil {
        slog.Error("Server forced to shutdown", "error", err)
    }

    slog.Info("Server exited")
}
```

---

#### Example Handler: `internal/handler/auth.go`

```go
package handler

import (
    "encoding/base64"
    "net/http"
    "strings"
    "log/slog"

    "agentventa-api/internal/service"
    "agentventa-api/internal/util"

    "github.com/go-chi/chi/v5"
)

type AuthHandler struct {
    userService *service.UserService
}

func NewAuthHandler(userService *service.UserService) *AuthHandler {
    return &AuthHandler{userService: userService}
}

// CheckToken validates credentials and returns JWT token
// GET /hs/dex/check/{token}
func (h *AuthHandler) CheckToken(w http.ResponseWriter, r *http.Request) {
    token := chi.URLParam(r, "token")

    // Extract Basic Auth credentials
    authHeader := r.Header.Get("Authorization")
    if !strings.HasPrefix(authHeader, "Basic ") {
        util.ErrorResponse(w, http.StatusUnauthorized, "Missing Basic Auth")
        return
    }

    // Decode Base64 credentials
    payload, err := base64.StdEncoding.DecodeString(authHeader[6:])
    if err != nil {
        util.ErrorResponse(w, http.StatusUnauthorized, "Invalid Basic Auth")
        return
    }

    // Parse username:password
    credentials := strings.SplitN(string(payload), ":", 2)
    if len(credentials) != 2 {
        util.ErrorResponse(w, http.StatusUnauthorized, "Invalid credentials format")
        return
    }

    username := credentials[0]
    password := credentials[1]

    // If token provided (not "---"), validate it first
    if token != "" && token != "---" {
        if h.userService.ValidateToken(r.Context(), token) {
            // Token still valid, return existing user data
            user, err := h.userService.GetUserByToken(r.Context(), token)
            if err == nil {
                slog.Info("Token validated", "username", user.Username)
                util.JSONResponse(w, http.StatusOK, map[string]interface{}{
                    "token": token,
                    "user": map[string]interface{}{
                        "guid": user.GUID,
                        "username": user.Username,
                        "description": user.FullName,
                        "options": user.Options,
                    },
                })
                return
            }
        }
    }

    // Authenticate with username/password
    user, newToken, err := h.userService.Authenticate(r.Context(), username, password)
    if err != nil {
        slog.Warn("Authentication failed", "username", username, "error", err)
        util.ErrorResponse(w, http.StatusUnauthorized, "Invalid credentials")
        return
    }

    slog.Info("User authenticated", "username", username, "user_guid", user.GUID)

    util.JSONResponse(w, http.StatusOK, map[string]interface{}{
        "token": newToken,
        "user": map[string]interface{}{
            "guid": user.GUID,
            "username": user.Username,
            "description": user.FullName,
            "options": user.Options,
        },
    })
}
```

---

## 7. Deployment (Ubuntu Server)

### Simple SystemD Service Deployment

#### Prerequisites
- Ubuntu 22.04 LTS server
- PostgreSQL 16 installed
- Domain name pointed to server (e.g., api.agentventa.com)
- SSL certificate (Let's Encrypt)

#### Installation Steps

**1. Install PostgreSQL**
```bash
sudo apt update
sudo apt install postgresql-16 postgresql-contrib
sudo systemctl start postgresql
sudo systemctl enable postgresql

# Create database
sudo -u postgres psql
CREATE DATABASE agentventa;
CREATE USER agentventa WITH PASSWORD 'secure-password';
GRANT ALL PRIVILEGES ON DATABASE agentventa TO agentventa;
\q
```

**2. Build Go Application**
```bash
# On development machine
make build-linux

# Transfer binary to server
scp ./bin/agentventa-api-linux server@api.agentventa.com:/opt/agentventa/
```

**3. Create SystemD Service**
```bash
sudo nano /etc/systemd/system/agentventa-api.service
```

```ini
[Unit]
Description=AgentVenta API Server
After=network.target postgresql.service
Requires=postgresql.service

[Service]
Type=simple
User=agentventa
Group=agentventa
WorkingDirectory=/opt/agentventa
ExecStart=/opt/agentventa/agentventa-api-linux
Restart=on-failure
RestartSec=5s

# Environment variables
Environment="PORT=8080"
Environment="DATABASE_URL=postgres://agentventa:secure-password@localhost:5432/agentventa?sslmode=disable"
Environment="JWT_SECRET=your-secret-key-change-this"
Environment="WEBHOOK_API_KEY=1c-webhook-secret-key"
Environment="UPLOAD_DIR=/var/agentventa/uploads"

# Logging
StandardOutput=journal
StandardError=journal
SyslogIdentifier=agentventa-api

# Security
NoNewPrivileges=true
PrivateTmp=true
ProtectSystem=strict
ProtectHome=true
ReadWritePaths=/var/agentventa

[Install]
WantedBy=multi-user.target
```

**4. Create User and Directories**
```bash
sudo useradd -r -s /bin/false agentventa
sudo mkdir -p /opt/agentventa
sudo mkdir -p /var/agentventa/uploads
sudo chown -R agentventa:agentventa /opt/agentventa
sudo chown -R agentventa:agentventa /var/agentventa
```

**5. Start Service**
```bash
sudo systemctl daemon-reload
sudo systemctl start agentventa-api
sudo systemctl enable agentventa-api
sudo systemctl status agentventa-api
```

**6. Nginx Reverse Proxy (with SSL)**
```bash
sudo apt install nginx certbot python3-certbot-nginx

sudo nano /etc/nginx/sites-available/agentventa-api
```

```nginx
server {
    listen 80;
    server_name api.agentventa.com;

    location / {
        proxy_pass http://localhost:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
}
```

```bash
sudo ln -s /etc/nginx/sites-available/agentventa-api /etc/nginx/sites-enabled/
sudo nginx -t
sudo systemctl restart nginx

# Get SSL certificate
sudo certbot --nginx -d api.agentventa.com
```

**7. Verify Deployment**
```bash
curl https://api.agentventa.com/health
# Expected: OK
```

---

## 8. Migration Roadmap (Simplified)

### Phase 1: Core Backend (Weeks 1-3)

**Goal:** Working API matching 1C endpoints.

**Tasks:**
1. Set up Go project structure
2. Implement database schema and migrations
3. Implement authentication (`/check/{token}`)
4. Implement catalog download (`/get/{type}/{token}`)
5. Implement document upload (`/post/{token}`)
6. Implement document retrieval (`/document/{type}/{guid}/{token}`)
7. Basic PDF generation (`/print/{guid}`)

**Deliverables:**
- Running Go API server
- PostgreSQL database with schema
- Integration tests for all endpoints
- Postman collection for testing

**Testing:** Use Android app in demo mode pointing to new API.

---

### Phase 2: 1C Integration (Week 4)

**Goal:** 1C system can push updates to Go API.

**Tasks:**
1. Implement webhook endpoints (`/api/v1/1c/webhook/*`)
2. Configure 1C HTTP service to send updates
3. Test bidirectional flow: 1C → Go API → Android app

**Deliverables:**
- 1C integration tested
- Catalog updates flow from 1C to Android via Go API
- Document uploads flow from Android to 1C via Go API

---

### Phase 3: Production Deployment (Week 5)

**Goal:** Live system for beta testing.

**Tasks:**
1. Deploy to Ubuntu server
2. Configure SSL
3. Set up database backups
4. Configure logging and monitoring
5. Load testing

**Deliverables:**
- Production API at `https://api.agentventa.com`
- Android app beta build using new API
- Rollback plan

---

### Phase 4: Android App Migration (Week 6)

**Goal:** Update Android app to use new API (if any changes needed).

**Tasks:**
1. Update `HttpClientApi` base URL (if changed)
2. Test all sync scenarios
3. Test offline→online sync
4. User acceptance testing

**Deliverables:**
- Production Android build
- Migration complete

---

### Future Enhancements (Phase 5+)

**Post-V1 Improvements:**
1. **Caching Layer**: Add Redis for catalog caching
2. **Multi-Tenancy**: Migrate to schema-per-tenant architecture
3. **WebSockets**: Real-time updates
4. **Message Queues**: Async processing with NATS/RabbitMQ
5. **Firebase Replacement**: Custom push notifications
6. **Advanced Monitoring**: Prometheus + Grafana
7. **CDN for Images**: MinIO + CloudFlare
8. **Horizontal Scaling**: Kubernetes deployment

---

## 9. Key Decisions & Trade-offs

### Why These Simplifications?

| Decision | Reason | Future Path |
|----------|--------|-------------|
| **No Redis** | Simpler deployment, PostgreSQL handles sessions | Add Redis when caching needed (performance) |
| **No Message Queues** | Synchronous processing sufficient initially | Add NATS when async jobs grow |
| **Single Database** | Easier migrations, fewer moving parts | Migrate to schemas when multi-tenancy needed |
| **Local File Storage** | No external dependencies | Migrate to S3/MinIO for scalability |
| **Chi Router** | Lightweight, stdlib-based, fast | Gin is alternative if richer middleware needed |
| **slog Logger** | Standard library, no deps | Add structured logging middleware if needed |
| **Keep Firebase** | Avoid Android app changes in V1 | Replace in V2 with custom push service |
| **1C Pushes Data** | Simpler than polling 1C | More real-time, less coupling |

---

## 10. Success Criteria

### V1 is Successful When:

1. ✅ Android app works without any code changes
2. ✅ All existing sync scenarios work (full sync, differential sync)
3. ✅ Document upload/download matches 1C behavior exactly
4. ✅ Performance is equal or better than 1C (< 2s for catalog sync)
5. ✅ 1C integration works (bidirectional data flow)
6. ✅ Zero data loss during migration
7. ✅ Can run in parallel with 1C (A/B testing)
8. ✅ Deployment is reproducible (scripts, docs)

---

## 11. Risks & Mitigations

| Risk | Impact | Mitigation |
|------|--------|-----------|
| **API incompatibility with Android app** | High | Extensive testing with existing app, Postman collection |
| **Data loss during migration** | Critical | Run parallel with 1C initially, extensive backups |
| **Performance issues** | Medium | Load testing before production, PostgreSQL tuning |
| **1C webhook failures** | Medium | Retry logic, error logging, fallback to polling |
| **Deployment complexity** | Low | Simple SystemD service, good documentation |

---

## Conclusion

This simplified V1 plan prioritizes:
- ✅ **Speed to market**: Weeks, not months
- ✅ **Minimal risk**: Drop-in replacement for 1C
- ✅ **No Android changes**: Zero app modifications
- ✅ **Simple deployment**: Single Ubuntu server
- ✅ **Future-proof**: Clear path to advanced features

**Next Steps:**
1. Review and approve this plan
2. Set up development environment
3. Begin Phase 1 implementation
4. Weekly progress reviews

**Estimated Timeline:** 6 weeks to production V1.
