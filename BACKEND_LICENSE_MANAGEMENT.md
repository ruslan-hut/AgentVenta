# Backend License Management Implementation Guide

## Overview

This document describes the required backend changes to properly implement license management based on the corrected architecture where:
- **License numbers identify 1C bases** (not devices)
- **Device UUIDs identify devices/accounts**
- **Backend maintains the mapping** between device UUIDs and license numbers

---

## Architecture Summary

### Core Concept
```
Device UUID → License Number → 1C Base
```

**Example Scenario:**
```
1C Base "CompanyA" → License "LICENSE-12345"
  ├─ Device "uuid-aaa-111" (Tablet 1)
  ├─ Device "uuid-bbb-222" (Tablet 2)
  └─ Device "uuid-ccc-333" (Phone 1)

1C Base "CompanyB" → License "LICENSE-67890"
  ├─ Device "uuid-ddd-444" (Tablet 3)
  └─ Device "uuid-eee-555" (Phone 2)
```

When Device "uuid-aaa-111" connects, backend knows to route data to/from CompanyA's 1C base via LICENSE-12345.

---

## Database Schema Changes

### Current Schema Issues

Based on the relay-hub-plan.md, the current MongoDB schema needs corrections:

#### Current (Incorrect) Schema
```javascript
// devices collection
{
  _id: ObjectId,
  uuid: String,                // Device UUID
  license_id: ObjectId,        // FK to licenses
  connection_state: String,
  last_seen: ISODate,
  db_guid: String              // Links to UserAccount.guid
}

// licenses collection
{
  _id: ObjectId,
  license_key: String,         // License number
  secret_hash: String,         // For authentication (WRONG!)
  device_limit: Number,
  expiration_date: ISODate
}
```

**Problems:**
1. `license.secret_hash` suggests license is used for authentication - INCORRECT
2. No clear connection between license and 1C base configuration
3. Unclear which license a device should use when connecting

---

### Corrected Schema

#### 1. `licenses` Collection
**Purpose:** Represents a 1C accounting database with its configuration

```javascript
{
  _id: ObjectId,
  license_number: String,          // Unique identifier (e.g., "LICENSE-12345")

  // 1C Base Configuration
  c1_base: {
    server: String,                // 1C server address (e.g., "1c.company.com")
    database: String,              // Database name (e.g., "trade_db")
    http_endpoint: String,         // Optional: full HTTP endpoint URL
    credentials: {                 // Optional: shared credentials if needed
      username: String,
      password_encrypted: String
    }
  },

  // License Metadata
  client_name: String,             // Company name for this license
  device_limit: Number,            // Max devices allowed
  expiration_date: ISODate,
  status: String,                  // "active" | "expired" | "suspended"

  // Tracking
  created_at: ISODate,
  updated_at: ISODate,

  // Optional: UserOptions to send to devices
  default_options: Object          // Default UserOptions for devices on this license
}
```

**Key Changes:**
- ✅ Added `c1_base` configuration object
- ✅ Renamed `license_key` → `license_number` for clarity
- ❌ Removed `secret_hash` - licenses are NOT used for authentication
- ✅ Added `default_options` for sending UserOptions to devices

**Indexes:**
```javascript
db.licenses.createIndex({ license_number: 1 }, { unique: true })
db.licenses.createIndex({ status: 1 })
db.licenses.createIndex({ expiration_date: 1 })
```

---

#### 2. `devices` Collection
**Purpose:** Represents individual Android devices/accounts linked to a license

```javascript
{
  _id: ObjectId,
  uuid: String,                    // Device UUID (UserAccount.guid from Android)
  license_number: String,          // Which license (and therefore 1C base) this device belongs to

  // Device State
  connection_state: String,        // "online" | "offline"
  last_seen: ISODate,
  first_connected: ISODate,

  // Device Information
  device_info: {
    model: String,                 // Optional: "Samsung Galaxy S21"
    android_version: String,       // Optional: "Android 13"
    app_version: String,           // Optional: "3.0.10234"
    description: String            // Optional: From UserAccount.description
  },

  // Account-Specific Overrides (optional)
  custom_options: Object,          // Override default_options from license if needed

  // WebSocket Connection Info
  active_connection_id: String,    // Current WebSocket connection ID (if online)

  // Tracking
  created_at: ISODate,
  updated_at: ISODate
}
```

