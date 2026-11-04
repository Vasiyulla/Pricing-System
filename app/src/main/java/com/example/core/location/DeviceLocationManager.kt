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
 * Enterprise Location Manager that queries real device GPS coordinates
 * via Google Play Services FusedLocationProviderClient with zero extra dependencies.
 */
class DeviceLocationManager(private val context: Context) {

    private val fusedLocationClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    // Default to Camden / Central London if permission not yet granted
    private val _currentCoordinates = MutableStateFlow(Pair(51.5074, -0.1278))
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

    @SuppressLint("MissingPermission")
    suspend fun refreshLocation(): Pair<Double, Double> {
        if (!hasLocationPermission()) {
            return _currentCoordinates.value
        }

        return suspendCancellableCoroutine { continuation ->
            val cts = CancellationTokenSource()
            continuation.invokeOnCancellation { cts.cancel() }

            fusedLocationClient.getCurrentLocation(
                Priority.PRIORITY_BALANCED_POWER_ACCURACY,
                cts.token
            ).addOnSuccessListener { location: Location? ->
                if (location != null) {
                    val coords = Pair(location.latitude, location.longitude)
                    _currentCoordinates.value = coords
                    continuation.resume(coords)
                } else {
                    fusedLocationClient.lastLocation.addOnSuccessListener { lastLoc: Location? ->
                        if (lastLoc != null) {
                            val coords = Pair(lastLoc.latitude, lastLoc.longitude)
                            _currentCoordinates.value = coords
                            continuation.resume(coords)
                        } else {
                            continuation.resume(_currentCoordinates.value)
                        }
                    }.addOnFailureListener {
                        continuation.resume(_currentCoordinates.value)
                    }
                }
            }.addOnFailureListener {
                continuation.resume(_currentCoordinates.value)
            }
        }
    }

    fun updateCoordinates(lat: Double, lon: Double) {
        _currentCoordinates.value = Pair(lat, lon)
    }
}
