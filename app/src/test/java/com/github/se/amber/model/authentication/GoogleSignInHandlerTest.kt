// This file was written with the aid of AI
package com.github.se.amber.model.authentication

import android.app.Application
import android.os.Bundle
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, manifest = Config.NONE)
class GoogleSignInHandlerTest {
  private val handler = GoogleSignInHandler()

  @Test
  fun emptyBundleThrowsParsingException() {
    assertThrows(GoogleIdTokenParsingException::class.java) { handler.getAuthCredential(Bundle()) }
  }

  @Test
  fun unrelatedBundleThrowsParsingException() {
    val data = Bundle().apply { putString("unrelated", "value") }

    assertThrows(GoogleIdTokenParsingException::class.java) { handler.getAuthCredential(data) }
  }
}
