package com.green3077.remotecontrol.net

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.atomic.AtomicBoolean

/** Client side of the pairing/streaming protocol; owns the socket and its reader thread. */
class ControllerClient(private val onEvent: (Event) -> Unit) {

    sealed class Event {
        data class Connected(val width: Int, val height: Int) : Event()
        data class Failed(val reason: String) : Event()
        data class VideoFrame(val data: ByteArray, val presentationTimeUs: Long, val keyframe: Boolean) : Event()
        object Disconnected : Event()
    }

    private var socket: Socket? = null
    @Volatile private var output: DataOutputStream? = null
    private val running = AtomicBoolean(false)

    fun connect(host: String, port: Int, pin: String) {
        Thread({
            var connected = false
            try {
                val s = Socket()
                s.connect(InetSocketAddress(host, port), 5000)
                socket = s
                val input = DataInputStream(s.getInputStream())
                val out = DataOutputStream(s.getOutputStream())
                output = out

                Protocol.writeFrame(out, Protocol.TYPE_HELLO, Protocol.helloPayload(pin))
                val ackFrame = Protocol.readFrame(input) ?: throw IOException("연결이 종료되었습니다")
                if (ackFrame.type != Protocol.TYPE_HELLO_ACK) throw IOException("잘못된 응답")
                val ack = Protocol.parseHelloAck(ackFrame.payload)
                if (!ack.success) {
                    onEvent(Event.Failed("PIN이 일치하지 않습니다"))
                    return@Thread
                }

                connected = true
                running.set(true)
                onEvent(Event.Connected(ack.width, ack.height))

                while (running.get()) {
                    val frame = Protocol.readFrame(input) ?: break
                    if (frame.type == Protocol.TYPE_VIDEO_FRAME) {
                        val vf = Protocol.parseVideoFrame(frame.payload)
                        onEvent(Event.VideoFrame(vf.data, vf.presentationTimeUs, vf.keyframe))
                    }
                }
            } catch (e: Exception) {
                if (!connected) onEvent(Event.Failed(e.message ?: "연결 실패"))
            } finally {
                running.set(false)
                onEvent(Event.Disconnected)
                closeQuietly()
            }
        }, "ControllerClient").apply { start() }
    }

    fun sendInput(action: Int, xNormalized: Float, yNormalized: Float) {
        val out = output ?: return
        try {
            Protocol.writeFrame(out, Protocol.TYPE_INPUT_EVENT, Protocol.inputEventPayload(action, xNormalized, yNormalized, 0))
        } catch (e: Exception) {
            // Connection likely dropped; the reader loop will notice and report Disconnected.
        }
    }

    fun disconnect() {
        if (running.compareAndSet(true, false)) {
            try {
                output?.let { Protocol.writeFrame(it, Protocol.TYPE_BYE) }
            } catch (e: Exception) {
                // ignore
            }
        }
        closeQuietly()
    }

    private fun closeQuietly() {
        try {
            socket?.close()
        } catch (e: Exception) {
            // ignore
        }
        socket = null
        output = null
    }
}
