// Written by Neha Chakraborty, with assistance from
// Claude (Anthropic) via Claude Code.
package com.github.se.amber.data.account

import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ResolveInfo
import androidx.test.core.app.ApplicationProvider
import com.github.se.amber.MainActivity
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class AccountDeletionRepositoryProviderTest {
  private val fake: AccountDeletionRepository = mockk(relaxUnitFun = true)
  private val rightHere = CoroutineScope(Dispatchers.Unconfined)

  private fun marker(set: Boolean) =
      object : DeletionMarker {
        override val isSet = set

        override fun set() = Unit

        override fun remove() = Unit
      }

  @Test
  fun repositoryIsBuiltOnceByFactoryAndAgainAfterAFailure() {
    var attempts = 0
    val provider = AccountDeletionRepositoryProvider {
      if (attempts++ == 0) throw IllegalStateException("Firebase not initialized")
      fake
    }

    runCatching { provider.repository }

    assertSame(fake, provider.repository)
    assertSame(fake, provider.repository)
    assertEquals(2, attempts)
  }

  @Test
  fun aLaunchWithoutAMarkerBuildsNothing() {
    var attempts = 0
    val provider = AccountDeletionRepositoryProvider {
      attempts++
      fake
    }

    provider.resumeInterruptedDeletion(marker(set = false), rightHere)

    assertEquals(0, attempts)
  }

  @Test
  fun aMarkerResumesTheDeletionAndNoFailureStopsTheAppFromOpening() {
    AccountDeletionRepositoryProvider { fake }
        .resumeInterruptedDeletion(marker(set = true), rightHere)
    coVerify(exactly = 1) { fake.finishInterruptedDeletion() }

    coEvery { fake.finishInterruptedDeletion() } throws IllegalStateException("Boom")
    AccountDeletionRepositoryProvider { fake }
        .resumeInterruptedDeletion(marker(set = true), rightHere)
    AccountDeletionRepositoryProvider { throw IllegalStateException("Firebase not initialized") }
        .resumeInterruptedDeletion(marker(set = true), rightHere)
  }

  @Test
  fun restartingOpensTheAppInANewTaskAndEndsTheProcess() {
    val context: Application = ApplicationProvider.getApplicationContext()
    val launcher =
        Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)
            .setPackage(context.packageName)
    val mainActivity =
        ActivityInfo().apply {
          packageName = context.packageName
          name = MainActivity::class.java.name
        }
    shadowOf(context.packageManager)
        .addResolveInfoForIntent(launcher, ResolveInfo().apply { activityInfo = mainActivity })
    var stops = 0

    AccountDeletionRepositoryProvider.restartApp(context) { stops++ }

    assertEquals(1, stops)
    val started = shadowOf(context).nextStartedActivity
    assertEquals(MainActivity::class.java.name, started.component?.className)
    assertTrue(started.flags and Intent.FLAG_ACTIVITY_CLEAR_TASK != 0)
  }

  @Test
  fun theProcessEndsEvenIfTheAppCannotBeReopened() {
    val brokenContext =
        mockk<Context> {
          every { packageManager } throws IllegalStateException("No package manager")
        }
    var stops = 0

    AccountDeletionRepositoryProvider.restartApp(brokenContext) { stops++ }

    assertEquals(1, stops)
  }
}
