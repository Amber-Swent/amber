// Written by GitHub Copilot.
package com.github.se.amber.data.storage

import com.github.se.amber.model.circle.CareCircle
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class CircleStorageProviderTest {
  @Before
  fun initializeFirebase() {
    val application = RuntimeEnvironment.getApplication()
    if (FirebaseApp.getApps(application).isEmpty()) {
      FirebaseApp.initializeApp(
          application,
          FirebaseOptions.Builder()
              .setApplicationId("1:123456789012:android:abcdef123456")
              .setApiKey("test-api-key")
              .setProjectId("amber-test")
              .build(),
      )
    }
  }

  @Test
  fun forCircleRejectsMissingStorageBucket() {
    val error =
        assertThrows(IllegalArgumentException::class.java) {
          CircleStorageProvider.forCircle(CareCircle(id = "circle-1"))
        }

    assertEquals("Storage is not ready for care circle circle-1", error.message)
  }

  @Test
  fun forCircleRejectsWhitespaceOnlyStorageBucket() {
    val error =
        assertThrows(IllegalArgumentException::class.java) {
          CircleStorageProvider.forCircle(CareCircle(id = "circle-1", storageBucket = "  "))
        }

    assertEquals("Storage is not ready for care circle circle-1", error.message)
  }

  @Test
  fun forCircleUsesCircleStorageBucket() {
    val storage =
        CircleStorageProvider.forCircle(
            CareCircle(id = "circle-1", storageBucket = "circle-bucket")
        )

    assertEquals("circle-bucket", storage.reference.bucket)
  }
}
