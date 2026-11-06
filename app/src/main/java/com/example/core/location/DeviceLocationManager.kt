package com.example.core.location

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Structured geographic location with accuracy bounds and London postal context.
 */
data class GeoLocation(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float = 0f,
    val isFreshGps: Boolean = false,
    val areaName: String = "Detecting Live Location..."
)

/**
 * Enterprise Location Manager that queries real device GPS coordinates
 * via Google Play Services FusedLocationProviderClient with zero extra dependencies.
 * Respects DPDP privacy: strictly on-demand, no continuous background tracking.
 */
class DeviceLocationManager(private val context: Context) {

    private val fusedLocationClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    // Initial state before live GPS fix is received
    private val _currentLocation = MutableStateFlow(
        GeoLocation(
            latitude = 0.0,
            longitude = 0.0,
            accuracyMeters = 0.0f,
            isFreshGps = false,
            areaName = "Detecting Live Location..."
        )
    )
    val currentLocation: StateFlow<GeoLocation> = _currentLocation.asStateFlow()

    // Backward-compatible coordinate pair flow
    private val _currentCoordinates = MutableStateFlow(Pair(0.0, 0.0))
    val currentCoordinates: StateFlow<Pair<Double, Double>> = _currentCoordinates.asStateFlow()

    fun hasLocationPermission(): Boolean {
        val fineLocation = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        val coarseLocation = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        return fineLocation || coarseLocation
    }

    /**
     * Queries accurate GPS coordinates on-demand with configurable precision.
     * Use highAccuracy = true for price submission and store check-in (<30m radius).
     * Use highAccuracy = false for browsing feeds to minimize battery drain.
     */
    @SuppressLint("MissingPermission")
    suspend fun getAccurateLocation(highAccuracy: Boolean = true): GeoLocation {
        if (!hasLocationPermission()) {
            return _currentLocation.value
        }

        val priority = if (highAccuracy) {
            Priority.PRIORITY_HIGH_ACCURACY
        } else {
            Priority.PRIORITY_BALANCED_POWER_ACCURACY
        }

        return suspendCancellableCoroutine { continuation ->
            val cts = CancellationTokenSource()
            continuation.invokeOnCancellation { cts.cancel() }

            fusedLocationClient.getCurrentLocation(priority, cts.token)
                .addOnSuccessListener { location: Location? ->
                    if (location != null) {
                        val geo = GeoLocation(
                            latitude = location.latitude,
                            longitude = location.longitude,
                            accuracyMeters = location.accuracy,
                            isFreshGps = true,
                            areaName = resolveAreaName(location.latitude, location.longitude)
                        )
                        _currentLocation.value = geo
                        _currentCoordinates.value = Pair(geo.latitude, geo.longitude)
                        continuation.resume(geo)
                    } else {
                        // Fall back to cached lastLocation
                        fusedLocationClient.lastLocation.addOnSuccessListener { lastLoc: Location? ->
                            if (lastLoc != null) {
                                val geo = GeoLocation(
                                    latitude = lastLoc.latitude,
                                    longitude = lastLoc.longitude,
                                    accuracyMeters = lastLoc.accuracy,
                                    isFreshGps = false,
                                    areaName = resolveAreaName(lastLoc.latitude, lastLoc.longitude)
                                )
                                _currentLocation.value = geo
                                _currentCoordinates.value = Pair(geo.latitude, geo.longitude)
                                continuation.resume(geo)
                            } else {
                                continuation.resume(_currentLocation.value)
                            }
                        }.addOnFailureListener {
                            continuation.resume(_currentLocation.value)
                        }
                    }
                }
                .addOnFailureListener {
                    continuation.resume(_currentLocation.value)
                }
        }
    }

    @SuppressLint("MissingPermission")
    suspend fun refreshLocation(): Pair<Double, Double> {
        val geo = getAccurateLocation(highAccuracy = false)
        return Pair(geo.latitude, geo.longitude)
    }

    fun updateCoordinates(lat: Double, lon: Double, areaName: String? = null) {
        val resolvedName = areaName ?: resolveAreaName(lat, lon)
        val geo = GeoLocation(
            latitude = lat,
            longitude = lon,
            accuracyMeters = 5.0f,
            isFreshGps = false,
            areaName = resolvedName
        )
        _currentLocation.value = geo
        _currentCoordinates.value = Pair(lat, lon)
    }

    /**
     * Resolves human-readable locality and city anywhere in the world using Android Geocoder.
     */
    private fun resolveAreaName(lat: Double, lon: Double): String {
        if (lat == 0.0 && lon == 0.0) return "Detecting Live Location..."
        return try {
            val geocoder = android.location.Geocoder(context, java.util.Locale.getDefault())
            @Suppress("DEPRECATION")
            val addresses = geocoder.getFromLocation(lat, lon, 1)
            if (!addresses.isNullOrEmpty()) {
                val addr = addresses[0]
                val subLocality = addr.subLocality ?: addr.locality ?: addr.subAdminArea
                val city = addr.locality ?: addr.adminArea ?: addr.countryName
                if (!subLocality.isNullOrBlank() && !city.isNullOrBlank() && subLocality != city) {
                    "$subLocality, $city"
                } else if (!subLocality.isNullOrBlank()) {
                    subLocality
                } else if (!city.isNullOrBlank()) {
                    city
                } else {
                    "Live Location (%.3f, %.3f)".format(lat, lon)
                }
            } else {
                "Live Location (%.3f, %.3f)".format(lat, lon)
            }
        } catch (_: Exception) {
            "Live Location (%.3f, %.3f)".format(lat, lon)
        }
    }
}
