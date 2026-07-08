# Wishlist Implementation Plan (July 2026)

Client wishlist, 6 features. Rule for all of them: **every feature is gated by a server-controlled
option key in `UserAccount.options`** (same pattern as `locations` / `loadImages` / `complexDiscounts`),
so users who don't need a feature never see it.

## Current architecture facts the plan builds on

- **Two upload transports**, both must be extended for anything that syncs back to 1C:
  - Direct HTTP: `NetworkRepositoryImpl.sendDocuments()` → `makePostRequest()` (entity `toMap()` with a `type` discriminator).
  - REST relay: `RelaySyncClient.uploadDocuments()` → `RelayUploadDocument(type, document_guid, data)` batch POST `/api/v1/device/upload`.
  - ⚠️ CLAUDE.md still describes a WebSocket transport — it was replaced by the REST relay; the `...ViaWebSocket` DAO method names are legacy. CLAUDE.md needs an update.
- **Dirty-flag precedents**:
  - Documents (Order/Cash): `is_processed=1 AND is_sent=0` → upload → `is_sent=1` (+`is_processed=2`, `status` on HTTP path).
  - Catalog-ish data (ClientLocation): `is_modified=1` → upload → reset `is_modified=0` on ack. **This is the template for app-edited catalog data** (clients, discounts).
  - Images (ClientImage): `is_sent=0 AND is_local=1`, Base64 in `url`, cleared after ack.
- **Options plumbing**: field in `UserOptions` + parsing in `UserOptionsBuilder` + `toJson()` + gating in
  `MainActivity.updateViewWithOptions()` / fragments / sync queue (`NetworkRepositoryImpl` lines ~244-251).
- **DB**: Room v26; each schema change = migration + schema export.
- **Known risk to guard in every catalog-edit feature**: sync cleanup deletes rows
  `WHERE timestamp < batchTimestamp` and server pushes `upsert` blindly. Locally created/modified rows
  (`is_modified=1`) must be **excluded from cleanup deletes and protected from upsert clobber**, otherwise
  a sync that runs before the upload ack will silently destroy the user's unsent edits.

---

## Feature 1 — Create new clients, sync to 1C

**Option key:** `editClients` (default `false`)

**Scope:** agent creates a new client (and optionally edits ones he created); the client is uploaded to 1C.

**DB (migration 26→27):**
- `clients`: add `is_modified INTEGER NOT NULL DEFAULT 0` (0 = server-owned, 1 = pending upload).
- Exclude `is_modified=1` from `deleteClients WHERE timestamp < :time` and from server upsert overwrite.

**Data/domain:**
- `Client.toMap()` with `type = "client"` (new constant reuses `Constants.DATA_CLIENT`).
- `ClientRepository`: add `saveClient(Client)` (upsert with `is_modified=1`, app-generated UUID guid).
- Use cases: `CreateClientUseCase`, `SaveClientUseCase`, `ValidateClientUseCase` (description required; phone/address optional).

**Upload:** add clients to `sendDocuments()` (HTTP) and `uploadDocuments()` (relay, type `"client"`);
on ack → `is_modified=0`. App-generated GUID stays authoritative (same as orders).

**UI:**
- `ClientEditFragment` + ViewModel: description, phone, address, notes, (optional: group picker, price type).
- "New client" menu action in `ClientListFragment`; optionally "Edit" in `ClientFragment` for `is_modified=1` clients.
- Nav graph destinations; visibility gated by `options.editClients`.

**1C/relay side:** accept upload type `client`, create counterparty, treat app GUID as external id; emit `editClients` in options JSON.

**Open questions for 1C author:** minimum required fields for a valid counterparty; is editing of *existing* (server-owned) clients wanted or only creation?

**Size:** M-L (new edit screen + upload path + cleanup protection).

---

## Feature 2 — Create/edit discounts, sync to 1C

