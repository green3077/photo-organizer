package com.green3077.photoorganizer

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import com.green3077.photoorganizer.databinding.ActivitySplashBinding

/**
 * 앱 실행 시 잠깐(SPLASH_DURATION_MS) 로고 화면을 보여준 뒤 홈 화면으로 넘어간다.
 * 뒤로가기로 다시 스플래시로 돌아오지 않도록 자기 자신은 finish()한다.
 */
class SplashActivity : AppCompatActivity() {

    private val handler = Handler(Looper.getMainLooper())
    private val goToHome = Runnable {
        startActivity(Intent(this, HomeActivity::class.java))
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(binding.root)
        handler.postDelayed(goToHome, SPLASH_DURATION_MS)
    }

    override fun onDestroy() {
        handler.removeCallbacks(goToHome)
        super.onDestroy()
    }

    companion object {
        private const val SPLASH_DURATION_MS = 3000L
    }
}
