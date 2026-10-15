// Written by Viktor Jurczenko, with assistance from
// Claude (Anthropic) via Claude Code.
package com.github.se.amber.data.media

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.Data
import androidx.work.ListenableWorker.Result
import androidx.work.Worker
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.workDataOf
import com.github.se.amber.data.media.FakeMediaSender.Call
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Checks how [MediaSendWorker] turns one send attempt into WorkManager's answer. */
@RunWith(RobolectricTestRunner::class)
class MediaSendWorkerTest {

  private val context: Context = ApplicationProvider.getApplicationContext()

  /** Runs one attempt of the worker, after [runAttemptCount] earlier ones, with [sender]. */
  private suspend fun runWorker(
      sender: FakeMediaSender,
      input: Data = MediaSendWorker.inputData("c1", "m1"),
      runAttemptCount: Int = 0,
  ): Result =
      TestListenableWorkerBuilder.from(context, MediaSendWorker::class.java)
          .setWorkerFactory(MediaSendWorkerFactory { sender })
          .setInputData(input)
          .setRunAttemptCount(runAttemptCount)
          .build()
          .doWork()

  @Test
  fun doneSucceeds() = runTest {
    assertEquals(Result.success(), runWorker(FakeMediaSender(mutableListOf(SendResult.DONE))))
  }

  @Test
  fun failedFails() = runTest {
    assertEquals(Result.failure(), runWorker(FakeMediaSender(mutableListOf(SendResult.FAILED))))
  }

  @Test
  fun retryAsksToRunAgain() = runTest {
    assertEquals(Result.retry(), runWorker(FakeMediaSender(mutableListOf(SendResult.RETRY))))
  }

  @Test
  fun passesItsIdsToTheSender() = runTest {
    val sender = FakeMediaSender()

    runWorker(sender, MediaSendWorker.inputData("circle-7", "media-42"))

    assertEquals(listOf(Call("circle-7", "media-42", lastAttempt = false)), sender.calls)
  }

  @Test
  fun missingIdFailsWithoutSending() = runTest {
    val sender = FakeMediaSender()

    val withoutMedia = runWorker(sender, workDataOf("circleId" to "c1"))
    val withoutCircle = runWorker(sender, workDataOf("mediaId" to "m1"))

    assertEquals(Result.failure(), withoutMedia)
    assertEquals(Result.failure(), withoutCircle)
    assertTrue(sender.calls.isEmpty())
  }

  @Test
  fun onlyTheLastAllowedAttemptIsTheLastAttempt() = runTest {
    val sender = FakeMediaSender()

    // runAttemptCount counts the earlier attempts: MAX_ATTEMPTS - 1 means this is the last one
    runWorker(sender, runAttemptCount = MediaSendWorker.MAX_ATTEMPTS - 2)
    runWorker(sender, runAttemptCount = MediaSendWorker.MAX_ATTEMPTS - 1)

    assertEquals(listOf(false, true), sender.calls.map { it.lastAttempt })
  }

  @Test
  fun retryOnTheLastAttemptFails() = runTest {
    // a sender breaking its contract (RETRY although lastAttempt) must not keep the job alive
    val sender = FakeMediaSender(mutableListOf(SendResult.RETRY))

    val result = runWorker(sender, runAttemptCount = MediaSendWorker.MAX_ATTEMPTS - 1)

    assertEquals(Result.failure(), result)
  }

  @Test
  fun factoryLeavesOtherWorkersToWorkManager() {
    // WorkManager asks the app's factory for every worker; null makes it create this one itself
    val factory = MediaSendWorkerFactory { FakeMediaSender() }

    val worker =
        factory.createWorker(context, OtherWorker::class.java.name, mockk<WorkerParameters>())

    assertNull(worker)
  }

  @Test
  fun factoryGetsTheSenderOnlyWhenCreatingAWorker() = runTest {
    var senderRequests = 0
    val factory = MediaSendWorkerFactory {
      senderRequests++
      FakeMediaSender()
    }
    assertEquals(0, senderRequests)

    TestListenableWorkerBuilder.from(context, MediaSendWorker::class.java)
        .setWorkerFactory(factory)
        .setInputData(MediaSendWorker.inputData("c1", "m1"))
        .build()

    assertEquals(1, senderRequests)
  }
}

/** A worker of another feature, which [MediaSendWorkerFactory] must not create. */
private class OtherWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
  override fun doWork(): Result = Result.success()
}
