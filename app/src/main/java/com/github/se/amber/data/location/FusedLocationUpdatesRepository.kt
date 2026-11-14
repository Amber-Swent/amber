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
import android.os.SystemClock
import android.util.Log
import androidx.core.content.ContextCompat
import com.github.se.amber.model.location.DevicePosition
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
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

    // Set once a new position arrived: the last known one must not replace it
    val hasNewPosition = AtomicBoolean(false)
    val callback =
        object : LocationCallback() {
          override fun onLocationResult(result: LocationResult) {
            result.lastLocation?.let {
              hasNewPosition.set(true)
              trySend(LocationUpdate.Position(it.toDevicePosition()))
            }
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
    val setting = locationManager.currentSetting()
    trySend(setting)
    try {
      client.requestLocationUpdates(REQUEST, callback, Looper.getMainLooper()).await()
      // While the first new position is awaited, which can take minutes without internet, show
      // the last known one if it is recent; never when the location is off. A new position landing
      // between the check and the send would be followed by the old one, until the next update
      // a few seconds later: harmless, so no lock
      if (setting == LocationUpdate.Searching) {
        recentLastKnownPosition()?.let {
          if (!hasNewPosition.get()) trySend(LocationUpdate.Position(it))
        }
      }
      awaitClose()
    } finally {
      client.removeLocationUpdates(callback)
      context.unregisterReceiver(settingsReceiver)
    }
  }

  /**
   * The phone's last known position, if it is at most [LAST_KNOWN_MAX_AGE_MILLIS] old; null if
   * there is none, it is older, or it can't be read (it's only a head start, never an error).
   */
  @SuppressLint("MissingPermission") // only called once the permission was checked
  private suspend fun recentLastKnownPosition(): DevicePosition? {
    val location =
        try {
          client.lastLocation.await()
        } catch (e: CancellationException) {
          throw e
        } catch (e: Exception) {
          Log.w(TAG, "Couldn't read the last known position", e)
          null
        } ?: return null
    val ageMillis =
        TimeUnit.NANOSECONDS.toMillis(
            SystemClock.elapsedRealtimeNanos() - location.elapsedRealtimeNanos
        )
    if (ageMillis > LAST_KNOWN_MAX_AGE_MILLIS) return null
    return location.toDevicePosition().copy(isLastKnown = true)
  }

  private fun LocationManager.currentSetting(): LocationUpdate =
      if (isLocationEnabled) LocationUpdate.Searching else LocationUpdate.LocationOff

  private companion object {
    val LOCATION_PERMISSIONS =
        listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)

    const val INTERVAL_MILLIS = 5_000L // walking pace: a few meters between positions
    const val MIN_INTERVAL_MILLIS = 2_000L
    const val MIN_DISTANCE_METERS = 5f // ignore the jitter of a phone standing still

    /** The oldest last known position shown while waiting: about 1 km at walking pace. */
    val LAST_KNOWN_MAX_AGE_MILLIS = TimeUnit.MINUTES.toMillis(10)

    const val TAG = "FusedLocationUpdates"

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
