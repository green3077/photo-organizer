package com.green3077.photoorganizer

import android.content.Intent
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.green3077.photoorganizer.data.PhotoDeleter
import com.green3077.photoorganizer.data.PhotoRepository
import com.green3077.photoorganizer.data.StreakTracker
import com.green3077.photoorganizer.databinding.ActivityPhotoViewerBinding
import com.green3077.photoorganizer.model.Photo
import com.green3077.photoorganizer.ui.PhotoPagerAdapter
import com.green3077.photoorganizer.ui.PhotoViewerHolder
import kotlinx.coroutines.launch

class PhotoViewerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPhotoViewerBinding
    private val repository by lazy { PhotoRepository(this) }
    private var photos: List<Photo> = emptyList()

    private val deleteLauncher =
        registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
            if (result.resultCode == RESULT_OK) onDeleteConfirmed()
        }
    private val deleter by lazy { PhotoDeleter(this, deleteLauncher) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPhotoViewerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        binding.toolbar.setNavigationOnClickListener { finish() }
        binding.btnDelete.setOnClickListener {
            photos.getOrNull(binding.pager.currentItem)?.let { deleter.requestDelete(listOf(it.uri)) }
        }
        binding.btnShare.setOnClickListener {
            photos.getOrNull(binding.pager.currentItem)?.let { sharePhoto(it) }
        }

        // 삭제 확인 시스템 다이얼로그가 떠 있는 동안 프로세스가 종료되면 static 홀더가
        // 비워진 채로 재생성될 수 있으므로, 그럴 땐 인텐트에 담아둔 사진 ID로 다시 불러온다.
        val cached = PhotoViewerHolder.photos
        if (cached.isNotEmpty()) {
            photos = cached
            showPager(intent.getIntExtra(EXTRA_INDEX, 0))
        } else {
            reloadFromRepository()
        }
    }

    private fun reloadFromRepository() {
        val ids = intent.getLongArrayExtra(EXTRA_PHOTO_IDS)
        if (ids == null || ids.isEmpty()) {
            finish()
            return
        }
        lifecycleScope.launch {
            val byId = repository.loadAllPhotos().associateBy { it.id }
            val restored = ids.toList().mapNotNull { byId[it] }
            if (restored.isEmpty()) {
                finish()
                return@launch
            }
            photos = restored
            PhotoViewerHolder.photos = restored
            showPager(intent.getIntExtra(EXTRA_INDEX, 0))
        }
    }

    private fun sharePhoto(photo: Photo) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/*"
            putExtra(Intent.EXTRA_STREAM, photo.uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, getString(R.string.share_title)))
    }

    private fun showPager(index: Int) {
        binding.pager.adapter = PhotoPagerAdapter(photos)
        binding.pager.setCurrentItem(index.coerceIn(0, photos.size - 1), false)
    }

    private fun onDeleteConfirmed() {
        StreakTracker.recordOrganizedToday(this)
        val current = binding.pager.currentItem
        photos = photos.filterIndexed { index, _ -> index != current }
        if (photos.isEmpty()) {
            finish()
            return
        }
        binding.pager.adapter = PhotoPagerAdapter(photos)
        binding.pager.setCurrentItem(current.coerceAtMost(photos.size - 1), false)
    }

    companion object {
        const val EXTRA_INDEX = "extra_index"
        const val EXTRA_PHOTO_IDS = "extra_photo_ids"
    }
}
