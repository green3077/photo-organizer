package com.green3077.photoorganizer

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.green3077.photoorganizer.databinding.ActivityPlaceChooserBinding

/** 홈 화면 "장소별 정리"를 누르면 뜨는 중간 화면 — 나라별(해외)/지역별(국내) 정리 중 고른다. */
class PlaceChooserActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPlaceChooserBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPlaceChooserBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        binding.toolbar.setNavigationOnClickListener { finish() }

        binding.cardCountry.setOnClickListener {
            startActivity(Intent(this, LocationActivity::class.java))
        }
        binding.cardRegion.setOnClickListener {
            startActivity(Intent(this, RegionActivity::class.java))
        }
    }
}