**Key Changes:**
- ✅ Changed `license_id: ObjectId` → `license_number: String` for direct reference
- ❌ Removed `db_guid` field - replaced by `uuid` which is the same concept
- ✅ Added `custom_options` for device-specific configuration
- ✅ Added `active_connection_id` for WebSocket tracking

**Indexes:**
```javascript
db.devices.createIndex({ uuid: 1 }, { unique: true })
db.devices.createIndex({ license_number: 1 })
db.devices.createIndex({ connection_state: 1 })
db.devices.createIndex({ license_number: 1, connection_state: 1 })  // Compound for queries
```

---

#### 3. `queue` Collection (No Changes)
```javascript
{
  _id: ObjectId,
  device_uuid: String,             // Target device (matches devices.uuid)
  data: Object,
  data_type: String,
  ready: Boolean,
  created_at: ISODate,
  delivered_at: ISODate,
  ttl: ISODate
}
```

**Indexes:**
```javascript
db.queue.createIndex({ device_uuid: 1, ready: 1 })
db.queue.createIndex({ ttl: 1 }, { expireAfterSeconds: 0 })
```

---

#### 4. `outgoing_queue` Collection (Update)
```javascript
{
  _id: ObjectId,
  device_uuid: String,             // Source device
  license_number: String,          // CHANGED: Direct license reference instead of license_id
  data: Object,
  data_type: String,
  created_at: ISODate,
  fetched: Boolean,
  fetched_at: ISODate,
  ttl: ISODate
}
```

**Key Changes:**
- ✅ Changed `license_id: ObjectId` → `license_number: String`
- This allows 1C system to fetch data by license number directly

**Indexes:**
```javascript
db.outgoing_queue.createIndex({ license_number: 1, fetched: 1 })
db.outgoing_queue.createIndex({ device_uuid: 1 })
db.outgoing_queue.createIndex({ ttl: 1 }, { expireAfterSeconds: 0 })
```

---

## API Changes

### 1. WebSocket Connection Handler

**Endpoint:** `GET /ws/device?uuid={device_uuid}`

**BEFORE (Incorrect):**
```javascript
// Old implementation
GET /ws/device?uuid={device_uuid}&license={license_key}

// Handler
async function handleWebSocketConnection(req, res) {
  const { uuid, license } = req.query;

  // Validate license authentication (WRONG!)
  const licenseValid = await validateLicense(license);
  if (!licenseValid) {
    return res.status(401).send("Invalid license");
  }

  // Continue...
}
```

**AFTER (Correct):**
```javascript
// New implementation
GET /ws/device?uuid={device_uuid}

// Handler
async function handleWebSocketConnection(req, res) {
  const { uuid } = req.query;

  // 1. Look up device by UUID
  const device = await db.devices.findOne({ uuid: uuid });
  if (!device) {
    logger.warn("Unknown device attempted connection", { uuid });
    return res.status(404).send("Device not registered");
  }

  // 2. Look up license by license_number
  const license = await db.licenses.findOne({
    license_number: device.license_number
  });
  if (!license) {
    logger.error("Device linked to non-existent license", {
      uuid,
      license_number: device.license_number
    });
    return res.status(500).send("Configuration error");
  }

  // 3. Validate license status
  if (license.status !== "active") {
    logger.warn("Device attempted connection with inactive license", {
      uuid,
      license_number: license.license_number,
      status: license.status
    });
    return res.status(403).send("License inactive");
  }

  if (license.expiration_date && license.expiration_date < new Date()) {
    logger.warn("Device attempted connection with expired license", {
      uuid,
      license_number: license.license_number,
      expiration_date: license.expiration_date
    });
    return res.status(403).send("License expired");
  }

  // 4. Upgrade WebSocket connection
  const ws = upgradeToWebSocket(req, res);

  // 5. Update device state
  await db.devices.updateOne(
    { uuid: uuid },
    {
      $set: {
        connection_state: "online",
        last_seen: new Date(),
        active_connection_id: ws.id
      }
    }
  );

  // 6. Store connection with metadata
  activeConnections.set(uuid, {
    socket: ws,
    device_uuid: uuid,
    license_number: device.license_number,
    c1_base: license.c1_base,  // Store for routing
    connected_at: new Date()
  });

  // 7. Send queued messages
  await sendQueuedMessages(uuid);

  logger.info("WebSocket connected", {
    uuid,
    license_number: device.license_number,
    c1_base: license.c1_base.database
  });
}
```

