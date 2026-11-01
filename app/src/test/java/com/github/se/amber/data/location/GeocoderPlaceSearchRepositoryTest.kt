// This file was written by Neha Chakraborty, with assistance from Claude (Anthropic) via Claude
// Code.
package com.github.se.amber.data.location

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import com.github.se.amber.model.location.GeoLocation
import io.mockk.every
import io.mockk.mockk
import java.io.IOException
import java.util.Locale
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowGeocoder
import org.robolectric.util.ReflectionHelpers

/**
 * Checks [GeocoderPlaceSearchRepository] with a fake lookup in place of the real [Geocoder], so no
 * network is used. Robolectric gives it Android's [Address], and a [Geocoder] that answers what the
 * test sets.
 */
@RunWith(RobolectricTestRunner::class)
class GeocoderPlaceSearchRepositoryTest {

  private val sdkInt = Build.VERSION.SDK_INT

  @After
  fun restoreDevice() {
    ReflectionHelpers.setStaticField(Build.VERSION::class.java, "SDK_INT", sdkInt)
    ShadowGeocoder.setIsPresent(true)
  }

  /** Runs [block] as on Android 12, which only has the blocking lookup. */
  private fun onAndroid12(block: suspend TestScope.() -> Unit) {
    ReflectionHelpers.setStaticField(Build.VERSION::class.java, "SDK_INT", Build.VERSION_CODES.S)
    runTest { block() }
  }

  private fun address(
      lat: Double? = 46.52,
      lng: Double? = 6.63,
      line: String? = "Lausanne, Switzerland",
  ) =
      Address(Locale.ENGLISH).apply {
        lat?.let { latitude = it }
        lng?.let { longitude = it }
        line?.let { setAddressLine(0, it) }
      }

  /** A repository whose lookup records what it was asked and answers [addresses]. */
  private class Recording(private val addresses: List<Address>) {
    val asked = mutableListOf<Pair<String, Int>>()
    val repository = GeocoderPlaceSearchRepository { name, max ->
      asked += name to max
      addresses
    }
  }

  @Test
  fun foundAddressesBecomeRealPlacesWithCoordinates() = runTest {
    val places = Recording(listOf(address())).repository.search("Lausanne").getOrThrow()

    assertEquals(listOf(GeoLocation(46.52, 6.63, "Lausanne, Switzerland")), places)
  }

  @Test
  fun theQueryIsTrimmedAndTheResultsLimited() = runTest {
    val recording = Recording(emptyList())

    recording.repository.search("  Lausanne ")

    assertEquals(listOf("Lausanne" to GeocoderPlaceSearchRepository.MAX_RESULTS), recording.asked)
  }

  @Test
  fun aBlankQueryFindsNothingWithoutSearching() = runTest {
    val recording = Recording(listOf(address()))

    assertTrue(recording.repository.search("   ").getOrThrow().isEmpty())
    assertTrue(recording.asked.isEmpty())
  }

  @Test
  fun anAddressWithoutCoordinatesIsLeftOut() = runTest {
    val addresses = listOf(address(lat = null), address(lng = null), address())

    val places = Recording(addresses).repository.search("Lausanne").getOrThrow()

    assertEquals(1, places.size)
  }

  @Test
  fun aFailedLookupIsAFailedResult() = runTest {
    val repository = GeocoderPlaceSearchRepository { _, _ -> throw IOException("offline") }

    val error = repository.search("Lausanne").exceptionOrNull()

    assertTrue(error is IOException)
  }

  @Test
  fun anAddressWithoutLineIsNamedByItsPlaceTownAndCountry() {
    val address =
        address(line = null).apply {
          featureName = "Ouchy"
          locality = "Lausanne"
          countryName = "Switzerland"
        }

    assertEquals("Ouchy, Lausanne, Switzerland", address.toGeoLocation()?.placeName)
  }

  @Test
  fun aCancelledLookupStaysCancelled() = runTest {
    val repository = GeocoderPlaceSearchRepository { _, _ -> throw CancellationException("left") }

    val error = runCatching { repository.search("Lausanne") }.exceptionOrNull()

    assertTrue(error is CancellationException)
  }

  @Test
  fun blankAddressPartsAreLeftOutOfTheName() {
    val address =
        address(line = "  ").apply {
          featureName = ""
          locality = "Lausanne"
          countryName = "Switzerland"
        }

    assertEquals("Lausanne, Switzerland", address.toGeoLocation()?.placeName)
  }

  @Test
  fun anAddressWithNoNameAtAllKeepsItsCoordinates() {
    val place = address(line = null).toGeoLocation()

    assertEquals(46.52, place?.lat)
    assertNull(place?.placeName)
  }

  // ---------- the real Geocoder ----------

  private val geocoder = Geocoder(ApplicationProvider.getApplicationContext())

  @Test
  fun theGeocoderAnswersTheNonBlockingLookup() = runTest {
    shadowOf(geocoder).setFromLocation(listOf(address()))
    val lookup = GeocoderPlaceSearchRepository.geocoderLookup(geocoder, StandardTestDispatcher())

    assertEquals(listOf(46.52), lookup("Lausanne", 5).map { it.latitude })
  }

  @Test
  fun aGeocoderErrorIsAnIOException() = runTest {
    shadowOf(geocoder).setErrorMessage("offline")
    val lookup = GeocoderPlaceSearchRepository.geocoderLookup(geocoder, StandardTestDispatcher())

    val error = runCatching { lookup("Lausanne", 5) }.exceptionOrNull()

    assertTrue(error is IOException)
    assertEquals("offline", error?.message)
  }

  @Test
  fun aDeviceWithoutPlaceSearchFails() = runTest {
    ShadowGeocoder.setIsPresent(false)
    val repository =
        GeocoderPlaceSearchRepository(ApplicationProvider.getApplicationContext<Context>())

    assertTrue(repository.search("Lausanne").exceptionOrNull() is IOException)
  }

  @Test
  fun olderAndroidUsesTheBlockingLookupOnTheGivenDispatcher() = onAndroid12 {
    val blocking = mockk<Geocoder>()
    @Suppress("DEPRECATION")
    every { blocking.getFromLocationName("Lausanne", 5) } returns mutableListOf(address())
    val dispatcher = StandardTestDispatcher(testScheduler)
    val lookup = GeocoderPlaceSearchRepository.geocoderLookup(blocking, dispatcher)

    assertEquals(listOf(46.52), lookup("Lausanne", 5).map { it.latitude })
  }

  @Test
  fun olderAndroidFindingNothingIsAnEmptyList() = onAndroid12 {
    val blocking = mockk<Geocoder>()
    @Suppress("DEPRECATION") every { blocking.getFromLocationName(any(), any()) } returns null
    val lookup =
        GeocoderPlaceSearchRepository.geocoderLookup(
            blocking,
            StandardTestDispatcher(testScheduler),
        )

    assertTrue(lookup("Grandma's house", 5).isEmpty())
  }
}
