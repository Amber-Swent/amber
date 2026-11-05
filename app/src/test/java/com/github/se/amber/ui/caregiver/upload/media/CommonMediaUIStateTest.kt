/* Written by Lou-Anne Maier, with assistance from
 * Claude (Anthropic) via Claude Code.
 */
package com.github.se.amber.ui.caregiver.upload.media

import com.github.se.amber.model.location.GeoLocation
import com.github.se.amber.model.media.MemoryDate
import java.time.Month
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Unit tests for [CommonMediaUIState], the fields every upload screen shares. */
class CommonMediaUIStateTest {
  private val complete =
      CommonMediaUIState(
          description = "A description",
          date = MemoryDate(2024, Month.MAY, 12),
      )
  private val lausanne = GeoLocation(lat = 46.52, lng = 6.63, placeName = "Lausanne, Switzerland")

  // NEW TEST
  @Test
  fun canUploadIsTrueWhenTheCommonFieldsAreComplete() {
    assertTrue(complete.canUpload)
  }

  // NEW TEST
  @Test
  fun canUploadIsFalseWithBlankDescription() {
    assertFalse(complete.copy(description = "  ").canUpload)
  }

  // NEW TEST
  @Test
  fun canUploadIsFalseWithoutDate() {
    assertFalse(complete.copy(date = null).canUpload)
  }

  // NEW TEST
  @Test
  fun canUploadIsFalseWithATypedButUnpickedPlace() {
    assertFalse(complete.copy(location = MediaLocationUIState(query = "Grandma")).canUpload)
  }

  // NEW TEST
  @Test
  fun canUploadIsTrueWithAPickedPlace() {
    val location = MediaLocationUIState(query = "Lausanne", pickedPlace = lausanne)
    assertTrue(complete.copy(location = location).canUpload)
  }

  // NEW TEST
  @Test
  fun canUploadIsFalseWhileUploading() {
    assertFalse(complete.copy(isUploading = true).canUpload)
  }
}
