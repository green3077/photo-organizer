package com.green3077.photoorganizer

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import com.green3077.photoorganizer.data.ChallengeSettings
import com.green3077.photoorganizer.databinding.ActivitySettingsBinding
import com.green3077.photoorganizer.notification.WorkScheduler
import java.time.LocalTime

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        binding.toolbar.setNavigationOnClickListener { finish() }

        binding.switchEnabled.isChecked = ChallengeSettings.isEnabled(this)
        updateTimeRowEnabled()
        updateTimeLabel()

        binding.switchEnabled.setOnCheckedChangeListener { _, checked ->
            ChallengeSettings.setEnabled(this, checked)
            updateTimeRowEnabled()
            WorkScheduler.applySettings(this)
        }

        binding.rowTime.setOnClickListener { showTimePicker() }
    }

    private fun updateTimeRowEnabled() {
        val enabled = ChallengeSettings.isEnabled(this)
        binding.rowTime.isEnabled = enabled
        binding.rowTime.alpha = if (enabled) 1f else 0.4f
    }

    private fun updateTimeLabel() {
        val time = ChallengeSettings.notifyTime(this)
        binding.textTimeValue.text = "%02d:%02d".format(time.hour, time.minute)
    }

    private fun showTimePicker() {
        val current = ChallengeSettings.notifyTime(this)
        val picker = MaterialTimePicker.Builder()
            .setTimeFormat(TimeFormat.CLOCK_24H)
            .setHour(current.hour)
            .setMinute(current.minute)
            .setTitleText(getString(R.string.settings_time_label))
            .build()
        picker.addOnPositiveButtonClickListener {
            ChallengeSettings.setNotifyTime(this, LocalTime.of(picker.hour, picker.minute))
            updateTimeLabel()
            WorkScheduler.applySettings(this)
        }
        picker.show(supportFragmentManager, "time_picker")
    }
}
