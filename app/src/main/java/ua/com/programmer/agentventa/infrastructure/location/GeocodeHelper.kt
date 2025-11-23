package ua.com.programmer.agentventa.infrastructure.location

interface GeocodeHelper {
    suspend fun getAddress(lat: Double, lng: Double): String
}