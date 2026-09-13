package com.green3077.remotecontrol.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import com.green3077.remotecontrol.R
import com.green3077.remotecontrol.media.ScreenEncoder
import com.green3077.remotecontrol.net.Protocol
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.ServerSocket
import java.net.Socket

/**
 * Owns the whole host-side pipeline: MediaProjection screen capture, H.264 encoding, and the TCP
 * server that streams video to a paired controller and relays its input back to the
 * accessibility service. Runs as a foreground service so the (Android-mandated) screen-capture
 * notification stays visible the entire time sharing is active.
 */
class HostForegroundService : Service() {

    enum class Status { WAITING, CONNECTED, STOPPED, ERROR }

    interface Listener {
        fun onStatus(status: Status)
    }

    companion object {
        private const val TAG = "HostForegroundService"
        private const val CHANNEL_ID = "host_share"
        private const val NOTIFICATION_ID = 1001

        const val ACTION_START = "com.green3077.remotecontrol.action.START"
        const val ACTION_STOP = "com.green3077.remotecontrol.action.STOP"
        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_RESULT_DATA = "result_data"
        const val EXTRA_PIN = "pin"

        @Volatile
        var listener: Listener? = null
    }

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var encoder: ScreenEncoder? = null
    private var serverSocket: ServerSocket? = null
    private var serverThread: Thread? = null

    @Volatile private var activeOutput: DataOutputStream? = null
    @Volatile private var activeSocket: Socket? = null
    @Volatile private var screenWidth: Int = 0
    @Volatile private var screenHeight: Int = 0
    private var pin: String = ""

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> handleStart(intent)
            ACTION_STOP -> {
                stopSharing()
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun handleStart(intent: Intent) {
        ensureNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())

        pin = intent.getStringExtra(EXTRA_PIN).orEmpty()
        val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, 0)
        val resultData = intent.getParcelableExtra<Intent>(EXTRA_RESULT_DATA)
        if (resultData == null) {
            Log.e(TAG, "Missing projection data")
            listener?.onStatus(Status.ERROR)
            stopSelf()
            return
        }

        val projectionManager =
            getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val projection = projectionManager.getMediaProjection(resultCode, resultData)
        if (projection == null) {
            listener?.onStatus(Status.ERROR)
            stopSelf()
            return
        }
        mediaProjection = projection
        projection.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() {
                stopSharing()
            }
        }, null)

        val metrics = DisplayMetrics()
        val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        @Suppress("DEPRECATION")
        wm.defaultDisplay.getRealMetrics(metrics)
        screenWidth = metrics.widthPixels
        screenHeight = metrics.heightPixels

        val screenEncoder = ScreenEncoder(screenWidth, screenHeight) { data, presentationTimeUs, keyframe ->
            sendVideoFrame(data, presentationTimeUs, keyframe)
        }
        screenEncoder.start()
        encoder = screenEncoder

        virtualDisplay = mediaProjection?.createVirtualDisplay(
            "RemoteControlCapture",
            screenWidth,
            screenHeight,
            metrics.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            screenEncoder.inputSurface,
            null,
            null,
        )

        startServer()
        listener?.onStatus(Status.WAITING)
    }

    private fun startServer() {
        serverThread = Thread({
            try {
                ServerSocket(Protocol.DEFAULT_PORT).use { server ->
                    serverSocket = server
                    while (!Thread.currentThread().isInterrupted) {
                        val socket = try {
                            server.accept()
                        } catch (e: Exception) {
                            break
                        }
                        handleClient(socket)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Server error", e)
            }
        }, "HostServer").apply { start() }
    }

    private fun handleClient(socket: Socket) {
        // Only one controller at a time; refuse a second concurrent connection.
        if (activeSocket != null) {
            socket.close()
            return
        }
        try {
            val input = DataInputStream(socket.getInputStream())
            val output = DataOutputStream(socket.getOutputStream())

            val hello = Protocol.readFrame(input)
            if (hello == null || hello.type != Protocol.TYPE_HELLO) {
                socket.close()
                return
            }
            val receivedPin = String(hello.payload, Charsets.US_ASCII)
            val success = receivedPin == pin
            Protocol.writeFrame(
                output,
                Protocol.TYPE_HELLO_ACK,
                Protocol.helloAckPayload(success, screenWidth, screenHeight),
            )
            if (!success) {
                socket.close()
                return
            }

            Protocol.writeFrame(output, Protocol.TYPE_VIDEO_CONFIG, Protocol.videoConfigPayload(screenWidth, screenHeight))

            activeSocket = socket
            activeOutput = output
            listener?.onStatus(Status.CONNECTED)

            while (true) {
                val frame = Protocol.readFrame(input) ?: break
                when (frame.type) {
                    Protocol.TYPE_INPUT_EVENT -> {
                        val event = Protocol.parseInputEvent(frame.payload)
                        val xPx = event.x * screenWidth
                        val yPx = event.y * screenHeight
                        RemoteControlAccessibilityService.instance?.handleInput(event.action, xPx, yPx)
                    }
                    Protocol.TYPE_BYE -> break
                }
            }
        } catch (e: Exception) {
            Log.i(TAG, "Client disconnected: ${e.message}")
        } finally {
            activeOutput = null
            activeSocket = null
            try {
                socket.close()
            } catch (e: Exception) {
                // ignore
            }
            if (mediaProjection != null) {
                listener?.onStatus(Status.WAITING)
            }
        }
    }

    private fun sendVideoFrame(data: ByteArray, presentationTimeUs: Long, keyframe: Boolean) {
        val output = activeOutput ?: return
        try {
            Protocol.writeFrame(
                output,
                Protocol.TYPE_VIDEO_FRAME,
                Protocol.videoFramePayload(data, 0, data.size, presentationTimeUs, keyframe),
            )
        } catch (e: Exception) {
            activeOutput = null
        }
    }

    private fun stopSharing() {
        try {
            serverSocket?.close()
        } catch (e: Exception) {
            // ignore
        }
        serverThread?.interrupt()
        serverThread = null
        serverSocket = null

        try {
            activeSocket?.close()
        } catch (e: Exception) {
            // ignore
        }
        activeSocket = null
        activeOutput = null

        encoder?.stop()
        encoder = null
        virtualDisplay?.release()
        virtualDisplay = null
        mediaProjection?.stop()
        mediaProjection = null

        listener?.onStatus(Status.STOPPED)
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    override fun onDestroy() {
        stopSharing()
        super.onDestroy()
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.host_notification_channel),
                NotificationManager.IMPORTANCE_LOW,
            )
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification =
        Notification.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.host_notification_title))
            .setContentText(getString(R.string.host_notification_text))
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setOngoing(true)
            .build()
}
