package com.green3077.remotecontrol.ui

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.green3077.remotecontrol.R
import com.green3077.remotecontrol.databinding.ActivityHostBinding
import com.green3077.remotecontrol.net.Protocol
import com.green3077.remotecontrol.service.HostForegroundService
import com.green3077.remotecontrol.service.RemoteControlAccessibilityService
import com.green3077.remotecontrol.util.NetworkUtils
import com.green3077.remotecontrol.util.PinGenerator

class HostActivity : AppCompatActivity() {
    private lateinit var binding: ActivityHostBinding
    private var sharing = false
    private var currentPin: String = ""

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { requestScreenCapture() }

    private val screenCaptureLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK && result.data != null) {
                startSharing(result.resultCode, result.data!!)
            }
        }

    private val statusListener = object : HostForegroundService.Listener {
        override fun onStatus(status: HostForegroundService.Status) {
            runOnUiThread { renderStatus(status) }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHostBinding.inflate(layoutInflater)
        setContentView(binding.root)

        HostForegroundService.listener = statusListener

        binding.startStopButton.setOnClickListener {
            if (sharing) stopSharing() else beginStartFlow()
        }
        binding.accessibilitySettingsButton.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
    }

    override fun onResume() {
        super.onResume()
        updateAccessibilityWarning()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (HostForegroundService.listener === statusListener) {
            HostForegroundService.listener = null
        }
    }

    private fun beginStartFlow() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            requestScreenCapture()
        }
    }

    private fun requestScreenCapture() {
        val manager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        screenCaptureLauncher.launch(manager.createScreenCaptureIntent())
    }

    private fun startSharing(resultCode: Int, data: Intent) {
        currentPin = PinGenerator.generate()
        val intent = Intent(this, HostForegroundService::class.java).apply {
            action = HostForegroundService.ACTION_START
            putExtra(HostForegroundService.EXTRA_RESULT_CODE, resultCode)
            putExtra(HostForegroundService.EXTRA_RESULT_DATA, data)
            putExtra(HostForegroundService.EXTRA_PIN, currentPin)
        }
        ContextCompat.startForegroundService(this, intent)

        sharing = true
        val ip = NetworkUtils.findLocalIpAddress()
        binding.addressText.text = if (ip != null) "$ip:${Protocol.DEFAULT_PORT}" else getString(R.string.host_ip_unknown)
        binding.pinText.text = currentPin
        binding.startStopButton.text = getString(R.string.host_stop)
        renderStatus(HostForegroundService.Status.WAITING)
    }

    private fun stopSharing() {
        val intent = Intent(this, HostForegroundService::class.java).apply {
            action = HostForegroundService.ACTION_STOP
        }
        startService(intent)
        sharing = false
        binding.startStopButton.text = getString(R.string.host_start)
        renderStatus(HostForegroundService.Status.STOPPED)
    }

    private fun renderStatus(status: HostForegroundService.Status) {
        binding.statusText.text = when (status) {
            HostForegroundService.Status.WAITING -> getString(R.string.host_status_waiting)
            HostForegroundService.Status.CONNECTED -> getString(R.string.host_status_connected)
            HostForegroundService.Status.STOPPED -> getString(R.string.host_status_stopped)
            HostForegroundService.Status.ERROR -> getString(R.string.host_status_stopped)
        }
    }

    private fun updateAccessibilityWarning() {
        val enabled = isAccessibilityServiceEnabled()
        binding.accessibilityWarning.visibility = if (enabled) View.GONE else View.VISIBLE
        binding.accessibilitySettingsButton.visibility = if (enabled) View.GONE else View.VISIBLE
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val expected = ComponentName(this, RemoteControlAccessibilityService::class.java).flattenToString()
        val enabledServices = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ) ?: return false
        return enabledServices.split(':').any { it.equals(expected, ignoreCase = true) }
    }
}
