package ua.com.programmer.agentventa.infrastructure.location

import android.app.Service
import android.content.Intent
import android.location.Location
import android.os.IBinder
import android.os.Looper
import android.util.Log
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import ua.com.programmer.agentventa.data.local.entity.LocationHistory
import ua.com.programmer.agentventa.domain.repository.LocationRepository
import ua.com.programmer.agentventa.utility.Constants
import javax.inject.Inject

@AndroidEntryPoint
class LocationUpdatesService: Service() {

    @Inject
    lateinit var repo: LocationRepository
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var lastSavedLocation: Location? = null

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(locationResult: LocationResult) {

            locationResult.locations.forEach { location ->

                if (location.accuracy > Constants.LOCATION_MIN_ACCURACY) return@forEach

                if (lastSavedLocation != null) {
                    val distance = location.distanceTo(lastSavedLocation!!)
                    if (distance < Constants.LOCATION_MIN_DISTANCE) return@forEach
                }
                lastSavedLocation = location

                CoroutineScope(Dispatchers.IO).launch {
                    repo.saveLocation(LocationHistory(
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
                    ))
                }
            }
        }
    }

    override fun onBind(p0: Intent?): IBinder? {
        return null
    }

    override fun onCreate() {
        super.onCreate()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        startLocationUpdates()
    }

    private fun startLocationUpdates() {
        val updateInterval = (1 * 10000).toLong()
        //val maxWaitTime = updateInterval * 10

        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, updateInterval)
            //.setDurationMillis(maxWaitTime)
            .build()

        try {
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                Looper.getMainLooper()
            )
        } catch (e: SecurityException) {
            Log.e("LocationService","request updates: $e")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.w("LocationService", "service destroyed")
    }
}