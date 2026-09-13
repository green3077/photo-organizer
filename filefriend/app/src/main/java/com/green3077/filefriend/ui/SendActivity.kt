package com.green3077.filefriend.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.CountDownTimer
import android.provider.OpenableColumns
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.green3077.filefriend.R
import com.green3077.filefriend.data.ShareInfo
import com.green3077.filefriend.data.ShareRepository
import com.green3077.filefriend.databinding.ActivitySendBinding
import kotlinx.coroutines.launch

class SendActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySendBinding
    private val repository = ShareRepository()
    private var currentShare: ShareInfo? = null
    private var countDownTimer: CountDownTimer? = null

    private val pickFileLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri == null) showChoose() else startUpload(uri)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySendBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.buttonChooseFile.setOnClickListener { launchPicker() }
        binding.buttonRetry.setOnClickListener { launchPicker() }
        binding.buttonCopyCode.setOnClickListener { copyCode() }
        binding.buttonShareCode.setOnClickListener { shareCode() }
        binding.buttonCancelShare.setOnClickListener { cancelShare() }

        if (savedInstanceState == null) {
            launchPicker()
        }
    }

    private fun launchPicker() {
        pickFileLauncher.launch(arrayOf("*/*"))
    }

    private fun startUpload(uri: Uri) {
        showUploading(0)
        lifecycleScope.launch {
            try {
                val (fileName, fileSize) = queryFileInfo(uri)
                val share = repository.uploadAndCreateCode(uri, fileName, fileSize) { percent ->
                    updateUploadProgress(percent)
                }
                currentShare = share
                showCodeReady(share)
            } catch (e: Exception) {
                showError(getString(R.string.send_error_upload_failed))
            }
        }
    }

    private fun queryFileInfo(uri: Uri): Pair<String, Long> {
        var name = "file"
        var size = 0L
        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (cursor.moveToFirst()) {
                if (nameIndex >= 0) name = cursor.getString(nameIndex) ?: name
                if (sizeIndex >= 0) size = cursor.getLong(sizeIndex)
            }
        }
        return name to size
    }

    private fun showChoose() {
        countDownTimer?.cancel()
        binding.groupChoose.visibility = View.VISIBLE
        binding.groupUploading.visibility = View.GONE
        binding.groupCodeReady.visibility = View.GONE
        binding.groupError.visibility = View.GONE
    }

    private fun showUploading(percent: Int) {
        binding.groupChoose.visibility = View.GONE
        binding.groupUploading.visibility = View.VISIBLE
        binding.groupCodeReady.visibility = View.GONE
        binding.groupError.visibility = View.GONE
        updateUploadProgress(percent)
    }

    private fun updateUploadProgress(percent: Int) {
        binding.uploadProgressBar.progress = percent
        binding.uploadProgressText.text = getString(R.string.send_uploading, percent)
    }

    private fun showCodeReady(share: ShareInfo) {
        binding.groupChoose.visibility = View.GONE
        binding.groupUploading.visibility = View.GONE
        binding.groupCodeReady.visibility = View.VISIBLE
        binding.groupError.visibility = View.GONE
        binding.codeText.text = share.code
        startCountdown(share.expiresAtMillis)
    }

    private fun showError(message: String) {
        countDownTimer?.cancel()
        binding.groupChoose.visibility = View.GONE
        binding.groupUploading.visibility = View.GONE
        binding.groupCodeReady.visibility = View.GONE
        binding.groupError.visibility = View.VISIBLE
        binding.errorText.text = message
    }

    private fun startCountdown(expiresAtMillis: Long) {
        countDownTimer?.cancel()
        val remaining = expiresAtMillis - System.currentTimeMillis()
        if (remaining <= 0) {
            showError(getString(R.string.send_expired))
            return
        }
        countDownTimer = object : CountDownTimer(remaining, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val totalSeconds = millisUntilFinished / 1000
                val minutes = totalSeconds / 60
                val seconds = totalSeconds % 60
                binding.timeLeftText.text = getString(
                    R.string.send_time_left,
                    "%02d:%02d".format(minutes, seconds)
                )
            }

            override fun onFinish() {
                showError(getString(R.string.send_expired))
            }
        }.start()
    }

    private fun copyCode() {
        val share = currentShare ?: return
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("code", share.code))
        Toast.makeText(this, R.string.send_code_copied, Toast.LENGTH_SHORT).show()
    }

    private fun shareCode() {
        val share = currentShare ?: return
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, getString(R.string.send_share_text, share.code))
        }
        startActivity(Intent.createChooser(intent, null))
    }

    private fun cancelShare() {
        val share = currentShare
        countDownTimer?.cancel()
        if (share == null) {
            finish()
            return
        }
        lifecycleScope.launch {
            repository.cancelShare(share)
            finish()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        countDownTimer?.cancel()
    }
}
