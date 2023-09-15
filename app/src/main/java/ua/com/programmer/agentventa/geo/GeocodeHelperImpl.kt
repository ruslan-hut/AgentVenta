package ua.com.programmer.agentventa.geo

import android.content.Context
import android.location.Geocoder
import java.util.Locale

class GeocodeHelperImpl constructor(
    private val context: Context
): GeocodeHelper {
    override suspend fun getAddress(lat: Double, lng: Double): String {
        val geocoder = Geocoder(context, Locale.getDefault())
        val addresses = geocoder.getFromLocation(lat, lng, 1)
        if (!addresses.isNullOrEmpty()) {
            val address = addresses[0]
            val sb = StringBuilder()
            for (i in 0..address.maxAddressLineIndex) {
                sb.append(address.getAddressLine(i)).append(" ")
            }
            return sb.toString()
        }
        return ""
    }
}