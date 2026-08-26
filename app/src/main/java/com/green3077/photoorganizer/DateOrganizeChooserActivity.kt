package com.green3077.photoorganizer

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.green3077.photoorganizer.databinding.ActivityDateOrganizeChooserBinding

/** 홈 화면 "날짜별 정리"를 누르면 뜨는 중간 화면 — 일별/월별 정리 중 고른다. */
class DateOrganizeChooserActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDateOrganizeChooserBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDateOrganizeChooserBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        binding.toolbar.setNavigationOnClickListener { finish() }

        binding.cardDay.setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java))
        }
        binding.cardMonth.setOnClickListener {
            startActivity(Intent(this, MonthActivity::class.java))
        }
    }
}
