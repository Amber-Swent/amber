// Written by Viktor Jurczenko, with assistance from
// Claude (Anthropic) via Claude Code.
package com.github.se.amber.model.media

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Checks [isMediaOf], the rule telling which care circle a media's file belongs to. */
class MediaItemTest {

  @Test
  fun aMediaStoredUnderTheCircleIsAMediaOfIt() {
    val picture = MediaItem.Picture(storagePath = "careCircles/c1/media/m1.jpg")

    assertTrue(picture.isMediaOf("c1"))
  }

  @Test
  fun aMediaOfACircleWhoseIdStartsTheSameIsNot() {
    val picture = MediaItem.Picture(storagePath = "careCircles/c10/media/m1.jpg")

    assertFalse(picture.isMediaOf("c1"))
  }

  @Test
  fun aTextHasNoFileSoIsAMediaOfNoCircle() {
    assertFalse(MediaItem.Text(text = "hello").isMediaOf("c1"))
  }
}
