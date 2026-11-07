package com.example.core.location

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.Looper
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
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
     * Continuous real-time location stream using callbackFlow.
     * Uses displacement-based and time-based throttling (e.g. 25m or 5s) to preserve battery.
     * Active only while the Flow is being collected (e.g. while HomeScreen is in the foreground).
     */
    @SuppressLint("MissingPermission")
    fun getLocationUpdates(
        minDisplacementMeters: Float = 25f,
        intervalMillis: Long = 5000L
    ): Flow<GeoLocation> = callbackFlow {
        if (!hasLocationPermission()) {
            trySend(_currentLocation.value)
            close()
            return@callbackFlow
        }

        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, intervalMillis)
            .setMinUpdateDistanceMeters(minDisplacementMeters)
            .setMinUpdateIntervalMillis(intervalMillis / 2)
            .build()

        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val loc = result.lastLocation ?: return
                val geo = GeoLocation(
                    latitude = loc.latitude,
                    longitude = loc.longitude,
                    accuracyMeters = loc.accuracy,
                    isFreshGps = true,
                    areaName = resolveAreaName(loc.latitude, loc.longitude)
                )
                _currentLocation.value = geo
                _currentCoordinates.value = Pair(geo.latitude, geo.longitude)
                trySend(geo)
            }
        }

        fusedLocationClient.requestLocationUpdates(request, callback, Looper.getMainLooper())

        awaitClose {
            fusedLocationClient.removeLocationUpdates(callback)
        }
    }

    /**
     * Computes real-time Haversine distance in meters between two lat/lon coordinates.
     */
    fun calculateDistanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Float {
        val results = FloatArray(1)
        Location.distanceBetween(lat1, lon1, lat2, lon2, results)
        return results[0]
    }

    /**
     * Resolves human-readable locality and city anywhere in the world using Android Geocoder.
     */
    fun resolveAreaName(lat: Double, lon: Double): String {
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

    /**
     * Resolves both short locality name and full street/building address.
     */
    fun resolveFullAddress(lat: Double, lon: Double): Pair<String, String> {
        if (lat == 0.0 && lon == 0.0) return Pair("Detecting Location...", "Real-time GPS")
        return try {
            val geocoder = android.location.Geocoder(context, java.util.Locale.getDefault())
            @Suppress("DEPRECATION")
            val addresses = geocoder.getFromLocation(lat, lon, 1)
            if (!addresses.isNullOrEmpty()) {
                val addr = addresses[0]
                val subLocality = addr.subLocality ?: addr.locality ?: addr.subAdminArea ?: ""
                val city = addr.locality ?: addr.adminArea ?: addr.countryName ?: ""
                val locality = if (subLocality.isNotBlank() && city.isNotBlank() && subLocality != city) {
                    "$subLocality, $city"
                } else if (subLocality.isNotBlank()) {
                    subLocality
                } else if (city.isNotBlank()) {
                    city
                } else {
                    "Live Location"
                }
                val fullAddress = addr.getAddressLine(0) ?: "$subLocality, $city"
                Pair(locality, fullAddress)
            } else {
                Pair("Live Location", "Lat: %.4f, Lon: %.4f".format(lat, lon))
            }
        } catch (_: Exception) {
            Pair("Live Location", "Lat: %.4f, Lon: %.4f".format(lat, lon))
        }
    }

    /**
     * Searches real-world places, streets, and apartments by text query (Blinkit/Zomato style).
     */
    fun searchAddresses(query: String, maxResults: Int = 5): List<com.example.core.model.SavedAddress> {
        if (query.isBlank()) return emptyList()
        return try {
            val geocoder = android.location.Geocoder(context, java.util.Locale.getDefault())
            @Suppress("DEPRECATION")
            val addresses = geocoder.getFromLocationName(query, maxResults)
            addresses?.map { addr ->
                val feature = addr.featureName ?: ""
                val thoroughfare = addr.thoroughfare ?: ""
                val subLocality = addr.subLocality ?: ""
                val locality = addr.locality ?: addr.adminArea ?: ""
                val fullLine = addr.getAddressLine(0) ?: listOf(feature, thoroughfare, subLocality, locality).filter { it.isNotBlank() }.joinToString(", ")
                val shortName = when {
                    feature.isNotBlank() && subLocality.isNotBlank() && feature != subLocality -> "$feature, $subLocality"
                    subLocality.isNotBlank() -> subLocality
                    locality.isNotBlank() -> locality
                    feature.isNotBlank() -> feature
                    else -> fullLine.take(30)
                }
                com.example.core.model.SavedAddress(
                    label = shortName,
                    addressLine = fullLine,
                    locality = locality.ifBlank { subLocality },
                    latitude = addr.latitude,
                    longitude = addr.longitude
                )
            } ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }
}