**Option key:** `editDiscounts` (default `false`; implies `complexDiscounts` UI is on)

**DB (migration):**
- `discounts`: add `is_modified INTEGER NOT NULL DEFAULT 0`.
- Same cleanup/upsert protection as Feature 1 (`deleteDiscounts`, `upsertDiscountList`).

**Data/domain:**
- `Discount.toMap()` with `type = "discount"` (fields: `client_guid`, `item_guid`, `discount`, matching `Discount.build()` naming).
- `DiscountRepository` (new, or extend ClientRepository): `saveDiscount`, `getDiscount(client, product)`.
- Use cases: `SaveDiscountUseCase`, `ValidateDiscountUseCase`.

**Upload:** both transports, ack resets `is_modified=0`.

**UI:**
- Extend `ClientDiscountsFragment`: FAB/menu "add discount", tap-to-edit existing row.
- Edit dialog/screen: product or product group picker (reuse product list selection flow), percentage input
  (respect the sign convention: negative = discount, positive = surcharge — probably present it to the user
  as "discount %" and negate internally to avoid confusion).
- Support the client-wide discount case (`product_guid = ""`).

**Open questions:** is deleting a discount required (needs a tombstone/`deleted` flag in the payload — 1C must handle it)? Who wins if 1C pushes a different value while a local edit is pending (suggest: local pending wins until acked)?

**Size:** M.

---

## Feature 3 — "Create delivery request" checkbox on order

**Option key:** `useDeliveryRequests` (default `false`)

