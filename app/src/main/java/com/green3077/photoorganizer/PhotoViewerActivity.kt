package com.green3077.photoorganizer

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
import java.time.MonthDay

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
        binding.toolbar.setOnMenuItemClickListener { item ->
            if (item.itemId == R.id.action_delete) {
                photos.getOrNull(binding.pager.currentItem)?.let { deleter.requestDelete(listOf(it.uri)) }
                true
            } else false
        }

        // 프로세스가 종료됐다 재생성된 경우 PhotoViewerHolder가 비어있을 수 있으므로,
        // 그럴 땐 인텐트에 담아둔 월/일/연도로 Repository에서 목록을 다시 불러온다.
        val cached = PhotoViewerHolder.photos
        if (cached.isNotEmpty()) {
            photos = cached
            showPager(intent.getIntExtra(EXTRA_INDEX, 0))
        } else {
            reloadFromRepository()
        }
    }

    private fun reloadFromRepository() {
        val month = intent.getIntExtra(EXTRA_MONTH, -1)
        val day = intent.getIntExtra(EXTRA_DAY, -1)
        val year = intent.getIntExtra(EXTRA_YEAR, -1)
        if (month == -1 || day == -1 || year == -1) {
            finish()
            return
        }
        lifecycleScope.launch {
            val monthDay = MonthDay.of(month, day)
            val allPhotos = repository.loadAllPhotos()
            val yearPhotos = repository.photosForMonthDay(allPhotos, monthDay)[year].orEmpty()
            if (yearPhotos.isEmpty()) {
                finish()
                return@launch
            }
            photos = yearPhotos
            PhotoViewerHolder.photos = yearPhotos
            showPager(intent.getIntExtra(EXTRA_INDEX, 0))
        }
    }

    private fun showPager(index: Int) {
        binding.pager.adapter = PhotoPagerAdapter(photos)
        binding.pager.setCurrentItem(index, false)
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
        const val EXTRA_MONTH = "extra_month"
        const val EXTRA_DAY = "extra_day"
        const val EXTRA_YEAR = "extra_year"
    }
}