**Key Points:**
- ✅ Only `uuid` parameter required
- ✅ Device UUID is looked up first
- ✅ License is retrieved via `device.license_number`
- ✅ License status and expiration validated
- ✅ Connection metadata includes `license_number` and `c1_base` for routing

---

### 2. Device Registration

**Endpoint:** `POST /api/v1/devices/register`

**Purpose:** Register a new device to a license (called by admin/1C system)

```javascript
// Request Body
{
  "device_uuid": "uuid-aaa-111",        // From Android UserAccount.guid
  "license_number": "LICENSE-12345",     // Which license to bind to
  "device_info": {                       // Optional
    "description": "Sales Agent - John"
  }
}

// Handler
async function registerDevice(req, res) {
  const { device_uuid, license_number, device_info } = req.body;

  // 1. Validate license exists and is active
  const license = await db.licenses.findOne({
    license_number: license_number,
    status: "active"
  });

  if (!license) {
    return res.status(404).json({
      error: "License not found or inactive"
    });
  }

  // 2. Check device limit
  const deviceCount = await db.devices.countDocuments({
    license_number: license_number
  });

  if (deviceCount >= license.device_limit) {
    return res.status(403).json({
      error: "Device limit reached",
      limit: license.device_limit,
      current_count: deviceCount
    });
  }

  // 3. Check if device already registered
  const existingDevice = await db.devices.findOne({ uuid: device_uuid });
  if (existingDevice) {
    // Update license binding
    await db.devices.updateOne(
      { uuid: device_uuid },
      {
        $set: {
          license_number: license_number,
          device_info: device_info,
          updated_at: new Date()
        }
      }
    );

    return res.json({
      message: "Device re-registered",
      device_uuid: device_uuid,
      license_number: license_number
    });
  }

  // 4. Create new device
  await db.devices.insertOne({
    uuid: device_uuid,
    license_number: license_number,
    connection_state: "offline",
    device_info: device_info || {},
    created_at: new Date(),
    updated_at: new Date(),
    first_connected: null
  });

  logger.info("Device registered", {
    device_uuid,
    license_number,
    c1_base: license.c1_base.database
  });

  res.json({
    message: "Device registered successfully",
    device_uuid: device_uuid,
    license_number: license_number,
    c1_base: {
      server: license.c1_base.server,
      database: license.c1_base.database
    }
  });
}
```

**Authentication:** Requires admin/1C system credentials, NOT device credentials

---

### 3. Push Data to Device

**Endpoint:** `POST /api/v1/push`

**BEFORE:**
```javascript
// Authentication: Bearer {license_key}:{secret}
// Body: { device_uuid, data_type, data }
```

**AFTER:**
```javascript
// Authentication: Bearer {license_number}:{admin_token}
// OR API key authentication for 1C system

async function pushToDevice(req, res) {
  const { device_uuid, data_type, data } = req.body;
  const license_number = req.auth.license_number;  // From auth middleware

  // 1. Verify device belongs to this license
  const device = await db.devices.findOne({
    uuid: device_uuid,
    license_number: license_number  // Ensure device belongs to caller's license
  });

  if (!device) {
    return res.status(404).json({
      error: "Device not found or not authorized for this license"
    });
  }

  // 2. Insert into queue
  const queueItem = await db.queue.insertOne({
    device_uuid: device_uuid,
    data: data,
    data_type: data_type,
    ready: false,
    created_at: new Date(),
    ttl: new Date(Date.now() + 24 * 60 * 60 * 1000)  // 24h TTL
  });

  // 3. If device is online, send immediately
  const connection = activeConnections.get(device_uuid);
  if (connection) {
    try {
      await sendMessageViaWebSocket(connection.socket, {
        type: "data",
        message_id: queueItem.insertedId.toString(),
        timestamp: new Date().toISOString(),
        payload: data
      });

      logger.info("Message sent to online device", {
        device_uuid,
        data_type,
        message_id: queueItem.insertedId
      });
    } catch (err) {
      logger.error("Failed to send to online device", {
        device_uuid,
        error: err.message
      });
    }
  }

  res.json({
    queued: true,
    queue_id: queueItem.insertedId,
    device_state: device.connection_state
  });
}
```

**Key Changes:**
- ✅ Verifies device belongs to the license making the request
- ✅ Uses `license_number` for authorization, not for device connection
- ✅ Prevents cross-license data access

---

### 4. Pull Data from Devices

