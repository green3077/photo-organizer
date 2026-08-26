package com.green3077.photoorganizer

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import com.green3077.photoorganizer.data.ChallengeSettings
import com.green3077.photoorganizer.databinding.ActivitySettingsBinding
import com.green3077.photoorganizer.notification.WorkScheduler
import com.green3077.photoorganizer.update.UpdateCheckResult
import com.green3077.photoorganizer.update.UpdateChecker
import com.green3077.photoorganizer.update.UpdateDownloader
import com.green3077.photoorganizer.update.UpdateInfo
import kotlinx.coroutines.launch
import java.time.LocalTime

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private val updateDownloader by lazy { UpdateDownloader(this) }

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

        binding.textCurrentVersion.text = getString(R.string.settings_current_version, BuildConfig.VERSION_NAME)
        binding.rowCheckUpdate.setOnClickListener { checkForUpdate() }
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

    private fun checkForUpdate() {
        setUpdateChecking(true)
        lifecycleScope.launch {
            when (val result = UpdateChecker.checkForUpdate()) {
                is UpdateCheckResult.Available -> showUpdateAvailableDialog(result.update)
                is UpdateCheckResult.UpToDate ->
                    Toast.makeText(this@SettingsActivity, R.string.update_up_to_date, Toast.LENGTH_SHORT).show()
                is UpdateCheckResult.Error ->
                    Toast.makeText(this@SettingsActivity, R.string.update_check_failed, Toast.LENGTH_SHORT).show()
            }
            setUpdateChecking(false)
        }
    }

    private fun setUpdateChecking(checking: Boolean) {
        binding.progressUpdateCheck.visibility = if (checking) View.VISIBLE else View.GONE
        binding.rowCheckUpdate.isEnabled = !checking
    }

    private fun showUpdateAvailableDialog(update: UpdateInfo) {
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.update_available_title))
            .setMessage(getString(R.string.update_available_message, update.versionName, BuildConfig.VERSION_NAME))
            .setPositiveButton(getString(R.string.update_now)) { _, _ ->
                updateDownloader.download(update) {
                    Toast.makeText(this, R.string.update_download_failed, Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(getString(R.string.update_later), null)
            .show()
    }
}
