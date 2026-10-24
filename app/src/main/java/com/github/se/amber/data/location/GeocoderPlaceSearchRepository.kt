// This file was written by Neha Chakraborty, with assistance from Claude (Anthropic) via Claude
// Code.
package com.github.se.amber.data.location

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.os.Build
import com.github.se.amber.model.location.GeoLocation
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

/**
 * [PlaceSearchRepository] backed by Android's [Geocoder]: no API key, but it needs a connection and
 * a device with a geocoding service (any phone with Google Play services).
 *
 * @param findAddresses looks up at most `maxResults` addresses for a name; replaced in tests.
 */
class GeocoderPlaceSearchRepository(
    private val findAddresses: suspend (name: String, maxResults: Int) -> List<Address>,
) : PlaceSearchRepository {

  constructor(context: Context) : this(geocoderLookup(Geocoder(context)))

  override suspend fun search(query: String): Result<List<GeoLocation>> {
    val name = query.trim()
    if (name.isEmpty()) return Result.success(emptyList())
    return try {
      Result.success(findAddresses(name, MAX_RESULTS).mapNotNull { it.toGeoLocation() })
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      Result.failure(e)
    }
  }

  companion object {
    const val MAX_RESULTS = 5

    /** Looks addresses up with [geocoder], with the non-blocking call where Android has it. */
    private fun geocoderLookup(geocoder: Geocoder): suspend (String, Int) -> List<Address> =
        { name, maxResults ->
          if (!Geocoder.isPresent()) throw IOException("No place search on this device")
          if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            suspendCancellableCoroutine { continuation ->
              geocoder.getFromLocationName(
                  name,
                  maxResults,
                  object : Geocoder.GeocodeListener {
                    override fun onGeocode(addresses: MutableList<Address>) =
                        continuation.resume(addresses)

                    override fun onError(errorMessage: String?) =
                        continuation.resumeWithException(IOException(errorMessage))
                  },
              )
            }
          } else {
            // blocks on the network: older Android has no other way
            withContext(Dispatchers.IO) {
              @Suppress("DEPRECATION") geocoder.getFromLocationName(name, maxResults).orEmpty()
            }
          }
        }
  }
}

/**
 * This address as a [GeoLocation], named by its first address line (e.g. "Lausanne, Switzerland"),
 * or by its place, town and country. Null if it has no coordinates: it isn't a point on the map.
 */
internal fun Address.toGeoLocation(): GeoLocation? {
  if (!hasLatitude() || !hasLongitude()) return null
  val name =
      getAddressLine(0)?.takeIf { it.isNotBlank() }
          ?: listOfNotNull(featureName, locality, countryName)
              .filter { it.isNotBlank() }
              .distinct()
              .joinToString(", ")
  return GeoLocation(lat = latitude, lng = longitude, placeName = name.ifBlank { null })
}
