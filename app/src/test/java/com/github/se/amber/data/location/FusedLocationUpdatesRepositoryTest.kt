// Written by Neha Chakraborty, with assistance from Claude (Anthropic) via Claude Code.

package com.github.se.amber.data.location

import android.Manifest
import android.app.Application
import android.content.Intent
import android.location.Location
import android.location.LocationManager
import android.os.Looper
import com.github.se.amber.model.location.DevicePosition
import com.github.se.amber.model.location.TestPositions.GENEVA_CORNAVIN
import com.github.se.amber.model.location.TestPositions.GPS_ACCURACY_METERS
import com.github.se.amber.model.location.TestPositions.LAUSANNE_FLON
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.Tasks
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
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
  }

  /** The Android location of this position, with no accuracy when it has none. */
  private fun DevicePosition.toLocation() =
      Location(LocationManager.GPS_PROVIDER).also { location ->
        location.latitude = lat
        location.longitude = lng
        accuracyMeters?.let { location.accuracy = it }
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
  fun `without the permission the flow stops with a SecurityException, asking nothing`() = runTest {
    shadowOf(app).denyPermissions(Manifest.permission.ACCESS_FINE_LOCATION)
    var caught: Throwable? = null

    repository.observeLocation().catch { caught = it }.collect()

    assertTrue(caught is SecurityException)
    verify(exactly = 0) {
      client.requestLocationUpdates(any(), any<LocationCallback>(), any<Looper>())
    }
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
        assertEquals(1, settingsReceivers().size)

        job.cancel()

        verify(exactly = 1) { client.removeLocationUpdates(callback.captured) }
        assertTrue(settingsReceivers().isEmpty())
      }

  @Test
  fun `a phone without a location service stops the flow, asking nothing`() = runTest {
    val noLocationService = FusedLocationUpdatesRepository(app, client, locationManager = null)
    var caught: Throwable? = null

    noLocationService.observeLocation().catch { caught = it }.collect()

    assertTrue(caught is IllegalStateException)
    verify(exactly = 0) {
      client.requestLocationUpdates(any(), any<LocationCallback>(), any<Looper>())
    }
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
}
