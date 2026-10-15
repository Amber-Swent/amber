/* Written by Lou-Anne Maier, with assistance from
 * Claude (Anthropic) via Claude Code.
 */
package com.github.se.amber.ui.caregiver.upload

import android.content.ContentResolver
import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import com.github.se.amber.ui.caregiver.upload.picture.PickedImage
import com.github.se.amber.ui.caregiver.upload.picture.SelectedPictureReaderAndroid
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import java.io.ByteArrayInputStream
import java.io.File
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Fake dispatcher that records how many blocks were dispatched through it. */
private class RecordingDispatcher : CoroutineDispatcher() {
  var dispatchCount = 0

  override fun dispatch(context: CoroutineContext, block: Runnable) {
    dispatchCount++
    block.run()
  }
}

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class SelectedPictureReaderAndroidTest {

  @get:Rule val tempFolder = TemporaryFolder()

  private val uriString = "content://media/external/images/media/42"
  private val pictureBytes = byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8)
  private val decodedWidth = 640
  private val decodedHeight = 480
  private lateinit var cacheDir: File
  private lateinit var contentResolver: ContentResolver
  private lateinit var appContext: Context
  private lateinit var context: Context

  @Before
  fun setUp() {
    cacheDir = tempFolder.newFolder("cache")

    contentResolver = mockk()
    every { contentResolver.openInputStream(any()) } answers { ByteArrayInputStream(pictureBytes) }

    appContext = mockk()
    every { appContext.cacheDir } returns cacheDir
    every { appContext.contentResolver } returns contentResolver

    context = mockk()
    every { context.applicationContext } returns appContext

    mockkStatic(BitmapFactory::class)
    every { BitmapFactory.decodeFile(any(), any()) } answers
        {
          val options = secondArg<BitmapFactory.Options>()
          options.outWidth = decodedWidth
          options.outHeight = decodedHeight
          null
        }
  }

  @After
  fun tearDown() {
    unmockkStatic(BitmapFactory::class)
  }

  private fun createReader(
      dispatcher: CoroutineDispatcher = UnconfinedTestDispatcher()
  ): SelectedPictureReaderAndroid = SelectedPictureReaderAndroid(context, dispatcher)

  @Test
  fun read_returnsFileThatExists() = runTest {
    val result = createReader().read(uriString)

    assertTrue(result.file.exists())
  }

  @Test
  fun read_copiesPictureBytesIntoFile() = runTest {
    val result = createReader().read(uriString)

    assertEquals(pictureBytes.toList(), result.file.readBytes().toList())
  }

  @Test
  fun read_createsFileInApplicationCacheDirectory() = runTest {
    val result = createReader().read(uriString)

    assertEquals(cacheDir, result.file.parentFile)
  }

  @Test
  fun read_createsFileWithPickedPrefix() = runTest {
    val result = createReader().read(uriString)

    assertTrue(result.file.name.startsWith("picked_"))
  }

  @Test
  fun read_createsFileWithJpgExtension() = runTest {
    val result = createReader().read(uriString)

    assertTrue(result.file.name.endsWith(".jpg"))
  }

  @Test
  fun read_returnsDecodedWidth() = runTest {
    val result = createReader().read(uriString)

    assertEquals(decodedWidth, result.width)
  }

  @Test
  fun read_returnsDecodedHeight() = runTest {
    val result = createReader().read(uriString)

    assertEquals(decodedHeight, result.height)
  }

  @Test
  fun read_opensInputStreamWithParsedUri() = runTest {
    createReader().read(uriString)

    verify(exactly = 1) { contentResolver.openInputStream(Uri.parse(uriString)) }
  }

  @Test
  fun read_decodesBoundsOfCopiedFile() = runTest {
    val result = createReader().read(uriString)

    verify(exactly = 1) { BitmapFactory.decodeFile(result.file.absolutePath, any()) }
  }

  @Test
  fun read_decodesWithJustDecodeBoundsEnabled() = runTest {
    var justDecodeBounds: Boolean? = null
    every { BitmapFactory.decodeFile(any(), any()) } answers
        {
          val options = secondArg<BitmapFactory.Options>()
          justDecodeBounds = options.inJustDecodeBounds
          null
        }

    createReader().read(uriString)

    assertEquals(true, justDecodeBounds)
  }

  @Test
  fun read_runsOnInjectedDispatcher() = runTest {
    val dispatcher = RecordingDispatcher()

    createReader(dispatcher).read(uriString)

    assertTrue(dispatcher.dispatchCount > 0)
  }

  @Test
  fun read_throwsIllegalArgumentExceptionWhenInputStreamIsNull() = runTest {
    every { contentResolver.openInputStream(any()) } returns null

    assertThrows(IllegalArgumentException::class.java) {
      runBlocking { createReader().read(uriString) }
    }
  }

  @Test
  fun read_throwsWithExpectedMessageWhenInputStreamIsNull() = runTest {
    every { contentResolver.openInputStream(any()) } returns null

    val exception =
        assertThrows(IllegalArgumentException::class.java) {
          runBlocking { createReader().read(uriString) }
        }

    assertEquals("Could not open selected picture", exception.message)
  }

  @Test
  fun read_doesNotDecodeBoundsWhenInputStreamIsNull() = runTest {
    every { contentResolver.openInputStream(any()) } returns null

    runCatching { createReader().read(uriString) }

    verify(exactly = 0) { BitmapFactory.decodeFile(any(), any()) }
  }

  @Test
  fun read_usesApplicationContextRatherThanGivenContext() = runTest {
    createReader().read(uriString)

    verify(atLeast = 1) { context.applicationContext }
  }

  @Test
  fun read_returnsDistinctFilesForSuccessiveCalls() = runTest {
    val reader = createReader()

    val first = reader.read(uriString)
    val second = reader.read(uriString)

    assertTrue(first.file != second.file)
  }

  @Test
  fun pickedImage_equalsInstanceWithSameValues() {
    val file = File("picture.jpg")

    assertEquals(PickedImage(file, 10, 20), PickedImage(file, 10, 20))
  }

  @Test
  fun pickedImage_copyOverridesWidth() {
    val original = PickedImage(File("picture.jpg"), 10, 20)

    assertEquals(99, original.copy(width = 99).width)
  }

  @Test
  fun pickedImage_copyKeepsHeightWhenWidthChanges() {
    val original = PickedImage(File("picture.jpg"), 10, 20)

    assertEquals(20, original.copy(width = 99).height)
  }
}
