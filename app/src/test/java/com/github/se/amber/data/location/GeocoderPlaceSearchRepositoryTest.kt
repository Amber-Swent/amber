// This file was written by Neha Chakraborty, with assistance from Claude (Anthropic) via Claude
// Code.
package com.github.se.amber.data.location

import android.location.Address
import com.github.se.amber.model.location.GeoLocation
import java.io.IOException
import java.util.Locale
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Checks [GeocoderPlaceSearchRepository] with a fake lookup in place of the real [Geocoder], so no
 * network is used. Robolectric gives it Android's [Address].
 */
@RunWith(RobolectricTestRunner::class)
class GeocoderPlaceSearchRepositoryTest {

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
  fun anAddressWithNoNameAtAllKeepsItsCoordinates() {
    val place = address(line = null).toGeoLocation()

    assertEquals(46.52, place?.lat)
    assertNull(place?.placeName)
  }
}
