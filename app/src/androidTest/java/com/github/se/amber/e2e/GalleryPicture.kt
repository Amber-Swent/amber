// Written by Viktor Jurczenko, with assistance from Claude (Anthropic) via Claude Code.

package com.github.se.amber.e2e

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.SystemClock
import android.provider.MediaStore
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import java.io.ByteArrayOutputStream
import java.util.regex.Pattern

/**
 * A picture in the phone's gallery, for a test to pick through the system Photo Picker, the screen
 * the app opens to choose a picture (`PickVisualMedia`).
 *
 * The gallery is Android's MediaStore, the phone's shared index of pictures and videos, which the
 * Photo Picker lists newest first. [insert] adds a generated JPEG to it, dated now, so it is the
 * first picture in the picker. [pickInPhotoPicker] taps it with UI Automator, which drives screens
 * outside the app, such as this system one. [delete] removes the picture again.
 *
 * Two versions of the Photo Picker exist, depending on the Android version and its Google Play
 * system updates: the MediaProvider one closes as soon as a picture is tapped, while the newer one
 * only selects it and waits for its "Done" button. [pickInPhotoPicker] handles both.
 *
 * Needs Android 13 (API 33) or later, where the Photo Picker is always part of the system. Android
 * 11 and 12 only get it through a Google Play system update, so there `PickVisualMedia` may open
 * the documents app instead.
 *
 * @param context the context whose content resolver writes to MediaStore
 * @param name the file name of the picture, unique to the test run
 */
class GalleryPicture(private val context: Context, private val name: String) {
  private var uri: Uri? = null

  /**
   * The JPEG bytes [insert] added to the gallery, for a test to check that what the app stored is
   * this picture and not another one; empty before [insert].
   */
  var bytes: ByteArray = ByteArray(0)
    private set