**Endpoint:** `GET /api/v1/pull?license_number={license}&limit=50`

**BEFORE:**
```javascript
// Returns all outgoing data for license_id
```

**AFTER:**
```javascript
async function pullFromDevices(req, res) {
  const { license_number, limit = 50 } = req.query;
  const authenticated_license = req.auth.license_number;  // From auth

  // 1. Verify caller is authorized for this license
  if (license_number !== authenticated_license) {
    return res.status(403).json({
      error: "Not authorized for this license"
    });
  }

  // 2. Fetch unfetched items for this license
  const items = await db.outgoing_queue.find({
    license_number: license_number,
    fetched: false
  })
  .sort({ created_at: 1 })
  .limit(parseInt(limit))
  .toArray();

  // 3. Mark as fetched
  const itemIds = items.map(item => item._id);
  if (itemIds.length > 0) {
    await db.outgoing_queue.updateMany(
      { _id: { $in: itemIds } },
      {
        $set: {
          fetched: true,
          fetched_at: new Date()
        }
      }
    );
  }

  // 4. Return data
  res.json({
    count: items.length,
    license_number: license_number,
    items: items.map(item => ({
      device_uuid: item.device_uuid,
      data_type: item.data_type,
      data: item.data,
      created_at: item.created_at
    }))
  });

  logger.info("Data pulled", {
    license_number,
    count: items.length
  });
}
```

**Key Changes:**
- ✅ Filters by `license_number` instead of `license_id`
- ✅ Validates authorization
- ✅ Returns data grouped by license (1C base)

---

### 5. Send UserOptions to Device

**New Endpoint:** `POST /api/v1/devices/{uuid}/options`

**Purpose:** Update UserOptions for a specific device (sent to Android in next sync)

```javascript
async function updateDeviceOptions(req, res) {
  const { uuid } = req.params;
  const options = req.body;  // UserOptions JSON
  const license_number = req.auth.license_number;

  // 1. Verify device belongs to caller's license
  const device = await db.devices.findOne({
    uuid: uuid,
    license_number: license_number
  });

  if (!device) {
    return res.status(404).json({
      error: "Device not found or not authorized"
    });
  }

  // 2. Update device custom options
  await db.devices.updateOne(
    { uuid: uuid },
    {
      $set: {
        custom_options: options,
        updated_at: new Date()
      }
    }
  );

  // 3. If device online, send update immediately via WebSocket
  const connection = activeConnections.get(uuid);
  if (connection) {
    await sendMessageViaWebSocket(connection.socket, {
      type: "data",
      message_id: generateId(),
      timestamp: new Date().toISOString(),
      payload: {
        data_type: "settings",
        options: options
      }
    });
  }

  logger.info("Device options updated", {
    device_uuid: uuid,
    license_number
  });

  res.json({
    message: "Options updated",
    will_sync_on: connection ? "immediately" : "next_connection"
  });
}
```

---

### 6. License Management Endpoints

**Create License:** `POST /admin/licenses`
```javascript
{
  "license_number": "LICENSE-12345",
  "client_name": "CompanyA",
  "c1_base": {
    "server": "1c.companya.com",
    "database": "trade_db",
    "http_endpoint": "http://1c.companya.com/trade_db/hs/dex/"
  },
  "device_limit": 10,
  "expiration_date": "2026-12-31T23:59:59Z",
  "default_options": {
    "read": true,
    "write": true,
    "loadImages": true,
    "useCompanies": true
  }
}
```

**Update License:** `PUT /admin/licenses/{license_number}`
```javascript
{
  "device_limit": 20,
  "expiration_date": "2027-12-31T23:59:59Z",
  "status": "active"
}
```

**List Devices for License:** `GET /admin/licenses/{license_number}/devices`
```javascript
// Response
{
  "license_number": "LICENSE-12345",
  "device_limit": 10,
  "devices": [
    {
      "uuid": "uuid-aaa-111",
      "connection_state": "online",
      "last_seen": "2025-01-28T14:30:00Z",
      "device_info": {
        "description": "Sales Agent - John",
        "model": "Samsung Galaxy S21"
      }
    }
  ]
}
```

---

## Authentication & Authorization Changes

### Current Issues
The relay-hub-plan.md shows:
```javascript
// WRONG: Using license for authentication
Authorization: Bearer {license_key}:{secret}
```

### Corrected Authentication

#### 1. WebSocket Connections (Devices)
**Authentication:** None required at connection time
**Authorization:** Device UUID must be registered in `devices` collection

