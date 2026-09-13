package com.green3077.filefriend.ui

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.green3077.filefriend.databinding.ActivityHomeBinding

class HomeActivity : AppCompatActivity() {
    private lateinit var binding: ActivityHomeBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.buttonSend.setOnClickListener {
            startActivity(Intent(this, SendActivity::class.java))
        }
        binding.buttonReceive.setOnClickListener {
            startActivity(Intent(this, ReceiveActivity::class.java))
        }
    }
}
