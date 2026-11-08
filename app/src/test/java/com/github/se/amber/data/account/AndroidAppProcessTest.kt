// Written by Neha Chakraborty, with assistance from Claude (Anthropic) via Claude Code.

package com.github.se.amber.data.account

import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ResolveInfo
import androidx.test.core.app.ApplicationProvider
import com.github.se.amber.MainActivity
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class AndroidAppProcessTest {
  private var exits = 0

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

    AndroidAppProcess(context) { exits++ }.restart()

    assertEquals(1, exits)
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

    AndroidAppProcess(brokenContext) { exits++ }.restart()

    assertEquals(1, exits)
  }

  @Test
  fun stoppingEndsTheProcessWithoutReopeningTheApp() {
    val context: Application = ApplicationProvider.getApplicationContext()

    AndroidAppProcess(context) { exits++ }.stop()

    assertEquals(1, exits)
    assertNull(shadowOf(context).nextStartedActivity)
  }
}
