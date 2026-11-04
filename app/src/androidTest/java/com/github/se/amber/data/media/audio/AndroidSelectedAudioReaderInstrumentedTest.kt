// Written by Viktor Jurczenko, with assistance from Claude (Anthropic) via Claude Code.
package com.github.se.amber.data.media.audio

import android.content.Context
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import androidx.core.net.toUri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Checks [AndroidSelectedAudioReader] with Android's real [MediaMetadataRetriever], which the unit
 * tests mock: the type it reads from the content of a real .m4a must be one of [M4A_TYPES], or the
 * reader would refuse every .m4a, our own recordings included.
 */
@RunWith(AndroidJUnit4::class)
class AndroidSelectedAudioReaderInstrumentedTest {

  private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
  private val source = File(context.cacheDir, "reader-test.m4a")
  private val read = mutableListOf<File>()

  @After
  fun tearDown() {
    source.delete()
    read.forEach { it.delete() }
  }

  @Test
  fun theRetrieverReadsAudioMp4FromTheContentOfAnM4a() {
    writeSilentM4a(source, durationUs = 1_000_000)

    val retriever = MediaMetadataRetriever()
    val contentType =
        try {
          retriever.setDataSource(source.path)
          retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE)
        } finally {
          retriever.release()
        }

    assertEquals("audio/mp4", contentType)
  }

  @Test
  fun acceptsARealM4aWithItsDuration() = runBlocking {
    writeSilentM4a(source, durationUs = 1_000_000)

    // a file URI gives no type, so only the content decides
    val audio = AndroidSelectedAudioReader(context).read(source.toUri().toString())
    read += audio.file

    assertTrue("duration ${audio.durationMs} ms", audio.durationMs in 900L..1_100L)
  }

  /**
   * Writes [durationUs] of silence into [file] as an .m4a (AAC, mono, 44.1 kHz), the format the
   * recorder uses, with Android's own encoder. It needs no microphone, which the CI emulator lacks.
   */
  private fun writeSilentM4a(file: File, durationUs: Long) {
    val totalBytes = SAMPLE_RATE * 2L * durationUs / 1_000_000 // 16-bit mono samples
    val format =
        MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, SAMPLE_RATE, 1).apply {
          setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
          setInteger(MediaFormat.KEY_BIT_RATE, 64_000)
        }
    val encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
    val muxer = MediaMuxer(file.path, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
    try {
      encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
      encoder.start()
      val info = MediaCodec.BufferInfo()
      var fed = 0L
      var track = -1
      while (true) {
        if (fed < totalBytes) {
          val index = encoder.dequeueInputBuffer(TIMEOUT_US)
          if (index >= 0) {
            val buffer = encoder.getInputBuffer(index)!!
            val size = minOf(buffer.capacity().toLong(), totalBytes - fed).toInt()
            buffer.clear()
            buffer.put(ByteArray(size))
            val timeUs = fed / 2 * 1_000_000 / SAMPLE_RATE
            fed += size
            val flags = if (fed == totalBytes) MediaCodec.BUFFER_FLAG_END_OF_STREAM else 0
            encoder.queueInputBuffer(index, 0, size, timeUs, flags)
          }
        }
        val index = encoder.dequeueOutputBuffer(info, TIMEOUT_US)
        if (index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
          track = muxer.addTrack(encoder.outputFormat)
          muxer.start()
        } else if (index >= 0) {
          val isConfig = info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0
          if (!isConfig && info.size > 0) {
            muxer.writeSampleData(track, encoder.getOutputBuffer(index)!!, info)
          }
          encoder.releaseOutputBuffer(index, false)
          if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
        }
      }
      muxer.stop()
    } finally {
      encoder.release()
      muxer.release()
    }
  }

  private companion object {
    const val SAMPLE_RATE = 44_100
    const val TIMEOUT_US = 10_000L
  }
}
