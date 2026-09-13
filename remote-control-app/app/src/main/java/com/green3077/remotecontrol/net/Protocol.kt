package com.green3077.remotecontrol.net

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException

/**
 * Minimal length-prefixed binary protocol used between the host and the controller.
 * Wire format for every message: [4-byte big-endian payload length][1-byte type][payload].
 * This link is unencrypted and is only intended for use on a trusted local network between
 * two devices whose owner has explicitly paired them with a freshly generated PIN.
 */
object Protocol {
    const val DEFAULT_PORT = 47631

    const val TYPE_HELLO: Int = 1
    const val TYPE_HELLO_ACK: Int = 2
    const val TYPE_VIDEO_CONFIG: Int = 3
    const val TYPE_VIDEO_FRAME: Int = 4
    const val TYPE_INPUT_EVENT: Int = 5
    const val TYPE_BYE: Int = 6

    const val INPUT_ACTION_DOWN: Int = 0
    const val INPUT_ACTION_MOVE: Int = 1
    const val INPUT_ACTION_UP: Int = 2

    const val VIDEO_FLAG_KEYFRAME: Int = 0x1

    class Frame(val type: Int, val payload: ByteArray)

    fun writeFrame(out: DataOutputStream, type: Int, payload: ByteArray = ByteArray(0)) {
        out.writeInt(payload.size)
        out.writeByte(type)
        if (payload.isNotEmpty()) out.write(payload)
        out.flush()
    }

    /** Returns null if the stream is closed cleanly before a new frame starts. */
    fun readFrame(input: DataInputStream): Frame? {
        val length = try {
            input.readInt()
        } catch (e: EOFException) {
            return null
        }
        val type = input.readUnsignedByte()
        val payload = if (length > 0) {
            val buf = ByteArray(length)
            input.readFully(buf)
            buf
        } else {
            ByteArray(0)
        }
        return Frame(type, payload)
    }

    fun helloPayload(pin: String): ByteArray = pin.toByteArray(Charsets.US_ASCII)

    fun helloAckPayload(success: Boolean, width: Int, height: Int): ByteArray {
        val buf = java.nio.ByteBuffer.allocate(1 + 4 + 4)
        buf.put(if (success) 1 else 0)
        buf.putInt(width)
        buf.putInt(height)
        return buf.array()
    }

    data class HelloAck(val success: Boolean, val width: Int, val height: Int)

    fun parseHelloAck(payload: ByteArray): HelloAck {
        val buf = java.nio.ByteBuffer.wrap(payload)
        val success = buf.get().toInt() == 1
        val width = buf.int
        val height = buf.int
        return HelloAck(success, width, height)
    }

    fun videoConfigPayload(width: Int, height: Int): ByteArray {
        val buf = java.nio.ByteBuffer.allocate(4 + 4)
        buf.putInt(width)
        buf.putInt(height)
        return buf.array()
    }

    data class VideoConfig(val width: Int, val height: Int)

    fun parseVideoConfig(payload: ByteArray): VideoConfig {
        val buf = java.nio.ByteBuffer.wrap(payload)
        return VideoConfig(buf.int, buf.int)
    }

    fun videoFramePayload(data: ByteArray, offset: Int, size: Int, presentationTimeUs: Long, keyframe: Boolean): ByteArray {
        val buf = java.nio.ByteBuffer.allocate(1 + 8 + size)
        buf.put(if (keyframe) VIDEO_FLAG_KEYFRAME.toByte() else 0)
        buf.putLong(presentationTimeUs)
        buf.put(data, offset, size)
        return buf.array()
    }

    class VideoFrame(val keyframe: Boolean, val presentationTimeUs: Long, val data: ByteArray)

    fun parseVideoFrame(payload: ByteArray): VideoFrame {
        val buf = java.nio.ByteBuffer.wrap(payload)
        val flags = buf.get().toInt()
        val pts = buf.long
        val data = ByteArray(payload.size - 9)
        buf.get(data)
        return VideoFrame((flags and VIDEO_FLAG_KEYFRAME) != 0, pts, data)
    }

    fun inputEventPayload(action: Int, xNormalized: Float, yNormalized: Float, pointerId: Int): ByteArray {
        val buf = java.nio.ByteBuffer.allocate(1 + 4 + 4 + 4)
        buf.put(action.toByte())
        buf.putFloat(xNormalized)
        buf.putFloat(yNormalized)
        buf.putInt(pointerId)
        return buf.array()
    }

    data class InputEvent(val action: Int, val x: Float, val y: Float, val pointerId: Int)

    fun parseInputEvent(payload: ByteArray): InputEvent {
        val buf = java.nio.ByteBuffer.wrap(payload)
        val action = buf.get().toInt()
        val x = buf.float
        val y = buf.float
        val pointerId = buf.int
        return InputEvent(action, x, y, pointerId)
    }
}
