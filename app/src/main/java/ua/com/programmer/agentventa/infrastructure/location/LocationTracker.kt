package ua.com.programmer.agentventa.infrastructure.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import ua.com.programmer.agentventa.data.local.entity.LocationHistory
import ua.com.programmer.agentventa.domain.repository.LocationRepository
import ua.com.programmer.agentventa.utility.Constants
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Foreground-only location tracker. Runs while the host activity is started.
 * Replaces the previous foreground service so the app no longer needs the
 * FOREGROUND_SERVICE_LOCATION permission.
 */
@Singleton
class LocationTracker @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repo: LocationRepository,
) {

    private val client: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var lastSavedLocation: Location? = null
    private var running = false

    private val callback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            result.locations.forEach { location ->
                if (location.accuracy > Constants.LOCATION_MIN_ACCURACY) return@forEach
                lastSavedLocation?.let {
                    if (location.distanceTo(it) < Constants.LOCATION_MIN_DISTANCE) return@forEach
                }
                lastSavedLocation = location
                scope.launch {
                    repo.saveLocation(
                        LocationHistory(
                            time = location.time,
                            accuracy = location.accuracy.toDouble(),
                            altitude = location.altitude,
                            bearing = location.bearing.toDouble(),
                            latitude = location.latitude,
                            longitude = location.longitude,
                            provider = location.provider ?: "",
                            speed = location.speed.toDouble(),
                            distance = 0.0,
                            pointName = "",
                            extra = ""
                        )
                    )
                }
            }
        }
    }

    fun start() {
        if (running) return
        if (ContextCompat.checkSelfPermission(
                context, Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) return

        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 10_000L).build()
        try {
            client.requestLocationUpdates(request, callback, Looper.getMainLooper())
            running = true
        } catch (e: SecurityException) {
            Log.e("AV-LocationTracker", "request updates: $e")
        }
    }

    fun stop() {
        if (!running) return
        client.removeLocationUpdates(callback)
        running = false
    }
}