```javascript
// WebSocket URL
wss://relay.example.com/ws/device?uuid={device_uuid}

// Validation
- Lookup device in database by UUID
- Check linked license is active and not expired
- Allow connection if valid
```

#### 2. REST API (1C System)
**Authentication:** API Key or OAuth token per license

**Option A: API Key per License**
```javascript
// 1C system stores API key
Authorization: Bearer LICENSE-12345:api_key_abc123xyz

// Server validates
const [license_number, api_key] = token.split(':');
const license = await db.licenses.findOne({ license_number });
if (license.api_key_hash !== hash(api_key)) {
  return 401;
}
```

**Option B: Shared Admin Token**
```javascript
// All 1C systems use shared admin token
Authorization: Bearer {admin_api_token}

// Include license in request body
POST /api/v1/push
{
  "license_number": "LICENSE-12345",
  "device_uuid": "uuid-aaa-111",
  "data": {...}
}

// Server validates admin token + license access
```

**Recommendation:** Use Option A (API key per license) for better security and isolation

---

## Migration Guide

### Step 1: Update MongoDB Schema

```javascript
// 1. Add license_number field to devices
db.devices.find().forEach(function(device) {
  // Lookup license_id and get license_number
  const license = db.licenses.findOne({ _id: device.license_id });
  if (license) {
    db.devices.updateOne(
      { _id: device._id },
      { $set: { license_number: license.license_key } }
    );
  }
});

// 2. Rename license.license_key to license.license_number
db.licenses.find().forEach(function(license) {
  db.licenses.updateOne(
    { _id: license._id },
    {
      $rename: { license_key: "license_number" },
      $unset: { secret_hash: "" }  // Remove authentication fields
    }
  );
});

// 3. Add c1_base configuration to licenses
db.licenses.updateMany(
  {},
  {
    $set: {
      c1_base: {
        server: "",      // To be filled manually or via migration script
        database: "",
        http_endpoint: ""
      }
    }
  }
);

// 4. Update outgoing_queue references
db.outgoing_queue.find().forEach(function(item) {
  const device = db.devices.findOne({ uuid: item.device_uuid });
  if (device) {
    db.outgoing_queue.updateOne(
      { _id: item._id },
      { $set: { license_number: device.license_number } }
    );
  }
});

// 5. Remove old fields
db.devices.updateMany({}, { $unset: { license_id: "", db_guid: "" } });
db.outgoing_queue.updateMany({}, { $unset: { license_id: "" } });

// 6. Create new indexes
db.devices.createIndex({ license_number: 1 });
db.devices.createIndex({ license_number: 1, connection_state: 1 });
db.outgoing_queue.createIndex({ license_number: 1, fetched: 1 });
db.licenses.createIndex({ license_number: 1 }, { unique: true });
```

### Step 2: Update WebSocket Handler

1. Remove `license` parameter from URL parsing
2. Add device lookup by UUID
3. Add license lookup via device.license_number
4. Update connection metadata storage
5. Remove license-based authentication

### Step 3: Update REST API Handlers

1. Change authentication from license-based to API key per license
2. Update `/api/v1/push` to verify device belongs to license
3. Update `/api/v1/pull` to use license_number instead of license_id
4. Add new device registration endpoint

### Step 4: Update 1C Integration

1. Configure API key for each 1C base
2. Update HTTP calls to use license_number in auth
3. Update device registration flow
4. Test data push/pull with new authentication

---

## Testing Checklist

### Schema Migration
- [ ] All devices have valid `license_number` field
- [ ] All licenses have `license_number` (no `license_key`)
- [ ] All licenses have `c1_base` configuration
- [ ] Old fields removed (`license_id`, `secret_hash`, `db_guid`)
- [ ] Indexes created successfully

### WebSocket Connections
- [ ] Device connects with only `?uuid={uuid}` parameter
- [ ] Connection rejected if device UUID not registered
- [ ] Connection rejected if linked license is inactive
- [ ] Connection rejected if linked license is expired
- [ ] Connection accepted for valid device with active license
- [ ] Multiple devices on same license can connect simultaneously

### REST API
- [ ] `/api/v1/push` requires valid API key
- [ ] `/api/v1/push` rejects cross-license device access
- [ ] `/api/v1/pull` returns only data for authenticated license
- [ ] Device registration works with license_number
- [ ] Device registration enforces device limits
- [ ] UserOptions update endpoint works

