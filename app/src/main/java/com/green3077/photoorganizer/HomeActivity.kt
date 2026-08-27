package com.green3077.photoorganizer

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.green3077.photoorganizer.databinding.ActivityHomeBinding

/**
 * 앱을 열면 바로 사진 목록이 나오지 않고, "사진정리 챌린지"와 "날짜별 정리" 두 메뉴만
 * 심플하게 보여준다. 실제 사진 접근 권한 확인/요청은 각 화면(ChallengeActivity,
 * MainActivity)에서 필요할 때 처리한다.
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
        binding.btnTrash.setOnClickListener {
            startActivity(Intent(this, TrashActivity::class.java))
        }
        binding.cardChallenge.setOnClickListener {
            startActivity(Intent(this, ChallengeActivity::class.java))
        }
        binding.cardDateOrganize.setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java))
        }
    }
}
