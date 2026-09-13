package com.green3077.filefriend.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.webkit.MimeTypeMap
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.green3077.filefriend.R
import com.green3077.filefriend.data.DownloadUtils
import com.green3077.filefriend.data.FetchResult
import com.green3077.filefriend.data.ShareInfo
import com.green3077.filefriend.data.ShareRepository
import com.green3077.filefriend.databinding.ActivityReceiveBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ReceiveActivity : AppCompatActivity() {
    private lateinit var binding: ActivityReceiveBinding
    private val repository = ShareRepository()
    private var savedFileName: String? = null
    private var savedUri: Uri? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityReceiveBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.buttonReceive.setOnClickListener { onReceiveClicked() }
        binding.buttonErrorRetry.setOnClickListener { showInput() }
        binding.buttonOpenFile.setOnClickListener { openSavedFile() }
        binding.buttonReceiveDone.setOnClickListener { finish() }
    }

    private fun onReceiveClicked() {
        val code = binding.codeInput.text?.toString().orEmpty()
        if (code.length != 3 || code.any { !it.isDigit() }) {
            showError(getString(R.string.receive_error_invalid_code))
            return
        }
        showDownloading(0)
        lifecycleScope.launch {
            try {
                when (val result = repository.fetchShare(code)) {
                    is FetchResult.Found -> downloadAndSave(result.share)
                    FetchResult.Expired -> showError(getString(R.string.receive_error_expired))
                    FetchResult.NotFound -> showError(getString(R.string.receive_error_not_found))
                }
            } catch (e: Exception) {
                showError(getString(R.string.receive_error_download_failed))
            }
        }
    }

    private suspend fun downloadAndSave(share: ShareInfo) {
        try {
            val uri = withContext(Dispatchers.IO) {
                val input = repository.openDownloadStream(share.storagePath)
                DownloadUtils.saveToDownloads(applicationContext, share.fileName, input) { bytesCopied ->
                    val percent = if (share.fileSize > 0) {
                        (bytesCopied * 100 / share.fileSize).toInt().coerceIn(0, 100)
                    } else 0
                    runOnUiThread { updateDownloadProgress(percent) }
                }
            }
            repository.consumeShare(share)
            savedFileName = share.fileName
            savedUri = uri
            showSuccess(share.fileName)
        } catch (e: Exception) {
            showError(getString(R.string.receive_error_download_failed))
        }
    }

    private fun openSavedFile() {
        val uri = savedUri ?: return
        val extension = savedFileName.orEmpty().substringAfterLast('.', "")
        val mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension.lowercase())
            ?: "*/*"
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(intent)
    }

    private fun showInput() {
        binding.codeInput.text?.clear()
        binding.groupInput.visibility = View.VISIBLE
        binding.groupDownloading.visibility = View.GONE
        binding.groupSuccess.visibility = View.GONE
        binding.groupError.visibility = View.GONE
    }

    private fun showDownloading(percent: Int) {
        binding.groupInput.visibility = View.GONE
        binding.groupDownloading.visibility = View.VISIBLE
        binding.groupSuccess.visibility = View.GONE
        binding.groupError.visibility = View.GONE
        updateDownloadProgress(percent)
    }

    private fun updateDownloadProgress(percent: Int) {
        binding.downloadProgressBar.progress = percent
        binding.downloadProgressText.text = getString(R.string.receive_downloading, percent)
    }

    private fun showSuccess(fileName: String) {
        binding.groupInput.visibility = View.GONE
        binding.groupDownloading.visibility = View.GONE
        binding.groupSuccess.visibility = View.VISIBLE
        binding.groupError.visibility = View.GONE
        binding.successDescText.text = getString(R.string.receive_success_desc, fileName)
    }

    private fun showError(message: String) {
        binding.groupInput.visibility = View.GONE
        binding.groupDownloading.visibility = View.GONE
        binding.groupSuccess.visibility = View.GONE
        binding.groupError.visibility = View.VISIBLE
        binding.errorText.text = message
    }
}
