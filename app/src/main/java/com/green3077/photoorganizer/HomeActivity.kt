package com.green3077.photoorganizer

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.green3077.photoorganizer.databinding.ActivityHomeBinding

/**
 * 앱을 열면 바로 사진 목록이 나오지 않고, "날짜별 정리"/"달력으로 보기" 메뉴로 들어가는
 * 진입 화면. 실제 사진 접근 권한 확인/요청은 각 화면(MainActivity, CalendarActivity)에서
 * 필요할 때 처리한다.
 */
class HomeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHomeBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)

        binding.btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        binding.cardDateOrganize.setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java))
        }
        binding.cardCalendar.setOnClickListener {
            startActivity(Intent(this, CalendarActivity::class.java))
        }
    }
}
