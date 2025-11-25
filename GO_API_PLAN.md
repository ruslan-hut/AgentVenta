# AgentVenta Go API Server - Complete Implementation Plan

## Executive Summary

This document provides a comprehensive plan for building a Go-based backend API server to replace the current 1C HTTP Data Exchange (hs/dex) interface and Firebase services. The new server will provide:

1. **RESTful API** for all data synchronization operations
2. **Push Notification Service** replacing Firebase Cloud Messaging (FCM)
3. **Error Tracking & Analytics** replacing Firebase Crashlytics
4. **License Management** replacing the current HTTP-based license validation
5. **Multi-tenant Architecture** supporting multiple company databases
6. **Offline-First Sync** with conflict resolution
7. **Real-time Updates** via WebSockets
8. **Image & File Storage** with CDN integration
9. **Location Tracking** and geofencing capabilities
10. **Fiscal Receipt Integration** proxy for Checkbox PRRO API

---

## Table of Contents

1. [Architecture Overview](#architecture-overview)
2. [Technology Stack](#technology-stack)
3. [Database Schema](#database-schema)
4. [API Endpoints](#api-endpoints)
5. [Data Models](#data-models)
6. [Authentication & Authorization](#authentication--authorization)
7. [Firebase Replacement](#firebase-replacement)
8. [Sync Strategy](#sync-strategy)
9. [Migration Plan](#migration-plan)
10. [Security Considerations](#security-considerations)
11. [Performance & Scalability](#performance--scalability)
12. [Testing Strategy](#testing-strategy)

---

## 1. Architecture Overview

### System Design

```
┌─────────────────────────────────────────────────────────────────┐
│                      Android Application                         │
│  (AgentVenta - Offline-First with Room Database)                │
└───────────────┬─────────────────────────────────────────────────┘
                │
                │ HTTPS/REST + WebSocket
                │
┌───────────────▼─────────────────────────────────────────────────┐
│                    Go API Server (Primary)                       │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │ API Gateway (Gin/Echo)                                   │   │
│  │  - Authentication (JWT + API Keys)                       │   │
│  │  - Rate Limiting                                         │   │
│  │  - Request Validation                                    │   │
│  └────────┬────────────────────────────────────────────────┘   │
│           │                                                      │
│  ┌────────▼────────────────────────────────────────────────┐   │
│  │ Service Layer                                            │   │
│  │  - UserService        - SyncService                      │   │
│  │  - DocumentService    - NotificationService              │   │
│  │  - CatalogService     - AnalyticsService                 │   │
│  │  - LicenseService     - FiscalService                    │   │
│  └────────┬────────────────────────────────────────────────┘   │
│           │                                                      │
│  ┌────────▼────────────────────────────────────────────────┐   │
│  │ Repository Layer                                         │   │
│  │  - PostgreSQL (Primary Data)                             │   │
│  │  - Redis (Cache + Sessions + Queues)                     │   │
│  │  - S3/MinIO (File Storage)                               │   │
│  └──────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────┘
                │
                │
    ┌───────────┴───────────┬──────────────┬──────────────┐
    │                       │              │              │
┌───▼────┐         ┌────────▼────┐  ┌──────▼─────┐  ┌───▼────────┐
│  NATS  │         │  1C System  │  │  Checkbox  │  │   Email    │
│Message │         │  (Legacy)   │  │  PRRO API  │  │  Service   │
│ Queue  │         │  Optional   │  │  (Fiscal)  │  │  (SMTP)    │
└────────┘         └─────────────┘  └────────────┘  └────────────┘
```

### Key Principles

1. **Microservices-Ready**: Modular design for easy service extraction
2. **Multi-Tenancy**: Support multiple companies with data isolation
3. **Offline-First Compatible**: Differential sync with conflict resolution
4. **Event-Driven**: NATS for async operations (notifications, analytics)
5. **Scalable**: Horizontal scaling with load balancing
6. **Observable**: Structured logging, metrics (Prometheus), tracing (Jaeger)

---

## 2. Technology Stack

### Core Framework
- **HTTP Framework**: Gin (fast) or Echo (middleware-rich)
- **Language**: Go 1.22+
- **Configuration**: Viper (12-factor app support)

### Database
- **Primary DB**: PostgreSQL 16+ (JSONB, full-text search, partitioning)
- **Cache**: Redis 7+ (sessions, rate limiting, sync locks)
- **Search**: PostgreSQL full-text search (initially), ElasticSearch (optional future)

### Storage
- **Files**: MinIO (S3-compatible) or AWS S3
- **CDN**: CloudFlare or AWS CloudFront

### Messaging & Async
- **Message Queue**: NATS or RabbitMQ
- **Push Notifications**: Firebase Admin SDK (initially) → Custom WebSocket solution
- **Background Jobs**: Asynq (Redis-based)

### Authentication
- **Tokens**: JWT (HS256 or RS256)
- **Password**: bcrypt or Argon2id
- **API Keys**: For service-to-service

### Observability
- **Logging**: zerolog or zap (structured JSON)
- **Metrics**: Prometheus + Grafana
- **Tracing**: OpenTelemetry → Jaeger
- **Error Tracking**: Sentry (replaces Crashlytics)

### DevOps
- **Containerization**: Docker + Docker Compose
- **Orchestration**: Kubernetes (production) or Docker Swarm (small deployments)
- **CI/CD**: GitHub Actions or GitLab CI
- **Migrations**: golang-migrate or goose

---

## 3. Database Schema

### PostgreSQL Schema Design

#### 3.1 Multi-Tenancy Strategy

**Approach**: Schema-per-tenant (PostgreSQL schemas)

```sql
-- Each company gets its own schema
CREATE SCHEMA company_a94ef3;  -- Based on company GUID
CREATE SCHEMA company_b48fg7;
CREATE SCHEMA shared;           -- Shared tables (users, licenses, etc.)
```

**Benefits**:
- Strong data isolation
- Easy backup/restore per company
- Independent schema migrations per tenant
- PostgreSQL RLS (Row-Level Security) as additional layer

---

#### 3.2 Shared Schema Tables

##### `shared.companies`
Master table for multi-tenant management.

```sql
CREATE TABLE shared.companies (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    guid VARCHAR(36) UNIQUE NOT NULL,           -- Maps to Android's db_guid
    name VARCHAR(255) NOT NULL,
    description TEXT,
    database_name VARCHAR(100) NOT NULL,        -- Android dbName
    is_active BOOLEAN DEFAULT true,
    subscription_tier VARCHAR(50) DEFAULT 'free',
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX idx_companies_guid ON shared.companies(guid);
CREATE INDEX idx_companies_active ON shared.companies(is_active);
```

---

##### `shared.user_accounts`
Maps to Android `UserAccount` entity. Represents agent/user credentials.

```sql
CREATE TABLE shared.user_accounts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    guid VARCHAR(36) UNIQUE NOT NULL,           -- Unique user identifier
    company_id UUID NOT NULL REFERENCES shared.companies(id) ON DELETE CASCADE,

    -- Authentication
    username VARCHAR(100) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,        -- bcrypt hash
    api_token VARCHAR(255),                     -- Current session token
    token_expires_at TIMESTAMPTZ,
    refresh_token VARCHAR(255),

    -- User Details
    email VARCHAR(255),
    phone VARCHAR(50),
    full_name VARCHAR(255),
    description TEXT,

    -- License
    license_key VARCHAR(100),
    license_expires_at TIMESTAMPTZ,

    -- Settings (JSON)
    options JSONB DEFAULT '{}'::jsonb,          -- Android UserAccount.options

    -- Status
    is_active BOOLEAN DEFAULT true,
    is_demo BOOLEAN DEFAULT false,
    last_login_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW(),

    CONSTRAINT unique_username_company UNIQUE(company_id, username)
);

CREATE INDEX idx_user_accounts_guid ON shared.user_accounts(guid);
CREATE INDEX idx_user_accounts_company ON shared.user_accounts(company_id);
CREATE INDEX idx_user_accounts_token ON shared.user_accounts(api_token);
CREATE INDEX idx_user_accounts_license ON shared.user_accounts(license_key);
```

**UserAccount.options JSONB Structure** (matches Android):
```json
{
  "write": true,
  "useCompanies": true,
  "useStores": true,
  "clientsLocations": true,
  "clientsDirections": false,
  "clientsProducts": false,
  "loadImages": true,
  "fiscalProvider": "checkbox",
  "fiscalProviderId": "provider-guid",
  "fiscalDeviceId": "device-id",
  "fiscalCashierPin": "1234"
}
```

---

##### `shared.licenses`
License key management (replaces Firebase Firestore license system).

```sql
CREATE TABLE shared.licenses (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    license_key VARCHAR(100) UNIQUE NOT NULL,
    company_id UUID REFERENCES shared.companies(id) ON DELETE SET NULL,

    -- License Type
    tier VARCHAR(50) DEFAULT 'free',            -- free, pro, enterprise
    max_users INT DEFAULT 1,
    features JSONB DEFAULT '{}'::jsonb,

    -- Validity
    issued_at TIMESTAMPTZ DEFAULT NOW(),
    expires_at TIMESTAMPTZ,
    is_active BOOLEAN DEFAULT true,

    -- Activation
    activation_count INT DEFAULT 0,
    max_activations INT DEFAULT 1,
    last_activation_at TIMESTAMPTZ,

    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX idx_licenses_key ON shared.licenses(license_key);
CREATE INDEX idx_licenses_company ON shared.licenses(company_id);
```

---

##### `shared.device_registrations`
Push notification device tokens (replaces FCM).

```sql
CREATE TABLE shared.device_registrations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_account_id UUID NOT NULL REFERENCES shared.user_accounts(id) ON DELETE CASCADE,

    -- Device Info
    device_id VARCHAR(255) NOT NULL,            -- Android device ID
    device_type VARCHAR(50) DEFAULT 'android',
    device_name VARCHAR(255),
    os_version VARCHAR(50),
    app_version VARCHAR(50),

    -- Push Tokens
    fcm_token TEXT,                             -- Firebase Cloud Messaging token
    websocket_id VARCHAR(100),                  -- For WebSocket connections

    -- Status
    is_active BOOLEAN DEFAULT true,
    last_seen_at TIMESTAMPTZ DEFAULT NOW(),
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW(),

    CONSTRAINT unique_device_user UNIQUE(user_account_id, device_id)
);

CREATE INDEX idx_device_registrations_user ON shared.device_registrations(user_account_id);
CREATE INDEX idx_device_registrations_device ON shared.device_registrations(device_id);
```

---

##### `shared.crash_reports`
Error tracking (replaces Firebase Crashlytics).

```sql
CREATE TABLE shared.crash_reports (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_account_id UUID REFERENCES shared.user_accounts(id) ON DELETE SET NULL,

    -- Report Data
    exception_type VARCHAR(255),
    exception_message TEXT,
    stack_trace TEXT,
    context JSONB DEFAULT '{}'::jsonb,          -- Additional context

    -- Environment
    app_version VARCHAR(50),
    os_version VARCHAR(50),
    device_model VARCHAR(255),

    -- Grouping
    fingerprint VARCHAR(64),                    -- MD5 hash for grouping similar errors
    occurrence_count INT DEFAULT 1,
    first_occurred_at TIMESTAMPTZ DEFAULT NOW(),
    last_occurred_at TIMESTAMPTZ DEFAULT NOW(),

    created_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX idx_crash_reports_user ON shared.crash_reports(user_account_id);
CREATE INDEX idx_crash_reports_fingerprint ON shared.crash_reports(fingerprint);
CREATE INDEX idx_crash_reports_occurred ON shared.crash_reports(last_occurred_at DESC);
```

---

##### `shared.analytics_events`
Usage analytics (replaces Firebase Analytics).

```sql
CREATE TABLE shared.analytics_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_account_id UUID REFERENCES shared.user_accounts(id) ON DELETE SET NULL,
    company_id UUID NOT NULL REFERENCES shared.companies(id) ON DELETE CASCADE,

    -- Event Data
    event_name VARCHAR(100) NOT NULL,
    event_category VARCHAR(100),
    properties JSONB DEFAULT '{}'::jsonb,

    -- Context
    session_id VARCHAR(100),
    device_id VARCHAR(255),
    ip_address INET,
    user_agent TEXT,

    created_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX idx_analytics_events_user ON shared.analytics_events(user_account_id);
CREATE INDEX idx_analytics_events_company ON shared.analytics_events(company_id);
CREATE INDEX idx_analytics_events_name ON shared.analytics_events(event_name);
CREATE INDEX idx_analytics_events_created ON shared.analytics_events(created_at DESC);

-- Partition by month for performance
CREATE TABLE shared.analytics_events_2025_01 PARTITION OF shared.analytics_events
    FOR VALUES FROM ('2025-01-01') TO ('2025-02-01');
```

---

##### `shared.notifications`
Notification queue and history.

```sql
CREATE TABLE shared.notifications (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_account_id UUID NOT NULL REFERENCES shared.user_accounts(id) ON DELETE CASCADE,

    -- Notification Content
    title VARCHAR(255) NOT NULL,
    message TEXT NOT NULL,
    notification_type VARCHAR(50),              -- order_update, sync_complete, fiscal_error
    payload JSONB DEFAULT '{}'::jsonb,

    -- Delivery
    is_sent BOOLEAN DEFAULT false,
    sent_at TIMESTAMPTZ,
    is_read BOOLEAN DEFAULT false,
    read_at TIMESTAMPTZ,

    -- Channels
    sent_via_fcm BOOLEAN DEFAULT false,
    sent_via_websocket BOOLEAN DEFAULT false,
    sent_via_email BOOLEAN DEFAULT false,

    created_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX idx_notifications_user ON shared.notifications(user_account_id);
CREATE INDEX idx_notifications_unread ON shared.notifications(user_account_id, is_read) WHERE is_read = false;
CREATE INDEX idx_notifications_created ON shared.notifications(created_at DESC);
```

---

#### 3.3 Tenant Schema Tables (Per-Company)

All tables below exist in each company schema (e.g., `company_a94ef3.orders`).

---

##### `[schema].clients`

```sql
CREATE TABLE clients (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    guid VARCHAR(36) UNIQUE NOT NULL,

    -- Basic Info
    code1 VARCHAR(50),
    code2 VARCHAR(50),
    description VARCHAR(255) NOT NULL,
    description_lc VARCHAR(255),                -- Lowercase for search
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

    -- Metadata
    timestamp BIGINT NOT NULL,                  -- Unix timestamp (ms) - for sync
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX idx_clients_guid ON clients(guid);
CREATE INDEX idx_clients_group ON clients(group_guid);
CREATE INDEX idx_clients_is_group ON clients(is_group);
CREATE INDEX idx_clients_description_lc ON clients(description_lc);
CREATE INDEX idx_clients_timestamp ON clients(timestamp);

-- Full-text search
CREATE INDEX idx_clients_fts ON clients USING gin(to_tsvector('simple', description || ' ' || COALESCE(notes, '')));
```

---

##### `[schema].products`

```sql
CREATE TABLE products (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    guid VARCHAR(36) UNIQUE NOT NULL,

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
    rest_type VARCHAR(50),

    -- Packaging
    package_only BOOLEAN DEFAULT false,
    package_value NUMERIC(12,3) DEFAULT 1.000,
    indivisible BOOLEAN DEFAULT false,

    -- Organization
    sorting INT DEFAULT 0,
    is_active BOOLEAN DEFAULT true,
    is_group BOOLEAN DEFAULT false,
    group_guid VARCHAR(36),

    -- Metadata
    timestamp BIGINT NOT NULL,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX idx_products_guid ON products(guid);
CREATE INDEX idx_products_barcode ON products(barcode);
CREATE INDEX idx_products_group ON products(group_guid);
CREATE INDEX idx_products_is_group ON products(is_group);
CREATE INDEX idx_products_description_lc ON products(description_lc);
CREATE INDEX idx_products_timestamp ON products(timestamp);

-- Full-text search
CREATE INDEX idx_products_fts ON products USING gin(to_tsvector('simple', description || ' ' || COALESCE(vendor_code, '') || ' ' || COALESCE(barcode, '')));
```

---

##### `[schema].orders`

```sql
CREATE TABLE orders (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    guid VARCHAR(36) UNIQUE NOT NULL,

    -- Document Info
    date DATE NOT NULL,
    time BIGINT NOT NULL,                       -- Unix timestamp (ms)
    time_saved BIGINT,
    number INT NOT NULL,

    -- References
    company_guid VARCHAR(36),
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
    next_payment NUMERIC(12,2) DEFAULT 0.00,

    -- Location
    latitude NUMERIC(10,7),
    longitude NUMERIC(10,7),
    location_time BIGINT,
    distance NUMERIC(10,2),

    -- Flags
    is_fiscal BOOLEAN DEFAULT false,
    is_return BOOLEAN DEFAULT false,
    is_processed BOOLEAN DEFAULT false,
    is_sent BOOLEAN DEFAULT false,

    -- Status
    status VARCHAR(50) DEFAULT 'draft',
    rest_type VARCHAR(50),

    -- Metadata
    created_by UUID REFERENCES shared.user_accounts(id),
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX idx_orders_guid ON orders(guid);
CREATE INDEX idx_orders_client ON orders(client_guid);
CREATE INDEX idx_orders_date ON orders(date DESC);
CREATE INDEX idx_orders_time ON orders(time DESC);
CREATE INDEX idx_orders_is_sent ON orders(is_sent) WHERE is_sent = false;
CREATE INDEX idx_orders_is_processed ON orders(is_processed) WHERE is_processed = false;
```

---

##### `[schema].order_contents`

```sql
CREATE TABLE order_contents (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_guid VARCHAR(36) NOT NULL,
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
    updated_at TIMESTAMPTZ DEFAULT NOW(),

    FOREIGN KEY (order_guid) REFERENCES orders(guid) ON DELETE CASCADE
);

CREATE INDEX idx_order_contents_order ON order_contents(order_guid);
CREATE INDEX idx_order_contents_product ON order_contents(product_guid);
```

---

##### `[schema].cash_receipts`

```sql
CREATE TABLE cash_receipts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    guid VARCHAR(36) UNIQUE NOT NULL,

    -- Document Info
    date DATE NOT NULL,
    time BIGINT NOT NULL,
    number INT NOT NULL,

    -- References
    company_guid VARCHAR(36),
    company_name VARCHAR(255),
    client_guid VARCHAR(36),
    client_name VARCHAR(255),
    reference_guid VARCHAR(36),                 -- Link to Order if payment

    -- Payment
    sum NUMERIC(12,2) NOT NULL,
    notes TEXT,

    -- Fiscal
    fiscal_number BIGINT,
    is_fiscal BOOLEAN DEFAULT false,

    -- Status
    status VARCHAR(50) DEFAULT 'draft',
    is_processed BOOLEAN DEFAULT false,
    is_sent BOOLEAN DEFAULT false,

    -- Metadata
    created_by UUID REFERENCES shared.user_accounts(id),
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX idx_cash_receipts_guid ON cash_receipts(guid);
CREATE INDEX idx_cash_receipts_client ON cash_receipts(client_guid);
CREATE INDEX idx_cash_receipts_date ON cash_receipts(date DESC);
CREATE INDEX idx_cash_receipts_is_sent ON cash_receipts(is_sent) WHERE is_sent = false;
```

---

##### `[schema].tasks`

```sql
CREATE TABLE tasks (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    guid VARCHAR(36) UNIQUE NOT NULL,

    -- Task Info
    date DATE NOT NULL,
    time BIGINT NOT NULL,
    description VARCHAR(255) NOT NULL,
    notes TEXT,

    -- References
    client_guid VARCHAR(36),

    -- Status
    is_done BOOLEAN DEFAULT false,
    color VARCHAR(20),

    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX idx_tasks_guid ON tasks(guid);
CREATE INDEX idx_tasks_client ON tasks(client_guid);
CREATE INDEX idx_tasks_date ON tasks(date);
CREATE INDEX idx_tasks_is_done ON tasks(is_done) WHERE is_done = false;
```

---

##### `[schema].companies`

```sql
CREATE TABLE companies (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    guid VARCHAR(36) UNIQUE NOT NULL,
    description VARCHAR(255) NOT NULL,
    is_default BOOLEAN DEFAULT false,
    timestamp BIGINT NOT NULL,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX idx_companies_guid ON companies(guid);
```

---

##### `[schema].stores`

```sql
CREATE TABLE stores (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    guid VARCHAR(36) UNIQUE NOT NULL,
    description VARCHAR(255) NOT NULL,
    is_default BOOLEAN DEFAULT false,
    timestamp BIGINT NOT NULL,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX idx_stores_guid ON stores(guid);
```

---

##### `[schema].payment_types`

```sql
CREATE TABLE payment_types (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    payment_type VARCHAR(50) UNIQUE NOT NULL,
    description VARCHAR(255) NOT NULL,
    is_fiscal BOOLEAN DEFAULT false,
    is_default BOOLEAN DEFAULT false,
    timestamp BIGINT NOT NULL,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX idx_payment_types_code ON payment_types(payment_type);
```

---

##### `[schema].price_types`

```sql
CREATE TABLE price_types (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    price_type VARCHAR(50) UNIQUE NOT NULL,
    description VARCHAR(255) NOT NULL,
    timestamp BIGINT NOT NULL,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX idx_price_types_code ON price_types(price_type);
```

---

##### `[schema].product_prices`

```sql
CREATE TABLE product_prices (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    product_guid VARCHAR(36) NOT NULL,
    price_type VARCHAR(50) NOT NULL,
    price NUMERIC(12,2) NOT NULL,
    timestamp BIGINT NOT NULL,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW(),

    CONSTRAINT unique_product_price_type UNIQUE(product_guid, price_type)
);

CREATE INDEX idx_product_prices_product ON product_prices(product_guid);
CREATE INDEX idx_product_prices_type ON product_prices(price_type);
```

---

##### `[schema].rests` (Stock Levels)

```sql
CREATE TABLE rests (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_guid VARCHAR(36) NOT NULL,
    store_guid VARCHAR(36) NOT NULL,
    product_guid VARCHAR(36) NOT NULL,
    quantity NUMERIC(12,3) NOT NULL,
    timestamp BIGINT NOT NULL,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW(),

    CONSTRAINT unique_rest_location UNIQUE(company_guid, store_guid, product_guid)
);

CREATE INDEX idx_rests_product ON rests(product_guid);
CREATE INDEX idx_rests_company ON rests(company_guid);
CREATE INDEX idx_rests_store ON rests(store_guid);
```

---

##### `[schema].debts`

```sql
CREATE TABLE debts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_guid VARCHAR(36) NOT NULL,
    client_guid VARCHAR(36) NOT NULL,
    doc_guid VARCHAR(36),
    doc_id VARCHAR(50),
    doc_type VARCHAR(50),

    -- Amounts
    sum NUMERIC(12,2) DEFAULT 0.00,
    sum_in NUMERIC(12,2) DEFAULT 0.00,
    sum_out NUMERIC(12,2) DEFAULT 0.00,

    -- Details
    has_content BOOLEAN DEFAULT false,
    content JSONB DEFAULT '{}'::jsonb,
    is_total BOOLEAN DEFAULT false,
    sorting INT DEFAULT 0,

    timestamp BIGINT NOT NULL,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW(),

    CONSTRAINT unique_debt_doc UNIQUE(company_guid, client_guid, doc_id)
);

CREATE INDEX idx_debts_client ON debts(client_guid);
CREATE INDEX idx_debts_company ON debts(company_guid);
```

---

##### `[schema].client_locations`

```sql
CREATE TABLE client_locations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    client_guid VARCHAR(36) UNIQUE NOT NULL,
    latitude NUMERIC(10,7) NOT NULL,
    longitude NUMERIC(10,7) NOT NULL,
    address TEXT,
    is_modified BOOLEAN DEFAULT false,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX idx_client_locations_client ON client_locations(client_guid);
CREATE INDEX idx_client_locations_modified ON client_locations(is_modified) WHERE is_modified = true;
```

---

##### `[schema].client_images`

```sql
CREATE TABLE client_images (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    client_guid VARCHAR(36) NOT NULL,
    guid VARCHAR(36) UNIQUE NOT NULL,
    url TEXT,                                   -- S3/MinIO path or Base64
    description TEXT,
    is_default BOOLEAN DEFAULT false,
    is_local BOOLEAN DEFAULT false,             -- Uploaded from device
    is_sent BOOLEAN DEFAULT false,
    timestamp BIGINT NOT NULL,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX idx_client_images_client ON client_images(client_guid);
CREATE INDEX idx_client_images_unsent ON client_images(is_sent) WHERE is_sent = false;
```

---

##### `[schema].product_images`

```sql
CREATE TABLE product_images (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    product_guid VARCHAR(36) NOT NULL,
    guid VARCHAR(36) UNIQUE NOT NULL,
    url TEXT NOT NULL,                          -- S3/MinIO URL
    description TEXT,
    type VARCHAR(50),
    is_default BOOLEAN DEFAULT false,
    timestamp BIGINT NOT NULL,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX idx_product_images_product ON product_images(product_guid);
```

---

##### `[schema].location_history`

```sql
CREATE TABLE location_history (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_account_id UUID NOT NULL REFERENCES shared.user_accounts(id),

    -- GPS Data
    time BIGINT NOT NULL,
    latitude NUMERIC(10,7) NOT NULL,
    longitude NUMERIC(10,7) NOT NULL,
    accuracy NUMERIC(10,2),
    altitude NUMERIC(10,2),
    bearing NUMERIC(5,2),
    speed NUMERIC(10,2),
    distance NUMERIC(10,2),

    -- Context
    provider VARCHAR(50),
    point_name TEXT,
    extra JSONB DEFAULT '{}'::jsonb,

    created_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX idx_location_history_user ON location_history(user_account_id);
CREATE INDEX idx_location_history_time ON location_history(time DESC);
CREATE INDEX idx_location_history_coords ON location_history USING gist(ll_to_earth(latitude, longitude));
```

---

##### `[schema].sync_results`

```sql
CREATE TABLE sync_results (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    document_type VARCHAR(50) NOT NULL,
    document_guid VARCHAR(36) NOT NULL,

    -- Sync Status
    status VARCHAR(50) NOT NULL,                -- ok, error, pending
    error_message TEXT,
    server_response JSONB DEFAULT '{}'::jsonb,

    -- Metadata
    synced_by UUID REFERENCES shared.user_accounts(id),
    synced_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX idx_sync_results_document ON sync_results(document_guid);
CREATE INDEX idx_sync_results_type ON sync_results(document_type);
CREATE INDEX idx_sync_results_status ON sync_results(status);
```

---

## 4. API Endpoints

### Base URL Structure

```
https://api.agentventa.com/v1
```

### 4.1 Authentication & User Management

#### `POST /v1/auth/register`
Register new user account.

**Request:**
```json
{
  "company_guid": "a94ef3-...",
  "username": "agent123",
  "password": "SecurePass123!",
  "email": "agent@example.com",
  "full_name": "John Doe",
  "license_key": "XXXX-XXXX-XXXX-XXXX"
}
```

**Response:**
```json
{
  "success": true,
  "data": {
    "user_guid": "user-guid-123",
    "access_token": "eyJhbGciOiJIUzI1NiIs...",
    "refresh_token": "refresh-token-abc",
    "expires_in": 86400
  }
}
```

---

#### `POST /v1/auth/login`
Authenticate user and receive tokens.

**Request:**
```json
{
  "username": "agent123",
  "password": "SecurePass123!",
  "company_guid": "a94ef3-..."
}
```

**Response:**
```json
{
  "success": true,
  "data": {
    "user_guid": "user-guid-123",
    "access_token": "eyJhbGciOiJIUzI1NiIs...",
    "refresh_token": "refresh-token-abc",
    "expires_in": 86400,
    "user_options": {
      "write": true,
      "useCompanies": true,
      "useStores": true,
      "clientsLocations": true,
      "loadImages": true
    }
  }
}
```

---

#### `POST /v1/auth/refresh`
Refresh access token using refresh token.

**Headers:**
```
Authorization: Bearer <refresh_token>
```

**Response:**
```json
{
  "success": true,
  "data": {
    "access_token": "eyJhbGciOiJIUzI1NiIs...",
    "expires_in": 86400
  }
}
```

---

#### `POST /v1/auth/logout`
Invalidate current session.

**Headers:**
```
Authorization: Bearer <access_token>
```

**Response:**
```json
{
  "success": true,
  "message": "Logged out successfully"
}
```

---

#### `GET /v1/auth/me`
Get current user profile.

**Headers:**
```
Authorization: Bearer <access_token>
```

**Response:**
```json
{
  "success": true,
  "data": {
    "guid": "user-guid-123",
    "username": "agent123",
    "email": "agent@example.com",
    "full_name": "John Doe",
    "company": {
      "guid": "a94ef3-...",
      "name": "Company ABC"
    },
    "license": {
      "key": "XXXX-XXXX-XXXX-XXXX",
      "tier": "pro",
      "expires_at": "2025-12-31T23:59:59Z"
    },
    "options": {
      "write": true,
      "useCompanies": true
    }
  }
}
```

---

### 4.2 License Management

#### `POST /v1/licenses/validate`
Validate license key (replaces Firebase Firestore license check).

**Request:**
```json
{
  "license_key": "XXXX-XXXX-XXXX-XXXX",
  "user_guid": "user-guid-123",
  "device_id": "android-device-abc"
}
```

**Response:**
```json
{
  "success": true,
  "data": {
    "is_valid": true,
    "tier": "pro",
    "expires_at": "2025-12-31T23:59:59Z",
    "features": {
      "max_users": 10,
      "fiscal_integration": true,
      "location_tracking": true
    }
  }
}
```

---

### 4.3 Data Synchronization

#### `POST /v1/sync/full`
Full catalog synchronization (replaces `GET /get/{type}/{token}{more}`).

**Headers:**
```
Authorization: Bearer <access_token>
X-Last-Sync-Timestamp: 1234567890000
```

**Request:**
```json
{
  "sync_types": [
    "clients",
    "products",
    "payment_types",
    "companies",
    "stores",
    "rests",
    "images"
  ],
  "options": {
    "loadImages": true,
    "useCompanies": true,
    "useStores": true
  }
}
```

**Response:**
```json
{
  "success": true,
  "data": {
    "timestamp": 1234567890000,
    "clients": {
      "total": 150,
      "items": [
        {
          "guid": "client-guid-1",
          "code1": "C001",
          "description": "Client ABC",
          "price_type": "retail",
          "discount": 5.0,
          "is_active": true,
          "timestamp": 1234567890000
        }
      ],
      "has_more": true,
      "next_page": "cursor-abc123"
    },
    "products": {
      "total": 500,
      "items": [...],
      "has_more": false
    }
  }
}
```

---

#### `POST /v1/sync/differential`
Differential sync: upload unsent documents and receive updates.

**Headers:**
```
Authorization: Bearer <access_token>
```

**Request:**
```json
{
  "orders": [
    {
      "guid": "order-guid-1",
      "date": "2025-01-15",
      "time": 1234567890000,
      "client_guid": "client-guid-1",
      "company_guid": "company-guid-1",
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
  "cash_receipts": [],
  "client_images": [
    {
      "client_guid": "client-guid-1",
      "guid": "image-guid-1",
      "url": "data:image/jpeg;base64,/9j/4AAQSkZJRg...",
      "description": "Store front",
      "is_default": true
    }
  ],
  "client_locations": []
}
```

**Response:**
```json
{
  "success": true,
  "data": {
    "orders": [
      {
        "guid": "order-guid-1",
        "status": "ok",
        "server_number": "ORD-2025-00123",
        "is_processed": true
      }
    ],
    "cash_receipts": [],
    "client_images": [
      {
        "guid": "image-guid-1",
        "status": "ok",
        "url": "https://cdn.agentventa.com/images/client-guid-1/image-guid-1.jpg"
      }
    ]
  }
}
```

---

#### `GET /v1/sync/status`
Check sync status for documents.

**Headers:**
```
Authorization: Bearer <access_token>
```

**Query Parameters:**
```
?document_guids=order-guid-1,order-guid-2,cash-guid-1
```

**Response:**
```json
{
  "success": true,
  "data": {
    "order-guid-1": {
      "status": "processed",
      "server_number": "ORD-2025-00123",
      "processed_at": "2025-01-15T10:30:00Z"
    },
    "order-guid-2": {
      "status": "pending",
      "message": "Waiting for approval"
    },
    "cash-guid-1": {
      "status": "error",
      "error": "Invalid fiscal number"
    }
  }
}
```

---

### 4.4 Catalog Data Endpoints

#### `GET /v1/clients`
Get client list with filtering and pagination.

**Headers:**
```
Authorization: Bearer <access_token>
```

**Query Parameters:**
```
?search=ABC
&is_group=false
&is_active=true
&group_guid=group-guid-1
&page=1
&limit=50
&sort_by=description
&order=asc
&timestamp_after=1234567890000
```

**Response:**
```json
{
  "success": true,
  "data": {
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
        "timestamp": 1234567890000,
        "location": {
          "latitude": 50.4501,
          "longitude": 30.5234,
          "address": "Kyiv, Shevchenko St, 1"
        },
        "debt": {
          "total": 2500.00,
          "overdue": 500.00
        }
      }
    ],
    "pagination": {
      "page": 1,
      "limit": 50,
      "total": 150,
      "total_pages": 3,
      "has_next": true
    }
  }
}
```

---

#### `GET /v1/clients/:guid`
Get single client details.

**Response:**
```json
{
  "success": true,
  "data": {
    "guid": "client-guid-1",
    "code1": "C001",
    "description": "Client ABC",
    "notes": "VIP client",
    "phone": "+380501234567",
    "address": "Kyiv, Shevchenko St, 1",
    "discount": 5.0,
    "bonus": 150.50,
    "price_type": "retail",
    "is_banned": false,
    "is_active": true,
    "location": {
      "latitude": 50.4501,
      "longitude": 30.5234,
      "address": "Kyiv, Shevchenko St, 1"
    },
    "debt": {
      "total": 2500.00,
      "documents": [
        {
          "doc_id": "INV-2025-001",
          "doc_type": "invoice",
          "sum": 2500.00,
          "date": "2025-01-10"
        }
      ]
    },
    "recent_orders": [
      {
        "guid": "order-guid-1",
        "date": "2025-01-15",
        "total_price": 1500.00,
        "status": "completed"
      }
    ]
  }
}
```

---

#### `GET /v1/products`
Get product catalog with filtering.

**Query Parameters:**
```
?search=laptop
&barcode=1234567890123
&group_guid=group-guid-1
&is_active=true
&price_type=retail
&page=1
&limit=50
&timestamp_after=1234567890000
```

**Response:**
```json
{
  "success": true,
  "data": {
    "items": [
      {
        "guid": "product-guid-1",
        "code1": "P001",
        "barcode": "1234567890123",
        "description": "Laptop Dell XPS 15",
        "vendor_code": "DELL-XPS15",
        "price": 35000.00,
        "min_price": 32000.00,
        "base_price": 28000.00,
        "quantity": 10.0,
        "unit": "piece",
        "weight": 2.5,
        "package_only": false,
        "is_active": true,
        "is_group": false,
        "group_guid": "group-guid-electronics",
        "timestamp": 1234567890000,
        "images": [
          {
            "guid": "image-guid-1",
            "url": "https://cdn.agentventa.com/products/product-guid-1/main.jpg",
            "is_default": true
          }
        ],
        "prices": [
          {
            "price_type": "retail",
            "price": 35000.00,
            "description": "Retail Price"
          },
          {
            "price_type": "wholesale",
            "price": 32000.00,
            "description": "Wholesale Price"
          }
        ],
        "stock": [
          {
            "company_guid": "company-guid-1",
            "store_guid": "store-guid-1",
            "quantity": 10.0
          }
        ]
      }
    ],
    "pagination": {
      "page": 1,
      "limit": 50,
      "total": 500,
      "has_next": true
    }
  }
}
```

---

### 4.5 Document Management

#### `POST /v1/orders`
Create new order.

**Request:**
```json
{
  "guid": "order-guid-1",
  "date": "2025-01-15",
  "time": 1234567890000,
  "client_guid": "client-guid-1",
  "company_guid": "company-guid-1",
  "store_guid": "store-guid-1",
  "price_type": "retail",
  "payment_type": "CASH",
  "discount": 5.0,
  "is_fiscal": true,
  "notes": "Urgent delivery",
  "location": {
    "latitude": 50.4501,
    "longitude": 30.5234
  },
  "items": [
    {
      "product_guid": "product-guid-1",
      "quantity": 2.0,
      "price": 750.00,
      "sum": 1500.00,
      "discount": 0.00
    }
  ]
}
```

**Response:**
```json
{
  "success": true,
  "data": {
    "guid": "order-guid-1",
    "number": 123,
    "status": "pending",
    "total_price": 1500.00,
    "is_processed": false,
    "created_at": "2025-01-15T10:00:00Z"
  }
}
```

---

#### `GET /v1/orders`
List orders with filtering.

**Query Parameters:**
```
?client_guid=client-guid-1
&date_from=2025-01-01
&date_to=2025-01-31
&status=pending
&is_fiscal=true
&page=1
&limit=50
```

**Response:**
```json
{
  "success": true,
  "data": {
    "items": [
      {
        "guid": "order-guid-1",
        "number": 123,
        "date": "2025-01-15",
        "client": {
          "guid": "client-guid-1",
          "description": "Client ABC"
        },
        "total_price": 1500.00,
        "total_quantity": 2.0,
        "is_fiscal": true,
        "status": "pending",
        "created_at": "2025-01-15T10:00:00Z"
      }
    ],
    "pagination": {
      "page": 1,
      "total": 25,
      "has_next": false
    }
  }
}
```

---

#### `GET /v1/orders/:guid`
Get order details with line items.

**Response:**
```json
{
  "success": true,
  "data": {
    "guid": "order-guid-1",
    "number": 123,
    "date": "2025-01-15",
    "time": 1234567890000,
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
    "payment_type": "CASH",
    "price_type": "retail",
    "discount": 5.0,
    "total_price": 1500.00,
    "total_quantity": 2.0,
    "is_fiscal": true,
    "status": "pending",
    "notes": "Urgent delivery",
    "location": {
      "latitude": 50.4501,
      "longitude": 30.5234
    },
    "items": [
      {
        "product_guid": "product-guid-1",
        "product_code": "P001",
        "product_description": "Laptop Dell XPS 15",
        "unit": "piece",
        "quantity": 2.0,
        "price": 750.00,
        "sum": 1500.00,
        "discount": 0.00
      }
    ],
    "created_at": "2025-01-15T10:00:00Z",
    "updated_at": "2025-01-15T10:00:00Z"
  }
}
```

---

#### `PUT /v1/orders/:guid`
Update existing order.

---

#### `DELETE /v1/orders/:guid`
Delete order (soft delete).

---

### 4.6 Fiscal Integration

#### `POST /v1/fiscal/receipts`
Create fiscal receipt via Checkbox PRRO API proxy.

**Request:**
```json
{
  "order_guid": "order-guid-1",
  "provider": "checkbox",
  "cashier_pin": "1234",
  "payment_type": "CASH",
  "items": [
    {
      "product_code": "P001",
      "description": "Laptop Dell XPS 15",
      "quantity": 2.0,
      "price": 750.00,
      "sum": 1500.00
    }
  ]
}
```

**Response:**
```json
{
  "success": true,
  "data": {
    "fiscal_number": 12345678,
    "receipt_url": "https://checkbox.ua/receipt/12345678",
    "receipt_pdf": "https://api.agentventa.com/v1/fiscal/receipts/12345678.pdf",
    "created_at": "2025-01-15T10:30:00Z"
  }
}
```

---

### 4.7 Image Management

#### `POST /v1/images/upload`
Upload image (client photos, product images).

**Request (multipart/form-data):**
```
file: <binary>
type: client_image
entity_guid: client-guid-1
is_default: true
description: Store front photo
```

**Response:**
```json
{
  "success": true,
  "data": {
    "guid": "image-guid-1",
    "url": "https://cdn.agentventa.com/images/client-guid-1/image-guid-1.jpg",
    "thumbnail_url": "https://cdn.agentventa.com/images/client-guid-1/image-guid-1-thumb.jpg",
    "size": 245678,
    "uploaded_at": "2025-01-15T10:00:00Z"
  }
}
```

---

#### `POST /v1/images/upload-base64`
Upload Base64-encoded image (for existing Android flow).

**Request:**
```json
{
  "type": "client_image",
  "client_guid": "client-guid-1",
  "guid": "image-guid-1",
  "data": "data:image/jpeg;base64,/9j/4AAQSkZJRg...",
  "description": "Store front",
  "is_default": true
}
```

**Response:**
```json
{
  "success": true,
  "data": {
    "guid": "image-guid-1",
    "url": "https://cdn.agentventa.com/images/client-guid-1/image-guid-1.jpg",
    "uploaded_at": "2025-01-15T10:00:00Z"
  }
}
```

---

### 4.8 Location Tracking

#### `POST /v1/locations/track`
Batch upload location history.

**Request:**
```json
{
  "locations": [
    {
      "time": 1234567890000,
      "latitude": 50.4501,
      "longitude": 30.5234,
      "accuracy": 15.0,
      "altitude": 180.0,
      "speed": 5.5,
      "provider": "gps"
    },
    {
      "time": 1234567900000,
      "latitude": 50.4505,
      "longitude": 30.5240,
      "accuracy": 12.0,
      "altitude": 182.0,
      "speed": 6.0,
      "provider": "gps"
    }
  ]
}
```

**Response:**
```json
{
  "success": true,
  "data": {
    "saved": 2,
    "skipped": 0
  }
}
```

---

#### `GET /v1/locations/history`
Get location history for user.

**Query Parameters:**
```
?date_from=2025-01-01
&date_to=2025-01-31
&limit=1000
```

**Response:**
```json
{
  "success": true,
  "data": {
    "items": [
      {
        "time": 1234567890000,
        "latitude": 50.4501,
        "longitude": 30.5234,
        "accuracy": 15.0,
        "speed": 5.5,
        "point_name": "Kyiv, Shevchenko St"
      }
    ],
    "total": 1500
  }
}
```

---

### 4.9 Push Notifications

#### `POST /v1/notifications/register-device`
Register device for push notifications (replaces FCM token registration).

**Request:**
```json
{
  "device_id": "android-device-abc",
  "device_name": "Samsung Galaxy S21",
  "os_version": "Android 13",
  "app_version": "3.0.10208",
  "fcm_token": "fcm-token-xyz"
}
```

**Response:**
```json
{
  "success": true,
  "data": {
    "device_id": "android-device-abc",
    "registered_at": "2025-01-15T10:00:00Z"
  }
}
```

---

#### `POST /v1/notifications/send`
Send notification to user (admin/system endpoint).

**Request:**
```json
{
  "user_guid": "user-guid-123",
  "title": "Order Processed",
  "message": "Your order #123 has been processed",
  "type": "order_update",
  "payload": {
    "order_guid": "order-guid-1",
    "order_number": 123
  }
}
```

**Response:**
```json
{
  "success": true,
  "data": {
    "notification_id": "notif-guid-1",
    "sent_to_devices": 2,
    "sent_at": "2025-01-15T10:00:00Z"
  }
}
```

---

#### `GET /v1/notifications`
Get user notifications (in-app notification center).

**Query Parameters:**
```
?is_read=false
&limit=50
```

**Response:**
```json
{
  "success": true,
  "data": {
    "items": [
      {
        "id": "notif-guid-1",
        "title": "Order Processed",
        "message": "Your order #123 has been processed",
        "type": "order_update",
        "payload": {
          "order_guid": "order-guid-1"
        },
        "is_read": false,
        "created_at": "2025-01-15T10:00:00Z"
      }
    ],
    "unread_count": 5
  }
}
```

---

#### `PUT /v1/notifications/:id/read`
Mark notification as read.

---

### 4.10 Error Tracking & Analytics

#### `POST /v1/analytics/crash`
Report app crash (replaces Firebase Crashlytics).

**Request:**
```json
{
  "exception_type": "java.lang.NullPointerException",
  "exception_message": "Attempt to invoke virtual method on null object",
  "stack_trace": "at ua.com.programmer.agentventa.Checkbox.createReceipt(Checkbox.kt:309)\n...",
  "context": {
    "fiscal_receipt": "data...",
    "user_action": "create_fiscal_receipt"
  },
  "app_version": "3.0.10208",
  "os_version": "Android 13",
  "device_model": "Samsung Galaxy S21"
}
```

**Response:**
```json
{
  "success": true,
  "data": {
    "crash_id": "crash-guid-1",
    "fingerprint": "md5-hash-abc123",
    "reported_at": "2025-01-15T10:00:00Z"
  }
}
```

---

#### `POST /v1/analytics/event`
Track analytics event (replaces Firebase Analytics).

**Request:**
```json
{
  "event_name": "order_created",
  "event_category": "documents",
  "properties": {
    "order_total": 1500.00,
    "item_count": 2,
    "is_fiscal": true,
    "payment_type": "CASH"
  }
}
```

**Response:**
```json
{
  "success": true,
  "data": {
    "event_id": "event-guid-1",
    "tracked_at": "2025-01-15T10:00:00Z"
  }
}
```

---

### 4.11 WebSocket Real-Time Updates

#### `WS /v1/ws`
WebSocket connection for real-time updates.

**Connection:**
```
wss://api.agentventa.com/v1/ws?token=<access_token>
```

**Message Types:**

**Client → Server (Subscribe):**
```json
{
  "type": "subscribe",
  "channels": ["orders", "notifications", "sync_status"]
}
```

**Server → Client (Order Update):**
```json
{
  "type": "order_update",
  "data": {
    "order_guid": "order-guid-1",
    "status": "processed",
    "server_number": "ORD-2025-00123",
    "updated_at": "2025-01-15T10:30:00Z"
  }
}
```

**Server → Client (Notification):**
```json
{
  "type": "notification",
  "data": {
    "title": "New Order Assignment",
    "message": "You have been assigned order #124",
    "payload": {
      "order_guid": "order-guid-2"
    }
  }
}
```

**Server → Client (Sync Complete):**
```json
{
  "type": "sync_complete",
  "data": {
    "timestamp": 1234567890000,
    "updated_items": {
      "clients": 5,
      "products": 10
    }
  }
}
```

---

## 5. Data Models (Go Structs)

### 5.1 Core Domain Models

```go
// User Account
type UserAccount struct {
    ID              uuid.UUID      `json:"id" db:"id"`
    GUID            string         `json:"guid" db:"guid"`
    CompanyID       uuid.UUID      `json:"company_id" db:"company_id"`
    Username        string         `json:"username" db:"username"`
    PasswordHash    string         `json:"-" db:"password_hash"`
    Email           string         `json:"email" db:"email"`
    FullName        string         `json:"full_name" db:"full_name"`
    LicenseKey      string         `json:"license_key" db:"license_key"`
    Options         UserOptions    `json:"options" db:"options"`
    IsActive        bool           `json:"is_active" db:"is_active"`
    IsDemo          bool           `json:"is_demo" db:"is_demo"`
    LastLoginAt     *time.Time     `json:"last_login_at" db:"last_login_at"`
    CreatedAt       time.Time      `json:"created_at" db:"created_at"`
    UpdatedAt       time.Time      `json:"updated_at" db:"updated_at"`
}

type UserOptions struct {
    Write               bool   `json:"write"`
    UseCompanies        bool   `json:"useCompanies"`
    UseStores           bool   `json:"useStores"`
    ClientsLocations    bool   `json:"clientsLocations"`
    ClientsDirections   bool   `json:"clientsDirections"`
    ClientsProducts     bool   `json:"clientsProducts"`
    LoadImages          bool   `json:"loadImages"`
    FiscalProvider      string `json:"fiscalProvider"`
    FiscalProviderID    string `json:"fiscalProviderId"`
    FiscalDeviceID      string `json:"fiscalDeviceId"`
    FiscalCashierPin    string `json:"fiscalCashierPin"`
}
```

---

```go
// Client
type Client struct {
    ID            uuid.UUID  `json:"id" db:"id"`
    GUID          string     `json:"guid" db:"guid"`
    Code1         string     `json:"code1" db:"code1"`
    Code2         string     `json:"code2" db:"code2"`
    Description   string     `json:"description" db:"description"`
    DescriptionLC string     `json:"-" db:"description_lc"`
    Notes         string     `json:"notes" db:"notes"`
    Phone         string     `json:"phone" db:"phone"`
    Address       string     `json:"address" db:"address"`
    Discount      float64    `json:"discount" db:"discount"`
    Bonus         float64    `json:"bonus" db:"bonus"`
    PriceType     string     `json:"price_type" db:"price_type"`
    IsBanned      bool       `json:"is_banned" db:"is_banned"`
    BanMessage    string     `json:"ban_message" db:"ban_message"`
    IsActive      bool       `json:"is_active" db:"is_active"`
    IsGroup       bool       `json:"is_group" db:"is_group"`
    GroupGUID     string     `json:"group_guid" db:"group_guid"`
    Timestamp     int64      `json:"timestamp" db:"timestamp"`
    CreatedAt     time.Time  `json:"created_at" db:"created_at"`
    UpdatedAt     time.Time  `json:"updated_at" db:"updated_at"`
}

// Extended Client with relations
type ClientDetail struct {
    Client
    Location      *ClientLocation `json:"location,omitempty"`
    Debt          *DebtSummary    `json:"debt,omitempty"`
    RecentOrders  []OrderSummary  `json:"recent_orders,omitempty"`
}
```

---

```go
// Product
type Product struct {
    ID            uuid.UUID  `json:"id" db:"id"`
    GUID          string     `json:"guid" db:"guid"`
    Code1         string     `json:"code1" db:"code1"`
    Code2         string     `json:"code2" db:"code2"`
    VendorCode    string     `json:"vendor_code" db:"vendor_code"`
    Barcode       string     `json:"barcode" db:"barcode"`
    Description   string     `json:"description" db:"description"`
    DescriptionLC string     `json:"-" db:"description_lc"`
    Price         float64    `json:"price" db:"price"`
    MinPrice      float64    `json:"min_price" db:"min_price"`
    BasePrice     float64    `json:"base_price" db:"base_price"`
    Quantity      float64    `json:"quantity" db:"quantity"`
    Weight        float64    `json:"weight" db:"weight"`
    Unit          string     `json:"unit" db:"unit"`
    PackageOnly   bool       `json:"package_only" db:"package_only"`
    PackageValue  float64    `json:"package_value" db:"package_value"`
    Indivisible   bool       `json:"indivisible" db:"indivisible"`
    IsActive      bool       `json:"is_active" db:"is_active"`
    IsGroup       bool       `json:"is_group" db:"is_group"`
    GroupGUID     string     `json:"group_guid" db:"group_guid"`
    Timestamp     int64      `json:"timestamp" db:"timestamp"`
    CreatedAt     time.Time  `json:"created_at" db:"created_at"`
    UpdatedAt     time.Time  `json:"updated_at" db:"updated_at"`
}

// Extended Product with relations
type ProductDetail struct {
    Product
    Images        []ProductImage `json:"images,omitempty"`
    Prices        []ProductPrice `json:"prices,omitempty"`
    Stock         []Rest         `json:"stock,omitempty"`
}
```

---

```go
// Order
type Order struct {
    ID                uuid.UUID  `json:"id" db:"id"`
    GUID              string     `json:"guid" db:"guid"`
    Date              string     `json:"date" db:"date"`
    Time              int64      `json:"time" db:"time"`
    TimeSaved         int64      `json:"time_saved" db:"time_saved"`
    Number            int        `json:"number" db:"number"`
    CompanyGUID       string     `json:"company_guid" db:"company_guid"`
    CompanyName       string     `json:"company_name" db:"company_name"`
    StoreGUID         string     `json:"store_guid" db:"store_guid"`
    StoreName         string     `json:"store_name" db:"store_name"`
    ClientGUID        string     `json:"client_guid" db:"client_guid"`
    ClientCode        string     `json:"client_code" db:"client_code"`
    ClientDescription string     `json:"client_description" db:"client_description"`
    DeliveryDate      string     `json:"delivery_date" db:"delivery_date"`
    Notes             string     `json:"notes" db:"notes"`
    PriceType         string     `json:"price_type" db:"price_type"`
    PaymentType       string     `json:"payment_type" db:"payment_type"`
    Discount          float64    `json:"discount" db:"discount"`
    TotalPrice        float64    `json:"total_price" db:"total_price"`
    TotalQuantity     float64    `json:"total_quantity" db:"total_quantity"`
    TotalWeight       float64    `json:"total_weight" db:"total_weight"`
    DiscountValue     float64    `json:"discount_value" db:"discount_value"`
    Latitude          float64    `json:"latitude" db:"latitude"`
    Longitude         float64    `json:"longitude" db:"longitude"`
    IsFiscal          bool       `json:"is_fiscal" db:"is_fiscal"`
    IsReturn          bool       `json:"is_return" db:"is_return"`
    IsProcessed       bool       `json:"is_processed" db:"is_processed"`
    IsSent            bool       `json:"is_sent" db:"is_sent"`
    Status            string     `json:"status" db:"status"`
    CreatedBy         uuid.UUID  `json:"created_by" db:"created_by"`
    CreatedAt         time.Time  `json:"created_at" db:"created_at"`
    UpdatedAt         time.Time  `json:"updated_at" db:"updated_at"`
}

type OrderContent struct {
    ID          uuid.UUID  `json:"id" db:"id"`
    OrderGUID   string     `json:"order_guid" db:"order_guid"`
    ProductGUID string     `json:"product_guid" db:"product_guid"`
    UnitCode    string     `json:"unit_code" db:"unit_code"`
    Quantity    float64    `json:"quantity" db:"quantity"`
    Weight      float64    `json:"weight" db:"weight"`
    Price       float64    `json:"price" db:"price"`
    Sum         float64    `json:"sum" db:"sum"`
    Discount    float64    `json:"discount" db:"discount"`
    IsDemand    bool       `json:"is_demand" db:"is_demand"`
    IsPacked    bool       `json:"is_packed" db:"is_packed"`
    CreatedAt   time.Time  `json:"created_at" db:"created_at"`
    UpdatedAt   time.Time  `json:"updated_at" db:"updated_at"`
}

// Extended Order with items
type OrderDetail struct {
    Order
    Items []OrderContentDetail `json:"items"`
}

type OrderContentDetail struct {
    OrderContent
    ProductCode        string `json:"product_code"`
    ProductDescription string `json:"product_description"`
}
```

---

### 5.2 API Request/Response Models

```go
// Authentication
type LoginRequest struct {
    Username    string `json:"username" binding:"required"`
    Password    string `json:"password" binding:"required"`
    CompanyGUID string `json:"company_guid" binding:"required"`
}

type LoginResponse struct {
    UserGUID     string      `json:"user_guid"`
    AccessToken  string      `json:"access_token"`
    RefreshToken string      `json:"refresh_token"`
    ExpiresIn    int64       `json:"expires_in"`
    UserOptions  UserOptions `json:"user_options"`
}

type RegisterRequest struct {
    CompanyGUID string `json:"company_guid" binding:"required"`
    Username    string `json:"username" binding:"required,min=3,max=100"`
    Password    string `json:"password" binding:"required,min=8"`
    Email       string `json:"email" binding:"required,email"`
    FullName    string `json:"full_name" binding:"required"`
    LicenseKey  string `json:"license_key" binding:"required"`
}

// Sync
type FullSyncRequest struct {
    SyncTypes []string    `json:"sync_types" binding:"required"`
    Options   UserOptions `json:"options"`
}

type FullSyncResponse struct {
    Timestamp int64                  `json:"timestamp"`
    Data      map[string]interface{} `json:"data"`
}

type DifferentialSyncRequest struct {
    Orders         []OrderCreateRequest     `json:"orders"`
    CashReceipts   []CashReceiptRequest     `json:"cash_receipts"`
    ClientImages   []ClientImageUpload      `json:"client_images"`
    ClientLocations []ClientLocationUpdate  `json:"client_locations"`
}

type DifferentialSyncResponse struct {
    Orders         []SyncResult `json:"orders"`
    CashReceipts   []SyncResult `json:"cash_receipts"`
    ClientImages   []SyncResult `json:"client_images"`
    ClientLocations []SyncResult `json:"client_locations"`
}

type SyncResult struct {
    GUID        string `json:"guid"`
    Status      string `json:"status"`
    Error       string `json:"error,omitempty"`
    ServerData  map[string]interface{} `json:"server_data,omitempty"`
}

// Order Creation
type OrderCreateRequest struct {
    GUID          string               `json:"guid" binding:"required"`
    Date          string               `json:"date" binding:"required"`
    Time          int64                `json:"time" binding:"required"`
    ClientGUID    string               `json:"client_guid"`
    CompanyGUID   string               `json:"company_guid"`
    StoreGUID     string               `json:"store_guid"`
    PriceType     string               `json:"price_type"`
    PaymentType   string               `json:"payment_type"`
    Discount      float64              `json:"discount"`
    IsFiscal      bool                 `json:"is_fiscal"`
    Notes         string               `json:"notes"`
    Location      *LocationData        `json:"location"`
    Items         []OrderContentCreate `json:"items" binding:"required,min=1"`
}

type OrderContentCreate struct {
    ProductGUID string  `json:"product_guid" binding:"required"`
    Quantity    float64 `json:"quantity" binding:"required,gt=0"`
    Price       float64 `json:"price" binding:"required,gt=0"`
    Sum         float64 `json:"sum" binding:"required"`
    Discount    float64 `json:"discount"`
}

// Pagination
type PaginationParams struct {
    Page    int    `form:"page" binding:"omitempty,min=1"`
    Limit   int    `form:"limit" binding:"omitempty,min=1,max=100"`
    SortBy  string `form:"sort_by"`
    Order   string `form:"order" binding:"omitempty,oneof=asc desc"`
}

type PaginationMeta struct {
    Page       int  `json:"page"`
    Limit      int  `json:"limit"`
    Total      int  `json:"total"`
    TotalPages int  `json:"total_pages"`
    HasNext    bool `json:"has_next"`
}

// Standard API Response
type APIResponse struct {
    Success bool        `json:"success"`
    Data    interface{} `json:"data,omitempty"`
    Error   *APIError   `json:"error,omitempty"`
}

type APIError struct {
    Code    string `json:"code"`
    Message string `json:"message"`
    Details string `json:"details,omitempty"`
}
```

---

## 6. Authentication & Authorization

### 6.1 JWT Authentication

**Token Structure:**
```json
{
  "sub": "user-guid-123",
  "company_id": "company-guid-abc",
  "username": "agent123",
  "exp": 1234567890,
  "iat": 1234567800,
  "jti": "token-id-xyz"
}
```

**Token Types:**
- **Access Token**: Short-lived (1 hour), used for API requests
- **Refresh Token**: Long-lived (30 days), used to obtain new access tokens

**Storage:**
- Access tokens: Redis (for blacklisting on logout)
- Refresh tokens: PostgreSQL `user_accounts.refresh_token`

---

### 6.2 Middleware

```go
// JWT Middleware
func JWTAuthMiddleware() gin.HandlerFunc {
    return func(c *gin.Context) {
        // Extract token from Authorization header
        // Validate token signature
        // Check token expiration
        // Check if token is blacklisted in Redis
        // Extract user_guid and company_id from claims
        // Set user context for downstream handlers
    }
}

// Rate Limiting Middleware
func RateLimitMiddleware() gin.HandlerFunc {
    // Redis-based rate limiting
    // Per-user rate limits (100 req/min)
    // Per-IP rate limits (300 req/min)
}

// Tenant Context Middleware
func TenantContextMiddleware() gin.HandlerFunc {
    // Extract company_id from JWT claims
    // Set PostgreSQL schema context for query isolation
    // company_schema := "company_" + company_guid
}
```

---

## 7. Firebase Replacement

### 7.1 Firebase Cloud Messaging (FCM) → Custom Push Service

**Migration Strategy:**

**Phase 1: Hybrid (Keep FCM, Add API)**
- Keep existing FCM integration in Android app
- Add new `/v1/notifications/register-device` endpoint
- Server stores FCM tokens and sends via Firebase Admin SDK
- Benefit: Zero Android app changes required initially

**Phase 2: WebSocket Real-Time (Long-term)**
- Implement WebSocket connection (`wss://api.agentventa.com/v1/ws`)
- When app is in foreground: Receive notifications via WebSocket
- When app is in background: Fall back to FCM
- Benefit: Real-time updates without FCM dependency

**Phase 3: Custom Push Gateway (Future)**
- Implement custom Android push using Firebase Alternative (e.g., Gotify, ntfy)
- Or use Android WorkManager for background sync checks
- Completely remove FCM dependency

**Implementation:**

```go
// Push Service Interface
type PushService interface {
    SendNotification(ctx context.Context, userID uuid.UUID, notification Notification) error
    SendBulkNotifications(ctx context.Context, notifications []BulkNotification) error
    RegisterDevice(ctx context.Context, device DeviceRegistration) error
    UnregisterDevice(ctx context.Context, deviceID string) error
}

// FCM Implementation (Phase 1)
type FCMPushService struct {
    fcmClient *messaging.Client
    repo      *DeviceRepository
}

func (s *FCMPushService) SendNotification(ctx context.Context, userID uuid.UUID, notif Notification) error {
    // Get user's registered devices
    devices, err := s.repo.GetActiveDevices(ctx, userID)

    // Send FCM message to each device
    for _, device := range devices {
        message := &messaging.Message{
            Token: device.FCMToken,
            Notification: &messaging.Notification{
                Title: notif.Title,
                Body:  notif.Message,
            },
            Data: notif.Payload,
        }
        _, err := s.fcmClient.Send(ctx, message)
    }

    // Save notification to database for in-app history
    s.repo.SaveNotification(ctx, userID, notif)
}

// WebSocket Implementation (Phase 2)
type WebSocketPushService struct {
    hub  *WebSocketHub
    repo *DeviceRepository
}

func (s *WebSocketPushService) SendNotification(ctx context.Context, userID uuid.UUID, notif Notification) error {
    // Try sending via active WebSocket connections
    sent := s.hub.SendToUser(userID, notif)

    // If no active WebSocket, fall back to FCM
    if !sent {
        return s.fcmFallback.SendNotification(ctx, userID, notif)
    }

    return nil
}
```

---

### 7.2 Firebase Crashlytics → Sentry / Custom Error Tracking

**Replacement Options:**

**Option A: Sentry (Recommended for quick migration)**
- Self-hosted or cloud Sentry instance
- Excellent error grouping and fingerprinting
- Android SDK available: `io.sentry:sentry-android`
- Go SDK: `github.com/getsentry/sentry-go`

**Option B: Custom Error Tracking**
- Use `shared.crash_reports` table
- Group errors by fingerprint (MD5 hash of stack trace)
- Admin dashboard for viewing grouped errors

**Android App Changes:**

```kotlin
// Replace Firebase Crashlytics
// Before:
FirebaseCrashlytics.getInstance().recordException(exception)

// After (Sentry):
Sentry.captureException(exception)

// Or (Custom API):
apiService.reportCrash(CrashReport(
    exceptionType = exception.javaClass.name,
    exceptionMessage = exception.message,
    stackTrace = exception.stackTraceToString(),
    context = mapOf("fiscal_receipt" to receiptData)
))
```

**Go Backend Implementation:**

```go
// Crash Report Handler
func (h *AnalyticsHandler) ReportCrash(c *gin.Context) {
    var req CrashReportRequest
    if err := c.ShouldBindJSON(&req); err != nil {
        c.JSON(400, APIResponse{Success: false, Error: &APIError{Message: err.Error()}})
        return
    }

    // Generate fingerprint for grouping
    fingerprint := generateFingerprint(req.ExceptionType, req.StackTrace)

    // Check if crash already exists
    existing, _ := h.repo.GetCrashByFingerprint(c, fingerprint)

    if existing != nil {
        // Increment occurrence count
        h.repo.IncrementCrashOccurrence(c, existing.ID)
    } else {
        // Create new crash report
        crash := &CrashReport{
            UserAccountID:    getUserID(c),
            ExceptionType:    req.ExceptionType,
            ExceptionMessage: req.ExceptionMessage,
            StackTrace:       req.StackTrace,
            Context:          req.Context,
            AppVersion:       req.AppVersion,
            OSVersion:        req.OSVersion,
            DeviceModel:      req.DeviceModel,
            Fingerprint:      fingerprint,
            OccurrenceCount:  1,
        }
        h.repo.CreateCrashReport(c, crash)
    }

    c.JSON(200, APIResponse{Success: true})
}
```

---

### 7.3 Firebase Analytics → Custom Analytics + Optional Mixpanel/PostHog

**Migration Strategy:**

1. **Custom Events Table**: Store all events in `shared.analytics_events`
2. **Real-time Dashboard**: Grafana + PostgreSQL/TimescaleDB queries
3. **Optional**: Export to PostHog (open-source product analytics) or Mixpanel

**Android App Changes:**

```kotlin
// Before (Firebase Analytics):
firebaseAnalytics.logEvent("order_created", Bundle().apply {
    putDouble("order_total", 1500.0)
    putInt("item_count", 2)
    putBoolean("is_fiscal", true)
})

// After (Custom API):
apiService.trackEvent(AnalyticsEvent(
    eventName = "order_created",
    eventCategory = "documents",
    properties = mapOf(
        "order_total" to 1500.0,
        "item_count" to 2,
        "is_fiscal" to true
    )
))
```

**Go Backend:**

```go
func (h *AnalyticsHandler) TrackEvent(c *gin.Context) {
    var req AnalyticsEventRequest
    if err := c.ShouldBindJSON(&req); err != nil {
        c.JSON(400, APIResponse{Success: false, Error: &APIError{Message: err.Error()}})
        return
    }

    event := &AnalyticsEvent{
        UserAccountID: getUserID(c),
        CompanyID:     getCompanyID(c),
        EventName:     req.EventName,
        EventCategory: req.EventCategory,
        Properties:    req.Properties,
        SessionID:     getSessionID(c),
        DeviceID:      getDeviceID(c),
        IPAddress:     c.ClientIP(),
        UserAgent:     c.Request.UserAgent(),
    }

    // Save to database (async via queue)
    h.queue.Enqueue("analytics.event", event)

    c.JSON(200, APIResponse{Success: true})
}
```

---

## 8. Sync Strategy

### 8.1 Full Sync Algorithm

**Server-side:**

```go
func (s *SyncService) PerformFullSync(ctx context.Context, req FullSyncRequest, userID uuid.UUID) (*FullSyncResponse, error) {
    companyID := getCompanyID(ctx)
    schema := getSchemaForCompany(companyID)

    response := &FullSyncResponse{
        Timestamp: time.Now().UnixMilli(),
        Data:      make(map[string]interface{}),
    }

    // For each requested sync type
    for _, syncType := range req.SyncTypes {
        switch syncType {
        case "clients":
            clients, err := s.repo.GetClients(ctx, schema, req.LastSyncTimestamp)
            response.Data["clients"] = PaginatedResult{
                Total: len(clients),
                Items: clients,
            }

        case "products":
            products, err := s.repo.GetProducts(ctx, schema, req.LastSyncTimestamp)
            response.Data["products"] = PaginatedResult{
                Total: len(products),
                Items: products,
            }

        case "images":
            if req.Options.LoadImages {
                images, err := s.repo.GetProductImages(ctx, schema, req.LastSyncTimestamp)
                response.Data["images"] = images
            }

        // ... other sync types
        }
    }

    return response, nil
}
```

**Optimization:**
- **Incremental Sync**: Only return items where `timestamp > last_sync_timestamp`
- **Pagination**: Use cursor-based pagination for large datasets
- **Compression**: gzip response bodies
- **Caching**: Redis cache for frequently requested catalog data

---

### 8.2 Differential Sync (Document Upload)

**Server-side:**

```go
func (s *SyncService) PerformDifferentialSync(ctx context.Context, req DifferentialSyncRequest, userID uuid.UUID) (*DifferentialSyncResponse, error) {
    companyID := getCompanyID(ctx)
    schema := getSchemaForCompany(companyID)

    response := &DifferentialSyncResponse{}

    // Process Orders
    for _, orderReq := range req.Orders {
        result := SyncResult{GUID: orderReq.GUID}

        // Validate order
        if err := s.validator.ValidateOrder(ctx, orderReq); err != nil {
            result.Status = "error"
            result.Error = err.Error()
        } else {
            // Save order to database
            order, err := s.repo.CreateOrder(ctx, schema, orderReq, userID)
            if err != nil {
                result.Status = "error"
                result.Error = err.Error()
            } else {
                result.Status = "ok"
                result.ServerData = map[string]interface{}{
                    "server_number": order.Number,
                    "is_processed":  order.IsProcessed,
                }

                // Trigger async processing (1C integration, notifications, etc.)
                s.queue.Enqueue("order.process", order.ID)
            }
        }

        response.Orders = append(response.Orders, result)
    }

    // Process Client Images
    for _, imageReq := range req.ClientImages {
        result := SyncResult{GUID: imageReq.GUID}

        // Decode Base64 image
        imageData, err := base64.StdEncoding.DecodeString(imageReq.Data)
        if err != nil {
            result.Status = "error"
            result.Error = "Invalid base64 data"
        } else {
            // Upload to S3/MinIO
            url, err := s.storage.UploadImage(ctx, imageReq.ClientGUID, imageReq.GUID, imageData)
            if err != nil {
                result.Status = "error"
                result.Error = err.Error()
            } else {
                // Save image record to database
                s.repo.SaveClientImage(ctx, schema, imageReq, url)
                result.Status = "ok"
                result.ServerData = map[string]interface{}{
                    "url": url,
                }
            }
        }

        response.ClientImages = append(response.ClientImages, result)
    }

    return response, nil
}
```

---

### 8.3 Conflict Resolution

**Strategy: Last-Write-Wins with Server Authority**

- Server timestamp is source of truth
- If client sends document with GUID that already exists:
  - Compare `time_saved` (client) vs `updated_at` (server)
  - If client is newer: Update server record
  - If server is newer: Return conflict error with server version
  - Android app can prompt user to resolve

**Future Enhancement: Version Vectors**
- Track version per document
- Detect true conflicts vs. concurrent non-conflicting edits

---

## 9. Migration Plan

### Phase 1: Infrastructure Setup (Week 1-2)

**Tasks:**
1. Set up Go project structure
2. Configure PostgreSQL with multi-tenant schemas
3. Set up Redis for caching and sessions
4. Configure MinIO/S3 for file storage
5. Set up Docker Compose for local development
6. Configure CI/CD pipeline

**Deliverables:**
- Running Go API server with health check endpoint
- Database migrations for shared and tenant schemas
- Docker setup for local development

---

### Phase 2: Core API Implementation (Week 3-6)

**Tasks:**
1. Implement authentication (login, register, token refresh)
2. Implement user account management
3. Implement license validation API
4. Implement catalog endpoints (clients, products, etc.)
5. Implement full sync endpoint
6. Implement differential sync endpoint
7. Implement document management (orders, cash)

**Deliverables:**
- Fully functional REST API matching existing Android app requirements
- API documentation (Swagger/OpenAPI)
- Integration tests for all endpoints

---

### Phase 3: Firebase Replacement (Week 7-8)

**Tasks:**
1. Implement push notification service (FCM Admin SDK initially)
2. Implement device registration endpoints
3. Implement crash reporting endpoint
4. Implement analytics event tracking
5. Set up Sentry for error tracking (optional)

**Deliverables:**
- Android app can send crash reports to new API
- Push notifications working via FCM Admin SDK
- Analytics dashboard (basic)

---

### Phase 4: Android App Migration (Week 9-10)

**Tasks:**
1. Update Android `HttpClientApi` to point to new server
2. Replace Firebase Crashlytics calls with new API
3. Update sync logic to use new endpoints
4. Test offline-first sync with new backend
5. Update authentication flow

**Deliverables:**
- Android app fully migrated to new API
- Backward compatibility maintained (can switch between old/new API via config)
- E2E testing complete

---

### Phase 5: Advanced Features (Week 11-12)

**Tasks:**
1. Implement WebSocket real-time updates
2. Implement fiscal integration proxy (Checkbox API)
3. Set up location tracking endpoints
4. Implement image CDN optimization
5. Set up monitoring and alerting (Prometheus + Grafana)

**Deliverables:**
- Real-time order status updates via WebSocket
- Fiscal receipt generation working
- Production monitoring dashboard

---

### Phase 6: Production Deployment (Week 13-14)

**Tasks:**
1. Deploy to production environment (Kubernetes or VPS)
2. Set up SSL certificates (Let's Encrypt)
3. Configure production database backups
4. Set up CDN for images (CloudFlare)
5. Load testing and performance optimization
6. Security audit

**Deliverables:**
- Production API running at `https://api.agentventa.com`
- Android app production build using new API
- Rollback plan and incident response procedures

---

## 10. Security Considerations

### 10.1 Security Measures

1. **HTTPS Only**: Enforce TLS 1.3 for all API communication
2. **JWT Signing**: Use RS256 (asymmetric) for production
3. **Password Hashing**: bcrypt or Argon2id with high cost factor
4. **Rate Limiting**: Redis-based per-user and per-IP limits
5. **SQL Injection**: Use parameterized queries (sqlx)
6. **XSS Protection**: Sanitize all user inputs
7. **CORS**: Restrict to Android app domain only
8. **API Key Rotation**: Support key rotation for integrations
9. **Audit Logging**: Log all sensitive operations (login, data changes)
10. **Database Encryption**: Encrypt sensitive fields (passwords, tokens)

---

### 10.2 Multi-Tenant Security

1. **Schema Isolation**: Each company has separate PostgreSQL schema
2. **Row-Level Security**: PostgreSQL RLS as additional layer
3. **Tenant Context Enforcement**: Middleware ensures queries use correct schema
4. **Cross-Tenant Access Prevention**: Validate all GUIDs against current tenant

```go
// Tenant Context Middleware
func TenantContextMiddleware() gin.HandlerFunc {
    return func(c *gin.Context) {
        companyID := getCompanyIDFromJWT(c)
        schema := "company_" + companyID

        // Set schema for all subsequent queries
        db := getDBFromContext(c)
        _, err := db.Exec("SET search_path TO " + schema)
        if err != nil {
            c.AbortWithStatus(500)
            return
        }

        c.Set("tenant_schema", schema)
        c.Next()
    }
}
```

---

## 11. Performance & Scalability

### 11.1 Performance Optimizations

1. **Database Indexing**: Comprehensive indexes on all foreign keys and search fields
2. **Connection Pooling**: pgx pool with optimal settings
3. **Query Optimization**: Use EXPLAIN ANALYZE for slow queries
4. **Caching Strategy**:
   - Redis cache for frequently accessed catalog data (TTL: 1 hour)
   - CDN caching for images (TTL: 1 week)
   - HTTP ETag headers for client-side caching
5. **Pagination**: Cursor-based pagination for large datasets
6. **Compression**: gzip response bodies (>1KB)
7. **Batch Operations**: Support bulk inserts for sync operations

---

### 11.2 Scalability Architecture

**Horizontal Scaling:**
- Stateless API servers behind load balancer
- PostgreSQL read replicas for read-heavy operations
- Redis cluster for distributed caching
- NATS for async message processing

**Vertical Scaling (Initial):**
- Single PostgreSQL instance with sufficient resources
- Single Redis instance
- Auto-scaling for API servers (Kubernetes HPA)

**Future (Multi-Region):**
- PostgreSQL multi-region replication
- CDN for global image delivery
- Regional API servers with GeoDNS routing

---

## 12. Testing Strategy

### 12.1 Testing Levels

1. **Unit Tests**: 80%+ coverage for business logic
2. **Integration Tests**: All API endpoints with test database
3. **E2E Tests**: Android app + API server integration
4. **Load Tests**: Apache JMeter or k6 for performance testing
5. **Security Tests**: OWASP ZAP for vulnerability scanning

---

### 12.2 Testing Tools

- **Unit/Integration**: Go testing package + testify
- **API Testing**: Postman collections + Newman
- **Load Testing**: k6 or Locust
- **Database**: PostgreSQL test containers
- **Mocking**: gomock or testify/mock

---

## Summary of Changes for Android App

### Minimal Changes (Phase 1)

1. **Update Base URL**: Change `HttpClient.baseUrl` to new API server
2. **Update Authentication**: Use JWT tokens instead of Basic Auth + token URL param
3. **Update Endpoint Paths**: Map old endpoints to new REST endpoints
4. **Add Authorization Header**: Include `Bearer <token>` in all requests

### Code Changes Required

```kotlin
// Before (Old API):
@GET("get/{type}/{token}{more}")
suspend fun get(@Path("type") type: String, @Path("token") token: String, @Path("more") more: String): Map<String,Any>

// After (New API):
@GET("v1/catalog/{type}")
suspend fun getCatalog(@Path("type") type: String, @Header("Authorization") auth: String, @Query("page") page: Int): Map<String,Any>
```

```kotlin
// Before (Old Auth):
request.newBuilder().header("Authorization", Credentials.basic(user, pass))

// After (New Auth):
request.newBuilder().header("Authorization", "Bearer $accessToken")
```

---

## Conclusion

This plan provides a comprehensive roadmap for building a Go-based API server that:

1. ✅ Replaces the 1C HTTP Data Exchange (hs/dex) interface
2. ✅ Replaces all Firebase services (FCM, Crashlytics, Analytics)
3. ✅ Supports multi-tenant architecture (multiple companies)
4. ✅ Maintains backward compatibility with Android app's offline-first design
5. ✅ Provides real-time updates via WebSocket
6. ✅ Implements comprehensive security and authentication
7. ✅ Scales horizontally for future growth
8. ✅ Includes monitoring, analytics, and error tracking

**Next Steps:**
1. Review and approve this plan
2. Set up development environment
3. Begin Phase 1: Infrastructure Setup
4. Iterative development with weekly demos

**Timeline:** 14 weeks (3.5 months) for full implementation and production deployment.
