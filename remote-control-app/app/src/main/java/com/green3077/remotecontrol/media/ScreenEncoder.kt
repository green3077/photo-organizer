package com.green3077.remotecontrol.media

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.os.Bundle
import android.view.Surface
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Encodes whatever is drawn onto [inputSurface] (fed by a [android.hardware.display.VirtualDisplay])
 * into an H.264 Annex-B elementary stream and hands each access unit to [onFrame] on a dedicated
 * drain thread.
 */
class ScreenEncoder(
    private val width: Int,
    private val height: Int,
    private val bitRate: Int = 6_000_000,
    private val frameRate: Int = 30,
    private val iFrameIntervalSeconds: Int = 2,
    private val onFrame: (data: ByteArray, presentationTimeUs: Long, keyframe: Boolean) -> Unit,
) {
    private lateinit var codec: MediaCodec
    lateinit var inputSurface: Surface
        private set

    private val running = AtomicBoolean(false)
    private var drainThread: Thread? = null

    fun start() {
        val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            setInteger(MediaFormat.KEY_BIT_RATE, bitRate)
            setInteger(MediaFormat.KEY_FRAME_RATE, frameRate)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, iFrameIntervalSeconds)
        }
        codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
        codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        inputSurface = codec.createInputSurface()
        codec.start()
        running.set(true)
        drainThread = Thread(::drainLoop, "ScreenEncoder-drain").apply { start() }
    }

    /**
     * Forces the next frame to be a full IDR keyframe. Called whenever a new controller connects
     * so it gets a complete, decodable picture right away instead of waiting for the next
     * scheduled I-frame interval.
     */
    fun requestKeyFrame() {
        val params = Bundle().apply { putInt(MediaCodec.PARAMETER_KEY_REQUEST_SYNC_FRAME, 0) }
        try {
            codec.setParameters(params)
        } catch (e: Exception) {
            // Best-effort: if this fails the periodic I-frame interval still guarantees a keyframe.
        }
    }

    private fun drainLoop() {
        val bufferInfo = MediaCodec.BufferInfo()
        // Some encoders only emit codec-config (SPS/PPS) once at startup rather than before every
        // forced keyframe, so we cache the latest one and prepend it to every keyframe we send —
        // that way a controller that (re)connects after startup always gets a fully self-contained
        // keyframe it can decode on its own.
        var lastConfig: ByteArray? = null
        while (running.get()) {
            val outputIndex = try {
                codec.dequeueOutputBuffer(bufferInfo, 100_000)
            } catch (e: IllegalStateException) {
                break
            }
            if (outputIndex < 0) continue
            val outputBuffer: ByteBuffer? = codec.getOutputBuffer(outputIndex)
            if (outputBuffer != null && bufferInfo.size > 0) {
                outputBuffer.position(bufferInfo.offset)
                outputBuffer.limit(bufferInfo.offset + bufferInfo.size)
                val chunk = ByteArray(bufferInfo.size)
                outputBuffer.get(chunk)

                if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) {
                    lastConfig = chunk
                } else {
                    val isKeyFrame = bufferInfo.flags and MediaCodec.BUFFER_FLAG_KEY_FRAME != 0
                    val config = lastConfig
                    val data = if (isKeyFrame && config != null) config + chunk else chunk
                    onFrame(data, bufferInfo.presentationTimeUs, isKeyFrame)
                }
            }
            codec.releaseOutputBuffer(outputIndex, false)
            if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
        }
    }

    fun stop() {
        if (!running.compareAndSet(true, false)) return
        drainThread?.join(500)
        drainThread = null
        try {
            codec.stop()
        } catch (e: Exception) {
            // ignore
        }
        codec.release()
        inputSurface.release()
    }
}
