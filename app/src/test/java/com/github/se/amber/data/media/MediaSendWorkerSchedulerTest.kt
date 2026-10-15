// Written by Viktor Jurczenko, with assistance from
// Claude (Anthropic) via Claude Code.
package com.github.se.amber.data.media

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.NetworkType
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import androidx.work.testing.WorkManagerTestInitHelper.ExecutorsMode
import com.github.se.amber.data.media.FakeMediaSender.Call
import kotlinx.coroutines.asCoroutineDispatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Checks the jobs [MediaSendWorkerScheduler] gives WorkManager, using WorkManager's test version: a
 * job never runs on its own; it runs at once, on the test thread, when the test marks its
 * constraints as met.
 */
@RunWith(RobolectricTestRunner::class)
class MediaSendWorkerSchedulerTest {

  private val context: Context = ApplicationProvider.getApplicationContext()
  private val sender = FakeMediaSender()
  private val scheduler = MediaSendWorkerScheduler(context)
  private val workManager: WorkManager
    get() = WorkManager.getInstance(context)

  @Before
  fun setUp() {
    val executor = SynchronousExecutor()
    val configuration =
        Configuration.Builder()
            .setExecutor(executor)
            .setTaskExecutor(executor)
            .setWorkerCoroutineContext(executor.asCoroutineDispatcher())
            .setClock { NOW }
            .setWorkerFactory(MediaSendWorkerFactory { sender })
            .build()
    WorkManagerTestInitHelper.initializeTestWorkManager(
        context,
        configuration,
        ExecutorsMode.LEGACY_OVERRIDE_WITH_SYNCHRONOUS_EXECUTORS,
    )
  }

  private fun jobsOf(circleId: String, mediaId: String): List<WorkInfo> =
      workManager.getWorkInfosForUniqueWork("media-send/$circleId/$mediaId").get()

  private fun jobOf(circleId: String, mediaId: String): WorkInfo =
      jobsOf(circleId, mediaId).single()

  /**
   * Runs this media's job once, as WorkManager would once online and past its wait. The test
   * version ignores the real network: it only checks that the test says the constraints are met.
   */
  private fun runJob(circleId: String, mediaId: String) {
    val driver = WorkManagerTestInitHelper.getTestDriver(context)!!
    val id = jobOf(circleId, mediaId).id
    driver.setInitialDelayMet(id)
    driver.setAllConstraintsMet(id)
  }

  @Test
  fun scheduleEnqueuesATaggedJobWaitingForTheNetwork() {
    scheduler.schedule("c1", "m1")

    val job = jobOf("c1", "m1")
    assertEquals(WorkInfo.State.ENQUEUED, job.state)
    assertEquals(NetworkType.CONNECTED, job.constraints.requiredNetworkType)
    assertTrue(job.tags.containsAll(setOf("media-send", "media-send/c1")))
    assertTrue("no send while offline", sender.calls.isEmpty())
  }

  @Test
  fun jobSendsItsMediaOnceOnline() {
    scheduler.schedule("c1", "m1")

    runJob("c1", "m1")

    assertEquals(listOf(Call("c1", "m1", lastAttempt = false)), sender.calls)
    assertEquals(WorkInfo.State.SUCCEEDED, jobOf("c1", "m1").state)
  }

  @Test
  fun retriesWaitThirtySecondsThenTwiceAsLongEachTime() {
    sender.results += listOf(SendResult.RETRY, SendResult.RETRY, SendResult.RETRY)
    scheduler.schedule("c1", "m1")

    // the clock is fixed, so each wait shows as the next run time minus NOW
    val waits =
        List(3) {
          runJob("c1", "m1")
          jobOf("c1", "m1").nextScheduleTimeMillis - NOW
        }

    // a linear backoff would give 30, 60, 90 s
    assertEquals(listOf(30_000L, 60_000L, 120_000L), waits)
    assertEquals(3, jobOf("c1", "m1").runAttemptCount)
    assertEquals(WorkInfo.State.ENQUEUED, jobOf("c1", "m1").state)
  }

  @Test
  fun schedulingTwiceKeepsOneJob() {
    scheduler.schedule("c1", "m1")
    val first = jobOf("c1", "m1").id

    scheduler.schedule("c1", "m1")

    assertEquals(first, jobOf("c1", "m1").id)
  }

  @Test
  fun schedulingAfterAFailureStartsANewJob() {
    sender.results += SendResult.FAILED
    scheduler.schedule("c1", "m1")
    runJob("c1", "m1")
    val failed = jobOf("c1", "m1")
    assertEquals(WorkInfo.State.FAILED, failed.state)

    scheduler.schedule("c1", "m1")

    val retried = jobOf("c1", "m1")
    assertNotEquals(failed.id, retried.id)
    assertEquals(WorkInfo.State.ENQUEUED, retried.state)
    assertEquals(0, retried.runAttemptCount)
  }

  @Test
  fun rescheduleReplacesAJobNotRunYet() {
    scheduler.schedule("c1", "m1")
    val first = jobOf("c1", "m1").id

    scheduler.reschedule("c1", "m1")

    // REPLACE cancels the old job and deletes it, so only the new one is left
    assertNull(workManager.getWorkInfoById(first).get())
    val replacement = jobOf("c1", "m1")
    assertNotEquals(first, replacement.id)
    assertEquals(WorkInfo.State.ENQUEUED, replacement.state)
    assertTrue(replacement.tags.containsAll(setOf("media-send", "media-send/c1")))
  }

  @Test
  fun cancelStopsOnlyThatMedia() {
    scheduler.schedule("c1", "m1")
    scheduler.schedule("c1", "m2")

    scheduler.cancel("c1", "m1")

    assertEquals(WorkInfo.State.CANCELLED, jobOf("c1", "m1").state)
    assertEquals(WorkInfo.State.ENQUEUED, jobOf("c1", "m2").state)
  }

  @Test
  fun cancelCircleStopsOnlyThatCircle() {
    scheduler.schedule("c1", "m1")
    scheduler.schedule("c1", "m2")
    // c10 starts with "c1": its jobs must not be taken for c1's
    scheduler.schedule("c10", "m1")

    scheduler.cancelCircle("c1")

    assertEquals(WorkInfo.State.CANCELLED, jobOf("c1", "m1").state)
    assertEquals(WorkInfo.State.CANCELLED, jobOf("c1", "m2").state)
    assertEquals(WorkInfo.State.ENQUEUED, jobOf("c10", "m1").state)
  }

  @Test
  fun cancelAllStopsEverySend() {
    scheduler.schedule("c1", "m1")
    scheduler.schedule("c2", "m1")

    scheduler.cancelAll()

    assertEquals(WorkInfo.State.CANCELLED, jobOf("c1", "m1").state)
    assertEquals(WorkInfo.State.CANCELLED, jobOf("c2", "m1").state)
  }

  private companion object {
    const val NOW = 1_000_000L
  }
}