### Data Routing
- [ ] Messages pushed to Device A reach only Device A
- [ ] Messages from Device A appear in correct license queue
- [ ] Multiple 1C bases don't see each other's data
- [ ] Queue cleanup removes old messages

### Authorization
- [ ] 1C System A cannot push to devices in License B
- [ ] 1C System A cannot pull data from License B
- [ ] Admin endpoints require admin authentication
- [ ] Device registration requires admin/1C authentication

---

## Security Considerations

### 1. Device UUID Validation
- Device UUIDs should be validated as proper UUIDs (format check)
- Prevent device UUID spoofing by requiring registration
- Log all connection attempts with unknown UUIDs

### 2. License Isolation
- Ensure strict filtering by license_number in all queries
- Prevent cross-license data leaks
- Audit queries to verify license_number is always included

### 3. API Key Management
- Store API keys as bcrypt hashes, never plaintext
- Rotate API keys periodically
- Provide API key regeneration endpoint
- Log all API key usage

### 4. Rate Limiting
- Implement per-license rate limits
- Prevent abuse of device registration endpoint
- Limit WebSocket connection attempts per UUID

### 5. Data Retention
- Implement TTL for queue items (24 hours)
- Implement TTL for outgoing_queue (7 days)
- Log and alert on queue size anomalies

---

## Performance Considerations

### Indexes
Ensure these indexes exist for optimal performance:
```javascript
// devices collection
{ uuid: 1 }                               // Unique, for WebSocket lookups
{ license_number: 1 }                     // For listing devices per license
{ license_number: 1, connection_state: 1 } // For online device queries

// licenses collection
{ license_number: 1 }                     // Unique, for license lookups
{ status: 1 }                             // For active license queries

// queue collection
{ device_uuid: 1, ready: 1 }              // For pending message queries
{ ttl: 1 }                                // TTL index for auto-cleanup

// outgoing_queue collection
{ license_number: 1, fetched: 1 }         // For pull queries
{ device_uuid: 1 }                        // For device-specific queries
{ ttl: 1 }                                // TTL index for auto-cleanup
```

### Caching
Consider caching:
- License configurations (c1_base) by license_number
- Device-to-license mappings by UUID
- Active WebSocket connection metadata

### Connection Pooling
- Use MongoDB connection pooling
- Maintain WebSocket connection map in memory (with cleanup on disconnect)

---

## Open Questions

1. **Device Registration Flow:** Should devices auto-register on first connection, or require manual registration?
   - **Recommendation:** Manual registration by admin/1C system for security

2. **License Assignment:** How does a new Android device know which license_number to use?
   - **Recommendation:** 1C system generates device UUID and registers it, then provides UUID to Android device via QR code or manual entry

3. **API Key Distribution:** How do 1C systems receive their API keys?
   - **Recommendation:** Admin panel generates API key when license is created, shown once to administrator

4. **Multi-tenancy:** Should relay server support multiple 1C installations per license?
   - **Current design:** One license = One 1C base (simple)
   - **Alternative:** License has array of c1_base configurations (complex)

5. **UserOptions Source of Truth:** Should default_options be in license or device?
   - **Recommendation:** License has default_options, device can override with custom_options

---

## Summary of Key Changes

### What Changed
1. ❌ **Removed:** License-based authentication for devices
2. ❌ **Removed:** `license` parameter from WebSocket URL
3. ❌ **Removed:** `license.secret_hash` field
4. ✅ **Added:** `license.c1_base` configuration object
5. ✅ **Added:** `devices.license_number` direct reference
6. ✅ **Added:** Device registration endpoint
7. ✅ **Changed:** Authentication from license to API key per license
8. ✅ **Changed:** Authorization checks to verify device belongs to license

### Why It Matters
- **Security:** Prevents cross-license data access
- **Clarity:** License identifies 1C base, not authentication credential
- **Scalability:** Easier to support multiple devices per license
- **Flexibility:** Allows device re-assignment between licenses
- **Correctness:** Matches the actual architecture where backend maintains device-to-license mapping

---

## Next Steps

1. **Review this document** with backend team
2. **Confirm database schema** changes
3. **Create migration scripts** for existing data
4. **Update backend code** (WebSocket handler, REST API)
5. **Test with Android app** using corrected WebSocket URL
6. **Deploy to staging** environment
7. **Validate end-to-end** flow
8. **Deploy to production** with monitoring
