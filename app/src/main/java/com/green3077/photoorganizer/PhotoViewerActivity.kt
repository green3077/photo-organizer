package com.green3077.photoorganizer

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
import androidx.lifecycle.lifecycleScope
import com.green3077.photoorganizer.data.PhotoDeleter
import com.green3077.photoorganizer.data.PhotoDetailsLoader
import com.green3077.photoorganizer.data.PhotoRepository
import com.green3077.photoorganizer.data.StreakTracker
import com.green3077.photoorganizer.databinding.ActivityPhotoViewerBinding
import com.green3077.photoorganizer.model.Photo
import com.green3077.photoorganizer.ui.PhotoDetailsSheet
import com.green3077.photoorganizer.ui.PhotoPagerAdapter
import com.green3077.photoorganizer.ui.PhotoViewerHolder
import kotlinx.coroutines.launch

class PhotoViewerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPhotoViewerBinding
    private val repository by lazy { PhotoRepository(this) }
    private val detailsLoader by lazy { PhotoDetailsLoader(this) }
    private var photos: List<Photo> = emptyList()
    private var chromeVisible = true

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
        binding.btnMore.setOnClickListener { view ->
            photos.getOrNull(binding.pager.currentItem)?.let { showMoreMenu(view, it) }
        }

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

    private fun showMoreMenu(anchor: View, photo: Photo) {
        PopupMenu(this, anchor).apply {
            menuInflater.inflate(R.menu.menu_photo_viewer_more, menu)
            setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    R.id.action_detail_info -> {
                        showDetailInfo(photo)
                        true
                    }
                    else -> false
                }
            }
        }.show()
    }

    private fun showDetailInfo(photo: Photo) {
        lifecycleScope.launch {
            val details = detailsLoader.load(photo)
            PhotoDetailsSheet.show(this@PhotoViewerActivity, photo, details)
        }
    }

    /** 사진을 한 번 탭하면 툴바·하단 버튼을 숨기거나 다시 보여준다(다른 갤러리 앱과 동일한 몰입 보기). */
    private fun toggleChrome() {
        chromeVisible = !chromeVisible
        val targetAlpha = if (chromeVisible) 1f else 0f
        listOf(binding.toolbar, binding.bottomActionBar).forEach { view ->
            view.animate().alpha(targetAlpha).setDuration(200).withStartAction {
                if (chromeVisible) view.visibility = View.VISIBLE
            }.withEndAction {
                if (!chromeVisible) view.visibility = View.INVISIBLE
            }.start()
        }
    }

    private fun showPager(index: Int) {
        binding.pager.adapter = PhotoPagerAdapter(photos, onSingleTap = ::toggleChrome)
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
        binding.pager.adapter = PhotoPagerAdapter(photos, onSingleTap = ::toggleChrome)
        binding.pager.setCurrentItem(current.coerceAtMost(photos.size - 1), false)
    }

    companion object {
        const val EXTRA_INDEX = "extra_index"
        const val EXTRA_PHOTO_IDS = "extra_photo_ids"
    }
}
