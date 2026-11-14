// Written by Neha Chakraborty, with assistance from Claude (Anthropic) via Claude Code.

package com.github.se.amber.data.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Looper
import androidx.core.content.ContextCompat
import com.github.se.amber.model.location.DevicePosition
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * The phone's position from Google Play services' fused location, asked at high accuracy so it uses
 * the GPS, which works without internet.
 *
 * @param context the app's context, for the permission and the location settings.
 * @param client gives the positions; tests pass a mock.
 * @param locationManager tells whether the location is on; tests use Robolectric's. Null on a phone
 *   without a location service, whose flow then stops with an [IllegalStateException].
 */
class FusedLocationUpdatesRepository(
    private val context: Context,
    private val client: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context),
    private val locationManager: LocationManager? =
        context.getSystemService(LocationManager::class.java),
) : LocationUpdatesRepository {

  override fun hasPermission(): Boolean = LOCATION_PERMISSIONS.any {
    ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
  }

  // The permission is checked first; a permission taken back later throws a SecurityException
  @SuppressLint("MissingPermission")
  override fun observeLocation(): Flow<LocationUpdate> = callbackFlow {
    if (!hasPermission()) throw SecurityException("The location permission isn't granted")
    val locationManager =
        locationManager ?: throw IllegalStateException("This phone has no location service")

    val callback =
        object : LocationCallback() {
          override fun onLocationResult(result: LocationResult) {
            result.lastLocation?.let { trySend(LocationUpdate.Position(it.toDevicePosition())) }
          }
        }
    // The location turned off or on in the settings; once on, the positions come back by
    // themselves
    val settingsReceiver =
        object : BroadcastReceiver() {
          override fun onReceive(context: Context, intent: Intent) {
            trySend(locationManager.currentSetting())
          }
        }

    // Registered before reading the setting, so a change in between isn't missed
    ContextCompat.registerReceiver(
        context,
        settingsReceiver,
        IntentFilter(LocationManager.PROVIDERS_CHANGED_ACTION),
        ContextCompat.RECEIVER_NOT_EXPORTED,
    )
    trySend(locationManager.currentSetting())
    try {
      client.requestLocationUpdates(REQUEST, callback, Looper.getMainLooper()).await()
      awaitClose()
    } finally {
      client.removeLocationUpdates(callback)
      context.unregisterReceiver(settingsReceiver)
    }
  }

  private fun LocationManager.currentSetting(): LocationUpdate =
      if (isLocationEnabled) LocationUpdate.Searching else LocationUpdate.LocationOff

  private companion object {
    val LOCATION_PERMISSIONS =
        listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)

    const val INTERVAL_MILLIS = 5_000L // walking pace: a few meters between positions
    const val MIN_INTERVAL_MILLIS = 2_000L
    const val MIN_DISTANCE_METERS = 5f // ignore the jitter of a phone standing still

    val REQUEST: LocationRequest =
        LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, INTERVAL_MILLIS)
            .setMinUpdateIntervalMillis(MIN_INTERVAL_MILLIS)
            .setMinUpdateDistanceMeters(MIN_DISTANCE_METERS)
            .build()
  }
}

private fun Location.toDevicePosition() =
    DevicePosition(
        lat = latitude,
        lng = longitude,
        accuracyMeters = if (hasAccuracy()) accuracy else null,
    )