  /**
   * Generates a small one-colour JPEG and adds it to the gallery, dated now, so the Photo Picker
   * lists it first. Its bytes are kept in [bytes].
   *
   * @throws IllegalStateException if MediaStore refuses the picture
   */
  fun insert() {
    bytes =
        ByteArrayOutputStream().use { output ->
          val bitmap = Bitmap.createBitmap(SIZE_PX, SIZE_PX, Bitmap.Config.ARGB_8888)
          bitmap.eraseColor(Color.rgb(255, 160, 0))
          bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, output)
          bitmap.recycle()
          output.toByteArray()
        }
    val now = System.currentTimeMillis()
    val values =
        ContentValues().apply {
          put(MediaStore.Images.Media.DISPLAY_NAME, "$name.jpg")
          put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
          put(MediaStore.Images.Media.DATE_TAKEN, now)
          put(MediaStore.Images.Media.DATE_ADDED, now / 1_000)
          // hidden from the picker until its bytes are written
          put(MediaStore.Images.Media.IS_PENDING, 1)
        }
    val resolver = context.contentResolver
    val inserted =
        checkNotNull(resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)) {
          "MediaStore refused the picture $name"
        }
    uri = inserted
    checkNotNull(resolver.openOutputStream(inserted)) { "Can't write the picture $name" }
        .use { it.write(bytes) }
    resolver.update(
        inserted,
        ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) },
        null,
        null,
    )
  }

  /**
   * Waits for the system Photo Picker, then taps its first picture, the newest one: the one
   * [insert] added. The MediaProvider picker closes on that tap and hands the picture to the app;
   * the newer one only selects it and waits for its "Done" button, which is then tapped too.
   *
   * @param timeoutMillis how long to wait for the picker and its pictures
   * @throws AssertionError if the picker or its pictures don't show in time; the picker's window
   *   hierarchy is then written to the log, to adapt the selectors to a new picker version
   */
  fun pickInPhotoPicker(timeoutMillis: Long) {
    val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    if (!device.wait(Until.hasObject(By.pkg(PICKER_PACKAGES)), timeoutMillis)) {
      fail(device, "The Photo Picker didn't open")
    }
    // The picker's layout can still move once its grid shows (e.g. a "cloud media app" banner is
    // added above it), and a tap during that lands beside the picture: try again until it closes.
    // Once the picture is selected, the next try taps "Done" instead: tapping the picture again
    // would deselect it.
    repeat(PICK_ATTEMPTS) {
      device.waitForIdle()
      val target =
          device.findObject(DONE_BUTTON)
              ?: findPicture(device, timeoutMillis)
              ?: fail(device, "The Photo Picker shows no picture")
      target.click()
      if (device.wait(Until.gone(By.pkg(PICKER_PACKAGES)), timeoutMillis / PICK_ATTEMPTS)) return
    }
    fail(device, "The Photo Picker didn't close after the picture was picked")
  }

  /**
   * Finds the first picture of the picker's grid, as the node to tap: the clickable cell holding
   * the thumbnail, since in some picker versions the thumbnail itself isn't clickable.
   *
   * The picker's versions name their thumbnails differently, so it checks all the known selectors
   * at each poll, and takes the first that matches. That way it doesn't wait for a selector the
   * picker on screen never matches.
   *
   * @param device the device showing the picker
   * @param timeoutMillis how long to wait for the grid's pictures
   * @return the node to tap, or null if no selector found a picture in time
   */
  private fun findPicture(device: UiDevice, timeoutMillis: Long): UiObject2? {
    val deadline = SystemClock.uptimeMillis() + timeoutMillis
    var thumbnail = PICTURE_SELECTORS.firstNotNullOfOrNull { device.findObject(it) }
    while (thumbnail == null && SystemClock.uptimeMillis() < deadline) {
      SystemClock.sleep(POLL_MILLIS)
      thumbnail = PICTURE_SELECTORS.firstNotNullOfOrNull { device.findObject(it) }
    }
    if (thumbnail == null) return null
    var node: UiObject2? = thumbnail
    while (node != null && !node.isClickable) node = node.parent
    return node ?: thumbnail
  }

  /** Removes the picture from the gallery; nothing happens if [insert] didn't add it. */
  fun delete() {
    uri?.let { context.contentResolver.delete(it, null, null) }
    uri = null
  }

  /**
   * Writes the current window hierarchy to the log, then fails.
   *
   * @param device the device whose screen to dump
   * @param message why the test fails
   * @return never: it always throws
   * @throws AssertionError always, with [message]
   */
  private fun fail(device: UiDevice, message: String): Nothing {
    val hierarchy = ByteArrayOutputStream().also { device.dumpWindowHierarchy(it) }
    Log.e(TAG, "$message. Window hierarchy:\n$hierarchy")
    throw AssertionError(message)
  }

  /** Holds the picture's size and the Photo Picker's known selectors. */
  private companion object {
    const val TAG = "GalleryPicture"
    const val SIZE_PX = 64
    const val JPEG_QUALITY = 90
    const val PICK_ATTEMPTS = 3
    const val POLL_MILLIS = 200L

    /** The packages of the Photo Picker: AOSP and Google builds, the MediaProvider one or not. */
    val PICKER_PACKAGES: Pattern =
        Pattern.compile("com\\.(google\\.)?android\\.(providers\\.media\\.module|photopicker)")

    /**
     * The thumbnails of the picker's grid, one selector per picker version: the MediaProvider one
     * names them `icon_thumbnail`; the newer one only describes them, e.g. "Photo taken on Nov 4,
     * 2026 9:32 PM". "Photo " with a space and more after it, so the "Photos" tab doesn't match.
     */
    val PICTURE_SELECTORS: List<BySelector> =
        listOf(
            By.pkg(PICKER_PACKAGES).res(Pattern.compile(".*:id/icon_thumbnail")),
            By.pkg(PICKER_PACKAGES).desc(Pattern.compile("Photo .+")),
        )

    /**
     * The newer picker's button that hands the selected picture to the app. Matched by its English
     * text, the language of the test emulators.
     */
    val DONE_BUTTON: BySelector = By.pkg(PICKER_PACKAGES).text("Done")
  }
}
