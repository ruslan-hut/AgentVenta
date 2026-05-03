package ua.com.programmer.agentventa.infrastructure.location

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.location.Location
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import ua.com.programmer.agentventa.R
import ua.com.programmer.agentventa.data.local.entity.LocationHistory
import ua.com.programmer.agentventa.domain.repository.LocationRepository
import ua.com.programmer.agentventa.presentation.main.MainActivity
import ua.com.programmer.agentventa.utility.Constants
import javax.inject.Inject

@AndroidEntryPoint
class LocationUpdatesService: Service() {

    companion object {
        private const val CHANNEL_ID = "location_updates"
        private const val NOTIFICATION_ID = 1001
    }

    @Inject
    lateinit var repo: LocationRepository
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var lastSavedLocation: Location? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(locationResult: LocationResult) {

            locationResult.locations.forEach { location ->

                if (location.accuracy > Constants.LOCATION_MIN_ACCURACY) return@forEach

                if (lastSavedLocation != null) {
                    val distance = location.distanceTo(lastSavedLocation!!)
                    if (distance < Constants.LOCATION_MIN_DISTANCE) return@forEach
                }
                lastSavedLocation = location

                serviceScope.launch {
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
        startInForeground()
        startLocationUpdates()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startInForeground()
        return START_STICKY
    }

    private fun startInForeground() {
        createNotificationChannel()
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.title_location_updates))
            .setContentText(getString(R.string.location_current))
            .setSmallIcon(R.drawable.sharp_map_24)
            .setOngoing(true)
            .setContentIntent(contentIntent)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun createNotificationChannel() {
        val manager = getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel_locations),
            NotificationManager.IMPORTANCE_LOW
        )
        manager.createNotificationChannel(channel)
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
            Log.e("AV-LocationService","request updates: $e")
        }
    }

    override fun onDestroy() {
        fusedLocationClient.removeLocationUpdates(locationCallback)
        serviceScope.cancel()
        super.onDestroy()
        Log.w("AV-LocationService", "service destroyed")
    }
}