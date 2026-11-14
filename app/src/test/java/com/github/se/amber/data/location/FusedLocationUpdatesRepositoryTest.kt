// Written by Neha Chakraborty, with assistance from Claude (Anthropic) via Claude Code.

package com.github.se.amber.data.location

import android.Manifest
import android.app.Application
import android.content.Intent
import android.location.Location
import android.location.LocationManager
import android.os.Looper
import android.os.SystemClock
import com.github.se.amber.model.location.DevicePosition
import com.github.se.amber.model.location.TestPositions.GENEVA_CORNAVIN
import com.github.se.amber.model.location.TestPositions.GPS_ACCURACY_METERS
import com.github.se.amber.model.location.TestPositions.LAUSANNE_FLON
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.TaskCompletionSource
import com.google.android.gms.tasks.Tasks
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class FusedLocationUpdatesRepositoryTest {

  private val app: Application = RuntimeEnvironment.getApplication()
  private val locationManager = app.getSystemService(LocationManager::class.java)
  private val client = mockk<FusedLocationProviderClient>()
  private val request = slot<LocationRequest>()
  private val callback = slot<LocationCallback>()
  private val repository = FusedLocationUpdatesRepository(app, client, locationManager)

  @Before
  fun setUp() {
    shadowOf(app).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION)
    shadowOf(locationManager).setLocationEnabled(true)
    every {
      client.requestLocationUpdates(capture(request), capture(callback), any<Looper>())
    } returns Tasks.forResult(null)
    every { client.removeLocationUpdates(any<LocationCallback>()) } returns Tasks.forResult(null)
    every { client.lastLocation } returns Tasks.forResult(null)
  }

  /**
   * The Android location of this position, with no accuracy when it has none, found [ageMillis]
   * ago.
   */
  private fun DevicePosition.toLocation(ageMillis: Long = 0) =
      Location(LocationManager.GPS_PROVIDER).also { location ->
        location.latitude = lat
        location.longitude = lng
        accuracyMeters?.let { location.accuracy = it }
        location.elapsedRealtimeNanos =
            SystemClock.elapsedRealtimeNanos() - TimeUnit.MILLISECONDS.toNanos(ageMillis)
      }

  /** Collects the updates of one collection until [during] is done. */
  private fun TestScope.updatesWhile(during: () -> Unit): List<LocationUpdate> {
    val received = mutableListOf<LocationUpdate>()
    val job = backgroundScope.launch { repository.observeLocation().collect { received.add(it) } }
    during()
    job.cancel()
    return received
  }

  private fun locationSettingChanged(enabled: Boolean) {
    shadowOf(locationManager).setLocationEnabled(enabled)
    app.sendBroadcast(Intent(LocationManager.PROVIDERS_CHANGED_ACTION).setPackage(app.packageName))
    shadowOf(Looper.getMainLooper()).idle()
  }

  private fun settingsReceivers() =
      shadowOf(app).registeredReceivers.filter {
        it.intentFilter.hasAction(LocationManager.PROVIDERS_CHANGED_ACTION)
      }

  @Test
  fun `precise or approximate location both count as the permission`() {
    assertTrue(repository.hasPermission())

    shadowOf(app).denyPermissions(Manifest.permission.ACCESS_FINE_LOCATION)
    assertFalse(repository.hasPermission())

    shadowOf(app).grantPermissions(Manifest.permission.ACCESS_COARSE_LOCATION)
    assertTrue(repository.hasPermission())
  }

  @Test
  fun `without the permission or a location service the flow stops, asking nothing`() = runTest {
    shadowOf(app).denyPermissions(Manifest.permission.ACCESS_FINE_LOCATION)
    var withoutPermission: Throwable? = null
    repository.observeLocation().catch { withoutPermission = it }.collect()

    shadowOf(app).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION)
    val noLocationService = FusedLocationUpdatesRepository(app, client, locationManager = null)
    var withoutService: Throwable? = null
    noLocationService.observeLocation().catch { withoutService = it }.collect()

    assertTrue(withoutPermission is SecurityException)
    assertTrue(withoutService is IllegalStateException)
    verify(exactly = 0) {
      client.requestLocationUpdates(any(), any<LocationCallback>(), any<Looper>())
    }
    assertTrue(settingsReceivers().isEmpty())
  }

  @Test
  fun `searches at high accuracy, then gives each position, with its accuracy if known`() =
      runTest(UnconfinedTestDispatcher()) {
        val received = mutableListOf<LocationUpdate>()
        val job = launch { repository.observeLocation().collect { received.add(it) } }

        val first = LAUSANNE_FLON.copy(accuracyMeters = GPS_ACCURACY_METERS)
        val withoutAccuracy = GENEVA_CORNAVIN
        callback.captured.onLocationResult(LocationResult.create(listOf(first.toLocation())))
        callback.captured.onLocationResult(
            LocationResult.create(listOf(withoutAccuracy.toLocation()))
        )
        job.cancel()

        assertEquals(Priority.PRIORITY_HIGH_ACCURACY, request.captured.priority)
        assertEquals(
            listOf(
                LocationUpdate.Searching,
                LocationUpdate.Position(first),
                LocationUpdate.Position(withoutAccuracy),
            ),
            received,
        )
      }

  @Test
  fun `follows the location setting going off and on again`() =
      runTest(UnconfinedTestDispatcher()) {
        shadowOf(locationManager).setLocationEnabled(false)
        val received = mutableListOf<LocationUpdate>()
        val job = launch { repository.observeLocation().collect { received.add(it) } }

        locationSettingChanged(enabled = true)
        locationSettingChanged(enabled = false)
        job.cancel()

        assertEquals(
            listOf(
                LocationUpdate.LocationOff,
                LocationUpdate.Searching,
                LocationUpdate.LocationOff,
            ),
            received,
        )
      }

  @Test
  fun `stopping the collection stops the GPS and the settings receiver`() =
      runTest(UnconfinedTestDispatcher()) {
        val job = launch { repository.observeLocation().collect() }
        settingsReceivers().single() // exactly one, or this throws

        job.cancel()

        verify(exactly = 1) { client.removeLocationUpdates(callback.captured) }
        assertTrue(settingsReceivers().isEmpty())
      }

  @Test
  fun `a phone that can't give its position stops the flow with the error, cleaning up`() =
      runTest {
        val noPlayServices = IllegalStateException("Google Play services missing")
        every { client.requestLocationUpdates(any(), capture(callback), any<Looper>()) } returns
            Tasks.forException(noPlayServices)
        var caught: Throwable? = null

        repository.observeLocation().catch { caught = it }.collect()

        // Task.await() rethrows a copy of the error (coroutines' stack trace recovery)
        assertTrue(caught is IllegalStateException)
        assertEquals(noPlayServices.message, caught?.message)
        verify(exactly = 1) { client.removeLocationUpdates(callback.captured) }
        assertTrue(settingsReceivers().isEmpty())
      }

  @Test
  fun `while searching, a recent last known position is given first, marked, but not an old one`() =
      runTest(UnconfinedTestDispatcher()) {
        val recent = LAUSANNE_FLON.copy(accuracyMeters = GPS_ACCURACY_METERS)
        every { client.lastLocation } returns
            Tasks.forResult(recent.toLocation(ageMillis = RECENT_MILLIS))
        val fresh = GENEVA_CORNAVIN.copy(accuracyMeters = GPS_ACCURACY_METERS)

        val withRecent = updatesWhile {
          callback.captured.onLocationResult(LocationResult.create(listOf(fresh.toLocation())))
        }

        assertEquals(
            listOf(
                LocationUpdate.Searching,
                LocationUpdate.Position(recent.copy(isLastKnown = true)),
                LocationUpdate.Position(fresh),
            ),
            withRecent,
        )

        every { client.lastLocation } returns
            Tasks.forResult(recent.toLocation(ageMillis = TOO_OLD_MILLIS))
        assertEquals(listOf(LocationUpdate.Searching), updatesWhile {})
      }

  @Test
  fun `no last known position when the location is off, after a new one, or if it can't be read`() =
      runTest(UnconfinedTestDispatcher()) {
        val recent = LAUSANNE_FLON.copy(accuracyMeters = GPS_ACCURACY_METERS)
        every { client.lastLocation } returns
            Tasks.forResult(recent.toLocation(ageMillis = RECENT_MILLIS))
        shadowOf(locationManager).setLocationEnabled(false)
        assertEquals(listOf(LocationUpdate.LocationOff), updatesWhile {})

        // A new position arrives while the last known one is still being read
        shadowOf(locationManager).setLocationEnabled(true)
        val lastKnownRead = TaskCompletionSource<Location?>()
        every { client.lastLocation } returns lastKnownRead.task
        val fresh = GENEVA_CORNAVIN.copy(accuracyMeters = GPS_ACCURACY_METERS)
        val afterNew = updatesWhile {
          callback.captured.onLocationResult(LocationResult.create(listOf(fresh.toLocation())))
          lastKnownRead.setResult(recent.toLocation(ageMillis = RECENT_MILLIS))
        }
        assertEquals(listOf(LocationUpdate.Searching, LocationUpdate.Position(fresh)), afterNew)

        every { client.lastLocation } returns
            Tasks.forException(IllegalStateException("Google Play services busy"))
        assertEquals(listOf(LocationUpdate.Searching), updatesWhile {})
      }

  private companion object {
    // Ages of a last known position, either side of the repository's 10-minute limit
    val RECENT_MILLIS = TimeUnit.MINUTES.toMillis(1)
    val TOO_OLD_MILLIS = TimeUnit.HOURS.toMillis(1)
  }
}
