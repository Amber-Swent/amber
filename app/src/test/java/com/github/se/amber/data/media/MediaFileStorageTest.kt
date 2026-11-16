// Written by Alaabrine, with assistance from Cursor Agent.

package com.github.se.amber.data.media

import com.github.se.amber.model.circle.CareCircle
import com.github.se.amber.model.media.MediaItem
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** A file whose [File.length] is [bytes], without writing them. */
internal fun fileOfLength(directory: File, bytes: Long): File =
    object : File(directory, "sized-$bytes") {
      override fun length() = bytes
    }

/** Checks [requireUploadable] and [requirePhotoUploadable]. Neither touches Firebase. */
class MediaFileStorageTest {

  @get:Rule val tmp = TemporaryFolder()

  private val circle = CareCircle(id = "c1", storageBucket = "bucket-c1")
  private val picture =
      MediaItem.Picture(id = "m1", authorId = "u1", storagePath = "careCircles/c1/media/m1.jpg")

  @Test
  fun requireUploadableReturnsTheContentTypeOfAnAcceptedFile() {
    val file = fileOfLength(tmp.root, 1)

    assertEquals("image/jpeg", requireUploadable(circle, picture, file))
    assertEquals(
        "image/png",
        requireUploadable(
            circle,
            picture.copy(storagePath = "careCircles/c1/media/m1.png"),
            file,
        ),
    )
    assertEquals(
        "audio/mp4",
        requireUploadable(
            circle,
            MediaItem.Audio(
                id = "m2",
                authorId = "u1",
                storagePath = "careCircles/c1/media/m2.m4a",
            ),
            file,
        ),
    )
    assertEquals(
        "image/jpeg",
        requireUploadable(circle, picture, fileOfLength(tmp.root, MAX_FILE_BYTES)),
    )
    // a person photo is capped at 2 MiB; a media file just over that is still accepted
    assertEquals(
        "image/jpeg",
        requireUploadable(circle, picture, fileOfLength(tmp.root, MAX_PHOTO_BYTES + 1)),
    )
  }

  @Test
  fun requireUploadableRefusesAMediaTheRulesWouldRefuse() {
    val file = fileOfLength(tmp.root, 1)
    val refused =
        mapOf(
            "text" to
                ("Media t1 is a text: it has no file to upload" to
                    {
                      requireUploadable(circle, MediaItem.Text(id = "t1"), file)
                    }),
            "wrong extension" to
                ("Media m1 (careCircles/c1/media/m1.gif) has no .jpg, .png or .m4a extension" to
                    {
                      requireUploadable(
                          circle,
                          picture.copy(storagePath = "careCircles/c1/media/m1.gif"),
                          file,
                      )
                    }),
            "another circle" to
                ("Media m1 (careCircles/c2/media/m1.jpg) isn't a media of care circle c1" to
                    {
                      requireUploadable(
                          circle,
                          picture.copy(storagePath = "careCircles/c2/media/m1.jpg"),
                          file,
                      )
                    }),
            "empty file" to
                ("Media m1's file is empty or over 25 MiB" to
                    {
                      requireUploadable(circle, picture, fileOfLength(tmp.root, 0))
                    }),
            "file over 25 MiB" to
                ("Media m1's file is empty or over 25 MiB" to
                    {
                      requireUploadable(
                          circle,
                          picture,
                          fileOfLength(tmp.root, MAX_FILE_BYTES + 1),
                      )
                    }),
        )

    for ((case, expected) in refused) {
      val (message, call) = expected
      assertEquals(
          case,
          message,
          assertThrows(case, IllegalArgumentException::class.java) { call() }.message,
      )
    }
  }

  @Test
  fun requirePhotoUploadableAcceptsAJpegOfOneByteAndOf2MiB() {
    assertEquals(
        "careCircles/c1/people/tom/p1.jpg",
        requirePhotoUploadable(circle, "tom", "p1", fileOfLength(tmp.root, 1)),
    )
    assertEquals(
        "careCircles/c1/people/tom/p1.jpg",
        requirePhotoUploadable(circle, "tom", "p1", fileOfLength(tmp.root, MAX_PHOTO_BYTES)),
    )
  }

  @Test
  fun requirePhotoUploadableRefusesAnIdOrFileTheRulesWouldRefuse() {
    val photo = fileOfLength(tmp.root, 1)
    val refused =
        mapOf<String, () -> Unit>(
            "underscore in the circle id" to
                {
                  requirePhotoUploadable(circle.copy(id = "c_1"), "tom", "p1", photo)
                },
            "blank circle id" to
                {
                  requirePhotoUploadable(circle.copy(id = " "), "tom", "p1", photo)
                },
            "slash in the circle id" to
                {
                  requirePhotoUploadable(circle.copy(id = "c/1"), "tom", "p1", photo)
                },
            "blank person id" to { requirePhotoUploadable(circle, " ", "p1", photo) },
            "slash in the person id" to { requirePhotoUploadable(circle, "to/m", "p1", photo) },
            "underscore in the person id" to
                {
                  requirePhotoUploadable(circle, "to_m", "p1", photo)
                },
            ".jpg only" to { requirePhotoUploadable(circle, "tom", "", photo) },
            "slash in the photo id" to { requirePhotoUploadable(circle, "tom", "p/1", photo) },
            "underscore in the photo id" to { requirePhotoUploadable(circle, "tom", "p_1", photo) },
            "empty file" to
                {
                  requirePhotoUploadable(circle, "tom", "p1", fileOfLength(tmp.root, 0))
                },
            "file over 2 MiB" to
                {
                  requirePhotoUploadable(
                      circle,
                      "tom",
                      "p1",
                      fileOfLength(tmp.root, MAX_PHOTO_BYTES + 1),
                  )
                },
        )

    for ((case, call) in refused) {
      assertThrows(case, IllegalArgumentException::class.java) { call() }
    }
  }
}
