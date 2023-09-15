package ua.com.programmer.agentventa.geo

interface GeocodeHelper {
    suspend fun getAddress(lat: Double, lng: Double): String
}