# WebSocket upload reliability — fixes checklist

Context: field devices stopped uploading orders while online. Root cause from
device logs: the connection FSM wedged in `Reconnecting(delayMs=8000, attempt=4)`
for ~19h with no live timer behind it — every sync was refused.

## Core fixes

- [x] **#1 Preempt `Reconnecting`** — `isOccupied()` no longer treats
  `Reconnecting` as busy, so an explicit `connect()`/`reconnect()` can cancel the
  stale timer and open a fresh socket. (`WebSocketRepositoryImpl.isOccupied`)
- [x] **#2 Self-healing watchdog** — `WebSocketConnectionManager.startWatchdog()`
  forces a reconnect when the state is an unchanged retryable failure across a
  full interval while a connection is wanted.
- [x] **#3 Upload awaits `Connected`** — `sendDocumentsViaWebSocket` waits for the
  real connected state (not a fixed `delay(500)`) before sending.
- [x] **#4 Dead-connection detection** — app-level pong/inbound-traffic timeout in
  `startPingScheduler`; `webSocket.cancel()` drives the normal reconnect path.
  (`WEBSOCKET_PONG_TIMEOUT = 75s`)
- [x] **#5 DNS storm coalescing** — `CachingDns` single-flights concurrent same-host
  lookups instead of each walking system→DoH→fallback.

## Code-review follow-ups (medium-effort review)

- [x] **R1 (correctness) Upload await stalls on terminal states** — `first { Connected }`
  never matches `Pending`/`LicenseError`/`Error(!canRetry)`, burning the full 20s
  and reporting a misleading timeout. Now awaits the settled state and surfaces the
  real reason. (`NetworkRepositoryImpl` upload path)
- [x] **R2 (efficiency/altitude) CachingDns lock across blocking I/O** — replaced the
  monitor-held-across-resolve with a per-host in-flight single-flight; followers join
  the leader's resolution without holding a lock across DNS/DoH I/O.
- [x] **R3 (correctness) `fallbackInUse` cross-host staleness** — made the fallback
  flag per-host; `lastLookupUsedFallback(host)` now reflects the queried host even on
  a memo/coalesced hit. (`CachingDns`, caller in `WebSocketRepositoryImpl.onFailure`)
- [x] **R4 (altitude) Duplicated scheduler loops** — extracted the shared
  `while(isActive){ delay; block }` scaffold into `launchLoop()` used by both the
  periodic check and the watchdog.
- [x] **R5 (reuse) Ad-hoc await-state blocks** — extracted `StateFlow<WebSocketState>.awaitState()`
  + `WebSocketState.isSettled()`; reused in the upload path, `checkDeviceApproval`,
  and `handleAccountChange`.

### Refuted in verification (no action)

- Watchdog backoff-reset false-positive — `Reconnecting.attempt` is part of value
  equality and increments every cycle, so identical snapshots only occur on a genuine
  wedge.
- Control-frame pong starving the watchdog — relay replies with an app-level `pong`
  text frame (`sphynx client.go:handlePing`), which reaches `onMessage`.
