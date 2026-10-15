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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Checks how [MediaSendWorker] turns one run, and its send attempt, into WorkManager's answer.
 * Counting failed attempts and giving up are the sender's job, so the worker never ends a job on
 * its own, except for a malformed input.
 */
@RunWith(RobolectricTestRunner::class)
class MediaSendWorkerTest {

  private val context: Context = ApplicationProvider.getApplicationContext()

  /** Runs one attempt of the worker, after [runAttemptCount] earlier ones, with [sender]. */
  private suspend fun runWorker(
      sender: () -> MediaSender,
      input: Data = MediaSendWorker.inputData("c1", "m1"),
      runAttemptCount: Int = 0,
  ): Result =
      TestListenableWorkerBuilder.from(context, MediaSendWorker::class.java)
          .setWorkerFactory(MediaSendWorkerFactory(sender))
          .setInputData(input)
          .setRunAttemptCount(runAttemptCount)
          .build()
          .doWork()

  private suspend fun runWorker(
      sender: MediaSender,
      input: Data = MediaSendWorker.inputData("c1", "m1"),
      runAttemptCount: Int = 0,
  ): Result = runWorker({ sender }, input, runAttemptCount)

  private val throwingSender = MediaSender { _, _ -> throw IllegalStateException("bug") }

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

    assertEquals(listOf(Call("circle-7", "media-42")), sender.calls)
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
  fun retryIsNeverTurnedIntoAFailure() = runTest {
    // only the sender decides when to give up: the worker has no limit on runs, however many
    // earlier runs WorkManager counted (stopped ones included)
    val sender = FakeMediaSender(mutableListOf(SendResult.RETRY))

    val result = runWorker(sender, runAttemptCount = 1_000)

    assertEquals(Result.retry(), result)
  }

  @Test
  fun unexpectedSendErrorIsRetried() = runTest {
    assertEquals(Result.retry(), runWorker(throwingSender))
  }

  @Test
  fun strayCancellationIsRetried() = runTest {
    // a CancellationException although the run wasn't stopped (e.g. a timeout leaking from send):
    // passed on, WorkManager would mark the job failed while the entry stays WAITING
    val sender = MediaSender { _, _ -> throw CancellationException("not a stop") }

    assertEquals(Result.retry(), runWorker(sender))
  }

  @Test
  fun stoppedRunIsPassedOn() = runTest {
    val sending = CompletableDeferred<Unit>()
    val sender = MediaSender { _, _ ->
      sending.complete(Unit)
      awaitCancellation()
    }
    val worker =
        TestListenableWorkerBuilder.from(context, MediaSendWorker::class.java)
            .setWorkerFactory(MediaSendWorkerFactory { sender })
            .setInputData(MediaSendWorker.inputData("c1", "m1"))
            .build()
    var result: Result? = null
    val run = launch { result = worker.doWork() }
    sending.await()

    // what WorkManager does to stop a run: cancel the coroutine running doWork
    run.cancelAndJoin()

    assertNull("a stopped run must not answer, so WorkManager reruns it", result)
    assertTrue(run.isCancelled)
  }

  @Test
  fun senderThatCantBeBuiltIsRetried() = runTest {
    val result = runWorker(sender = { throw IllegalStateException("Firebase not ready") })

    assertEquals(Result.retry(), result)
  }

  @Test
  fun factoryLeavesOtherWorkersToWorkManager() {
    // WorkManager asks the app's factory for every worker; null makes it create this one itself
    var senderRequests = 0
    val factory = MediaSendWorkerFactory {
      senderRequests++
      FakeMediaSender()
    }

    val worker =
        factory.createWorker(context, OtherWorker::class.java.name, mockk<WorkerParameters>())

    assertNull(worker)
    assertEquals(0, senderRequests)
  }

  @Test
  fun senderIsBuiltOnlyWhenTheWorkerRuns() = runTest {
    var senderRequests = 0
    val factory = MediaSendWorkerFactory {
      senderRequests++
      FakeMediaSender()
    }
    val worker =
        TestListenableWorkerBuilder.from(context, MediaSendWorker::class.java)
            .setWorkerFactory(factory)
            .setInputData(MediaSendWorker.inputData("c1", "m1"))
            .build()
    assertEquals(0, senderRequests)

    worker.doWork()

    assertEquals(1, senderRequests)
  }
}

/** A worker of another feature, which [MediaSendWorkerFactory] must not create. */
private class OtherWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
  override fun doWork(): Result = Result.success()
}
