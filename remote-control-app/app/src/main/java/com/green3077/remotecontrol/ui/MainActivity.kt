package com.green3077.remotecontrol.ui

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.green3077.remotecontrol.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.hostButton.setOnClickListener {
            startActivity(Intent(this, HostActivity::class.java))
        }
        binding.controllerButton.setOnClickListener {
            startActivity(Intent(this, ControllerActivity::class.java))
        }
    }
}
