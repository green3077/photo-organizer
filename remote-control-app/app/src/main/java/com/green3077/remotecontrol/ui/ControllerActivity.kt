package com.green3077.remotecontrol.ui

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.green3077.remotecontrol.R
import com.green3077.remotecontrol.databinding.ActivityControllerBinding
import com.green3077.remotecontrol.net.Protocol

class ControllerActivity : AppCompatActivity() {
    private lateinit var binding: ActivityControllerBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityControllerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.portInput.setText(Protocol.DEFAULT_PORT.toString())

        binding.connectButton.setOnClickListener {
            val ip = binding.ipInput.text?.toString()?.trim().orEmpty()
            val portText = binding.portInput.text?.toString()?.trim().orEmpty()
            val pin = binding.pinInput.text?.toString()?.trim().orEmpty()
            val port = portText.toIntOrNull()

            if (ip.isEmpty() || port == null || pin.length != 6) {
                binding.statusText.text = getString(R.string.controller_failed)
                return@setOnClickListener
            }

            val intent = Intent(this, RemoteViewActivity::class.java).apply {
                putExtra(RemoteViewActivity.EXTRA_HOST, ip)
                putExtra(RemoteViewActivity.EXTRA_PORT, port)
                putExtra(RemoteViewActivity.EXTRA_PIN, pin)
            }
            startActivity(intent)
        }
    }
}
