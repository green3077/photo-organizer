package com.green3077.remotecontrol.net

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream

class ProtocolTest {

    @Test
    fun `frame round trip preserves type and payload`() {
        val out = ByteArrayOutputStream()
        val dataOut = DataOutputStream(out)
        Protocol.writeFrame(dataOut, Protocol.TYPE_HELLO, Protocol.helloPayload("123456"))

        val dataIn = DataInputStream(ByteArrayInputStream(out.toByteArray()))
        val frame = Protocol.readFrame(dataIn)

        assertEquals(Protocol.TYPE_HELLO, frame?.type)
        assertEquals("123456", frame?.payload?.let { String(it, Charsets.US_ASCII) })
    }

    @Test
    fun `hello ack round trip`() {
        val payload = Protocol.helloAckPayload(success = true, width = 1080, height = 2400)
        val ack = Protocol.parseHelloAck(payload)

        assertTrue(ack.success)
        assertEquals(1080, ack.width)
        assertEquals(2400, ack.height)
    }

    @Test
    fun `failed hello ack round trip`() {
        val payload = Protocol.helloAckPayload(success = false, width = 0, height = 0)
        val ack = Protocol.parseHelloAck(payload)

        assertFalse(ack.success)
    }

    @Test
    fun `video frame round trip preserves data and flags`() {
        val original = byteArrayOf(1, 2, 3, 4, 5)
        val payload = Protocol.videoFramePayload(original, 0, original.size, presentationTimeUs = 42L, keyframe = true)
        val frame = Protocol.parseVideoFrame(payload)

        assertTrue(frame.keyframe)
        assertEquals(42L, frame.presentationTimeUs)
        assertArrayEquals(original, frame.data)
    }

    @Test
    fun `input event round trip preserves normalized coordinates`() {
        val payload = Protocol.inputEventPayload(Protocol.INPUT_ACTION_MOVE, 0.25f, 0.75f, pointerId = 0)
        val event = Protocol.parseInputEvent(payload)

        assertEquals(Protocol.INPUT_ACTION_MOVE, event.action)
        assertEquals(0.25f, event.x)
        assertEquals(0.75f, event.y)
    }
}