The cheapest feature: all real logic (creating the delivery request document in 1C with requisites from
the counterparty's main subdivision, status "відправляємо") lives in 1C. The app only carries a flag.

**DB (migration):** `orders`: add `is_delivery INTEGER NOT NULL DEFAULT 0`.

**App changes:**
- `Order` entity field + include `is_delivery` in `Order.toMap()`.
- Checkbox in `OrderFragment` next to fiscal/return flags, visible only when `options.useDeliveryRequests`;
  read-only after send (same as other order fields).
- Show the flag in order list row (optional, nice-to-have).

**1C side:** read `is_delivery` from the order payload, auto-create the delivery request.

**Size:** S.

---

## Feature 4 — Product photos (фотографії номенклатури) — ✅ DONE (app side)

**Clarified** (see `~/projects/rich-hills/tasks/images-via-relay-plan.md`): the feature is
**delivering product images to the app through the relay**, not photo capture. 1C uploads image
bytes to sphynx (`PUT /api/v1/images/{guid}`), the `image` catalog record carries the ready
absolute URL (`https://.../api/v1/images/{guid}?v={time}`), and the app downloads via Glide
(`GET /api/v1/images/{guid}` with device auth `Bearer {app_key}:{device_uuid}`).

**Option key:** existing `loadImages` (already gates image loading — no new option needed).

**App change (implemented July 2026):** `GlideImageLoadingManager.createHeaders()` now sends
`Authorization: Bearer {apiKey}:{deviceUuid}` for relay accounts (`account.isRelayRest()`) instead
of Basic auth; direct-1C HTTP accounts keep Basic. `ApiKeyProvider` injected via `GlobalModule`.
Everything else already worked: `url` from the `image` record is used as-is, Glide caches by full
URL so `?v=` version bumps invalidate the cache, `product_images` parsing unchanged.

**Remaining work is 1C-side** (rich-hills): PUT bytes to sphynx, use `data.url` from the response,
delta register `AV_ВыгруженныеКартинки`. Backend (sphynx) is done.

---

## Feature 5 — Cash receipt creation & sync (надходження в касу)

**Mostly already exists.** The `Cash` document *is* an incoming cash receipt: creatable in the app
(client, company, sum, notes, fiscal flag), validated, uploaded on **both** transports, marked sent on ack.

**Plan = audit + gap-fix + option gating:**
1. **Clarify with the 1C author what's missing** — most likely the gap is on the 1C/relay side
   (the base doesn't yet create the ПКО from the uploaded `cash` payload), not in the app.
2. **Option-gate it**: `cashListFragment` is currently always visible in the drawer. Add `useCash`
   option (default `true` to not break existing users) → hide drawer item + "create cash from client" action when off.
3. **Fix known relay discrepancy**: relay ack path (`markCashSentViaWebSocket`) only sets `is_sent=1`,
   while HTTP path sets `is_processed=2` and stores server `status`. Align them (applies to orders too).

**Size:** S (app side).

---

## Feature 6 — Report "Розрахунки з контрагентами" (settlements report)

**Option key:** `debtReport` (default `false`)

**Good news:** the data is already synced. The `debts` table contains per-client aggregate rows
(`is_total=1`, `sum`, `sum_in`, `sum_out`, `company_guid`) that **no UI currently consumes** — all existing
debt screens filter `is_total=0` per client. No schema change, no upload, no new sync type.

**App changes (net-new screen):**
- DAO query: `debts WHERE is_total=1` JOIN `clients` (description), filtered by current account,
  optional company filter when `useCompanies`, ordered by client name or debt size.
- `SettlementsReportFragment` + ViewModel: list of counterparties with balance (in/out/total),
  search box, grand-total footer; row tap → navigate to that client's `ClientDebtsFragment` (existing).
- Drawer entry in the `header_service` (or documents) group of `navigation_drawer.xml`,
  visibility toggled in `MainActivity.updateViewWithOptions()` by `options.debtReport`.

**Size:** S-M.

---

## Cross-cutting work (do once, first)

1. **Option keys**: add all 6 fields to `UserOptions` + `UserOptionsBuilder` + `toJson()` in one pass;
   agree key names with the 1C/relay side so the server starts emitting them.
2. **Catalog-edit upload framework** (needed by Features 1, 2, 4): the ClientLocation `is_modified`
   pattern generalized — pending-row queries, upload wiring in both transports, ack handlers,
   cleanup/upsert protection for locally-modified rows. Build it once with Feature 1, reuse for 2 and 4.
3. **DB migrations**: one migration per implementation step (27, 28, …), schema export each time.
4. **Docs**: update CLAUDE.md (WebSocket → REST relay reality) and note new option keys.
5. **Tests**: fake repositories exist — add use-case tests per feature (validate/save), DAO migration tests.

## Suggested implementation order

| Step | Feature | Why this order |
|------|---------|----------------|
| 1 | #3 Delivery request checkbox | Smallest, unblocks 1C-side work early |
| 2 | #6 Settlements report | Data already there, pure UI, quick client-visible win |
| 3 | #5 Cash audit + option gating + relay ack fix | Small; ack fix also benefits orders |
| 4 | #1 New clients (+ catalog-edit upload framework) | Framework is built here |
| 5 | #2 Discount editing | Reuses framework + client screens from step 4 |
| 6 | #4 Product photos | Reuses framework; pending clarification of scope |

## Questions to send back to the client (1C author)

1. **#4**: "фотографії номенклатури" — це зйомка фото товарів агентом з передачею в базу, чи щось інше? (Показ фото з бази вже працює через `loadImages`.)
2. **#5**: "надходження в касу" вже створюється і передається з додатку (документ Cash). Що саме не працює / чого бракує — обробка на стороні 1С?
3. **#1**: які реквізити обовʼязкові для нового контрагента в 1С? Чи достатньо GUID, згенерованого додатком? Потрібно тільки створення нових чи також редагування існуючих?
4. **#2**: чи потрібне видалення знижок з додатку, чи тільки створення/зміна? Хто "переможе" при конфлікті (локальна зміна vs дані з 1С)?
5. Назви ключів опцій: `editClients`, `editDiscounts`, `useDeliveryRequests`, `editProductImages`, `useCash`, `debtReport` — узгодити з боку сервера/1С.
