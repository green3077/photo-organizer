package com.green3077.remotecontrol.media

import android.media.MediaCodec
import android.media.MediaFormat
import android.view.Surface
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Decodes an H.264 Annex-B elementary stream (as produced by [ScreenEncoder] on the host) and
 * renders it directly onto [outputSurface].
 */
class ScreenDecoder(
    private val width: Int,
    private val height: Int,
    private val outputSurface: Surface,
) {
    private data class Chunk(val data: ByteArray, val presentationTimeUs: Long)

    private lateinit var codec: MediaCodec
    private val pending = LinkedBlockingQueue<Chunk>()
    private val running = AtomicBoolean(false)
    private var inputThread: Thread? = null
    private var outputThread: Thread? = null

    fun start() {
        val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height)
        codec = MediaCodec.createDecoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
        codec.configure(format, outputSurface, null, 0)
        codec.start()
        running.set(true)
        inputThread = Thread(::inputLoop, "ScreenDecoder-input").apply { start() }
        outputThread = Thread(::outputLoop, "ScreenDecoder-output").apply { start() }
    }

    fun submit(data: ByteArray, presentationTimeUs: Long) {
        if (running.get()) pending.offer(Chunk(data, presentationTimeUs))
    }

    private fun inputLoop() {
        while (running.get()) {
            val chunk = pending.poll(100, TimeUnit.MILLISECONDS) ?: continue
            val index = try {
                codec.dequeueInputBuffer(40_000)
            } catch (e: IllegalStateException) {
                break
            }
            if (index < 0) continue
            val buffer = codec.getInputBuffer(index) ?: continue
            buffer.clear()
            buffer.put(chunk.data)
            codec.queueInputBuffer(index, 0, chunk.data.size, chunk.presentationTimeUs, 0)
        }
    }

    private fun outputLoop() {
        val bufferInfo = MediaCodec.BufferInfo()
        while (running.get()) {
            val index = try {
                codec.dequeueOutputBuffer(bufferInfo, 40_000)
            } catch (e: IllegalStateException) {
                break
            }
            if (index >= 0) {
                codec.releaseOutputBuffer(index, true)
            }
        }
    }

    fun stop() {
        if (!running.compareAndSet(true, false)) return
        inputThread?.join(500)
        outputThread?.join(500)
        try {
            codec.stop()
        } catch (e: Exception) {
            // ignore, tearing down anyway
        }
        codec.release()
    }
}
