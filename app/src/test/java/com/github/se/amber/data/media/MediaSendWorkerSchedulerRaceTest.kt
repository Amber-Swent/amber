// Written by Viktor Jurczenko, with assistance from
// Claude (Anthropic) via Claude Code.
package com.github.se.amber.data.media

import android.content.Context
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
import androidx.work.testing.WorkManagerTestInitHelper.ExecutorsMode
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.asCoroutineDispatcher
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/**
 * Checks the race [MediaSendScheduler.reschedule] exists for: a send has marked its entry FAILED
 * but WorkManager still sees its job running when the user asks to retry.
 *
 * Unlike [MediaSendWorkerSchedulerTest], WorkManager runs here on real background threads: with
 * synchronous executors, a schedule made during a send would only be handled once the job has
 * ended, and the race couldn't happen.
 */
@RunWith(RobolectricTestRunner::class)
class MediaSendWorkerSchedulerRaceTest {

  private val context: Context = ApplicationProvider.getApplicationContext()
  private val workerThread: ExecutorService = Executors.newSingleThreadExecutor()
  private val taskThread: ExecutorService = Executors.newSingleThreadExecutor()
  private val scheduler = MediaSendWorkerScheduler(context)
  private val workManager: WorkManager
    get() = WorkManager.getInstance(context)

  /** Opened when the send has decided its result and its job is still running. */
  private val sendFinishing = CountDownLatch(1)
  /** Lets the send answer. */
  private val answer = CountDownLatch(1)
  private val sender =
      FakeMediaSender(mutableListOf(SendResult.FAILED)).apply {
        beforeAnswer = {
          sendFinishing.countDown()
          answer.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        }
      }

  @Before
  fun setUp() {
    val configuration =
        Configuration.Builder()
            .setExecutor(workerThread)
            .setTaskExecutor(taskThread)
            .setWorkerCoroutineContext(workerThread.asCoroutineDispatcher())
            .setWorkerFactory(MediaSendWorkerFactory { sender })
            .build()
    WorkManagerTestInitHelper.initializeTestWorkManager(
        context,
        configuration,
        ExecutorsMode.PRESERVE_EXECUTORS,
    )
  }

  @After
  fun tearDown() {
    answer.countDown()
    WorkManagerTestInitHelper.closeWorkDatabase()
    workerThread.shutdownNow()
    taskThread.shutdownNow()
  }

  // Waits for the operations already queued on WorkManager's task thread, such as an enqueue.
  private fun jobs(): List<WorkInfo> =
      workManager.getWorkInfosForUniqueWork(WORK_NAME).get(TIMEOUT_SECONDS, TimeUnit.SECONDS)

  /**
   * Runs the media's first job until its send has decided FAILED, calls [retry] while WorkManager
   * still sees the job running, then lets the send answer and waits for the job to end.
   *
   * @return the id of the failed job.
   */
  private fun retryWhileTheFailedSendFinishes(retry: () -> Unit): UUID {
    scheduler.schedule("c1", "m1")
    val failing = jobs().single().id
    WorkManagerTestInitHelper.getTestDriver(context)!!.setAllConstraintsMet(failing)
    waitUntil("the send never ran") { sendFinishing.count == 0L }

    // the race window: the send has decided FAILED, but WorkManager still sees the job running
    assertEquals(WorkInfo.State.RUNNING, workManager.getWorkInfoById(failing).get()?.state)
    retry()
    jobs() // the retry is handled while the job is still running, whatever the threads' timing
    answer.countDown()

    waitUntil("the job never ended") { jobs().none { it.state == WorkInfo.State.RUNNING } }
    return failing
  }

  /**
   * Waits until [done], running what WorkManager posts to the main thread meanwhile (Robolectric
   * runs it only when asked).
   */
  private fun waitUntil(failure: String, done: () -> Boolean) {
    val deadline = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(TIMEOUT_SECONDS)
    while (!done()) {
      check(System.currentTimeMillis() < deadline) { failure }
      shadowOf(Looper.getMainLooper()).idle()
      Thread.sleep(10)
    }
  }

  @Test
  fun scheduleLeavesNoJob() {
    retryWhileTheFailedSendFinishes { scheduler.schedule("c1", "m1") }

    // the retry was ignored: nothing will send the media, although its entry is WAITING again
    assertTrue(jobs().none { it.state == WorkInfo.State.ENQUEUED })
  }

  @Test
  fun rescheduleLeavesANewJob() {
    val failing = retryWhileTheFailedSendFinishes { scheduler.reschedule("c1", "m1") }

    val retry = jobs().single { it.state == WorkInfo.State.ENQUEUED }
    assertNotEquals(failing, retry.id)
    assertEquals(0, retry.runAttemptCount)
  }

  private companion object {
    val WORK_NAME = MediaSendWorkerScheduler.workName("c1", "m1")
    // generous: the first test of the class also pays for starting Robolectric
    const val TIMEOUT_SECONDS = 15L
  }
}
