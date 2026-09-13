package com.green3077.remotecontrol.ui

import android.graphics.SurfaceTexture
import android.os.Bundle
import android.os.SystemClock
import android.view.MotionEvent
import android.view.Surface
import android.view.TextureView
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.green3077.remotecontrol.R
import com.green3077.remotecontrol.databinding.ActivityRemoteViewBinding
import com.green3077.remotecontrol.media.ScreenDecoder
import com.green3077.remotecontrol.net.ControllerClient
import com.green3077.remotecontrol.net.Protocol

class RemoteViewActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_HOST = "host"
        const val EXTRA_PORT = "port"
        const val EXTRA_PIN = "pin"

        /** Minimum spacing between two ACTION_MOVE sends, to avoid flooding the network and the
         * host's gesture-dispatch pipeline with more updates than a touch drag actually needs. */
        private const val MOVE_THROTTLE_MS = 30L
    }

    private lateinit var binding: ActivityRemoteViewBinding
    private lateinit var client: ControllerClient
    private lateinit var host: String
    private var port: Int = Protocol.DEFAULT_PORT
    private lateinit var pin: String

    private var surface: Surface? = null
    private var remoteWidth = 0
    private var remoteHeight = 0
    private var decoder: ScreenDecoder? = null
    private var lastMoveSentAt = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRemoteViewBinding.inflate(layoutInflater)
        setContentView(binding.root)

        host = intent.getStringExtra(EXTRA_HOST).orEmpty()
        port = intent.getIntExtra(EXTRA_PORT, Protocol.DEFAULT_PORT)
        pin = intent.getStringExtra(EXTRA_PIN).orEmpty()

        binding.disconnectButton.setOnClickListener { finish() }
        binding.reconnectButton.setOnClickListener { reconnect() }

        binding.remoteTexture.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(texture: SurfaceTexture, width: Int, height: Int) {
                surface = Surface(texture)
                maybeStartDecoder()
            }

            override fun onSurfaceTextureSizeChanged(texture: SurfaceTexture, width: Int, height: Int) = Unit

            override fun onSurfaceTextureDestroyed(texture: SurfaceTexture): Boolean {
                decoder?.stop()
                decoder = null
                surface = null
                return true
            }

            override fun onSurfaceTextureUpdated(texture: SurfaceTexture) = Unit
        }

        binding.remoteTexture.setOnTouchListener { view, event -> handleTouch(view, event) }

        client = ControllerClient { event -> runOnUiThread { handleEvent(event) } }
        beginConnect()
    }

    private fun beginConnect() {
        binding.reconnectButton.visibility = View.GONE
        binding.overlayStatusText.text = getString(R.string.controller_connecting)
        client.connect(host, port, pin)
    }

    private fun reconnect() {
        // The previous connection is fully torn down by the time Disconnected fires, so the
        // decoder (still holding the render surface) can simply keep waiting for fresh frames.
        beginConnect()
    }

    private fun handleEvent(event: ControllerClient.Event) {
        when (event) {
            is ControllerClient.Event.Connected -> {
                remoteWidth = event.width
                remoteHeight = event.height
                binding.overlayStatusText.text = ""
                binding.reconnectButton.visibility = View.GONE
                maybeStartDecoder()
            }
            is ControllerClient.Event.Failed -> {
                binding.overlayStatusText.text = event.reason
            }
            is ControllerClient.Event.VideoFrame -> {
                decoder?.submit(event.data, event.presentationTimeUs)
            }
            ControllerClient.Event.Disconnected -> {
                if (binding.overlayStatusText.text.isNullOrEmpty()) {
                    binding.overlayStatusText.text = getString(R.string.controller_failed)
                }
                binding.reconnectButton.visibility = View.VISIBLE
            }
        }
    }

    private fun maybeStartDecoder() {
        val s = surface ?: return
        if (remoteWidth == 0 || remoteHeight == 0) return
        if (decoder != null) return
        decoder = ScreenDecoder(remoteWidth, remoteHeight, s).apply { start() }
    }

    private fun handleTouch(view: View, event: MotionEvent): Boolean {
        val x = (event.x / view.width).coerceIn(0f, 1f)
        val y = (event.y / view.height).coerceIn(0f, 1f)
        val action = when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> Protocol.INPUT_ACTION_DOWN
            MotionEvent.ACTION_MOVE -> {
                val now = SystemClock.elapsedRealtime()
                if (now - lastMoveSentAt < MOVE_THROTTLE_MS) return true
                lastMoveSentAt = now
                Protocol.INPUT_ACTION_MOVE
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> Protocol.INPUT_ACTION_UP
            else -> return false
        }
        client.sendInput(action, x, y)
        return true
    }

    override fun onDestroy() {
        super.onDestroy()
        client.disconnect()
        decoder?.stop()
        decoder = null
    }
}
