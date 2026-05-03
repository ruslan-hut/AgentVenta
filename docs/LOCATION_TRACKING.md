# Location Tracking

## Decision

The app tracks GPS **only while in the foreground**. There is no background or foreground service for location.

The previous implementation used a foreground service (`LocationUpdatesService`) declared with `foregroundServiceType="location"` and the `FOREGROUND_SERVICE_LOCATION` permission. That permission requires a Play Console review and a "Location in background" prominent disclosure flow on every release. The product does not need background tracking — agents use the app actively while visiting clients — so the cost outweighs the benefit.

Removed in favor of a lightweight in-process tracker tied to `MainActivity` lifecycle.

## Implementation

- `infrastructure/location/LocationTracker.kt` — `@Singleton`, injected via Hilt. Wraps `FusedLocationProviderClient` with the same filters used previously (`Constants.LOCATION_MIN_ACCURACY`, `Constants.LOCATION_MIN_DISTANCE`, 10s interval, `PRIORITY_HIGH_ACCURACY`). Saves accepted points via `LocationRepository`.
- `MainActivity` injects `LocationTracker` and calls:
  - `start()` on `onStart()` if `options.locations` is enabled and `ACCESS_FINE_LOCATION` is granted.
  - `start()` after the runtime permission is granted, and after `updateViewWithOptions` flips `options.locations` on.
  - `stop()` on `onStop()`, and when `options.locations` flips off.
- `start()` / `stop()` are idempotent; `start()` is a no-op without permission.

## Manifest

The following are **not** declared and must not be reintroduced unless the product genuinely needs background tracking and we are prepared to handle the Play Store review:

- `android.permission.FOREGROUND_SERVICE`
- `android.permission.FOREGROUND_SERVICE_LOCATION`
- `<service android:name=".infrastructure.location.LocationUpdatesService" .../>`

`ACCESS_FINE_LOCATION` and `ACCESS_COARSE_LOCATION` remain (runtime-requested when `options.locations` is enabled).

## Behavior changes vs. the old service

| Aspect | Old (foreground service) | New (LocationTracker) |
|---|---|---|
| Tracks while app in background / screen off | Yes | No |
| Tracks while another app is in foreground | Yes | No |
| Survives `MainActivity` destruction | Yes (until service killed) | No (stops with activity) |
| Persistent notification | Yes | No |
| Play Console "Location in background" review | Required | Not required |

If a future requirement reintroduces background tracking, the foreground-service path needs to come back along with the Play Store declaration — do not work around it with `WorkManager` periodic location pulls or alarms; those run afoul of the same policy.
