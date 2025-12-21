package ua.com.programmer.agentventa.infrastructure.websocket

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import ua.com.programmer.agentventa.infrastructure.logger.Logger
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Monitors network connectivity state using ConnectivityManager.
 * Provides reactive StateFlow for observing connection changes.
 */
@Singleton
class NetworkConnectivityMonitor @Inject constructor(
    @ApplicationContext private val context: Context,
    private val logger: Logger
) {
    private val TAG = "NetworkMonitor"

    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val _isConnected = MutableStateFlow(checkCurrentConnectivity())
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var isMonitoring = false

    /**
     * Start monitoring network connectivity changes.
     * Safe to call multiple times - will only register once.
     */
    fun startMonitoring() {
        if (isMonitoring) return

        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .addCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            .build()

        networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                logger.d(TAG, "Network available")
                _isConnected.value = true
            }

            override fun onLost(network: Network) {
                logger.d(TAG, "Network lost")
                // Check if there's still another active network
                _isConnected.value = checkCurrentConnectivity()
            }

            override fun onCapabilitiesChanged(
                network: Network,
                networkCapabilities: NetworkCapabilities
            ) {
                val hasInternet = networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                val isValidated = networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                _isConnected.value = hasInternet && isValidated
            }
        }

        try {
            connectivityManager.registerNetworkCallback(request, networkCallback!!)
            isMonitoring = true
            logger.d(TAG, "Network monitoring started")
        } catch (e: Exception) {
            logger.e(TAG, "Failed to register network callback: ${e.message}")
        }
    }

    /**
     * Stop monitoring network connectivity changes.
     */
    fun stopMonitoring() {
        if (!isMonitoring) return

        networkCallback?.let {
            try {
                connectivityManager.unregisterNetworkCallback(it)
                logger.d(TAG, "Network monitoring stopped")
            } catch (e: Exception) {
                logger.e(TAG, "Failed to unregister network callback: ${e.message}")
            }
        }
        networkCallback = null
        isMonitoring = false
    }

    /**
     * Check if network is currently available (synchronous check).
     */
    fun isNetworkAvailable(): Boolean {
        return checkCurrentConnectivity()
    }

    private fun checkCurrentConnectivity(): Boolean {
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false

        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }
}
