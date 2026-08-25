package com.green3077.photoorganizer

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import com.green3077.photoorganizer.data.PhotoDeleter
import com.green3077.photoorganizer.data.PhotoRepository
import com.green3077.photoorganizer.data.StreakTracker
import com.green3077.photoorganizer.databinding.ActivityDetailBinding
import com.green3077.photoorganizer.model.Photo
import com.green3077.photoorganizer.ui.DetailListAdapter
import com.green3077.photoorganizer.ui.PhotoViewerHolder
import com.green3077.photoorganizer.util.DateFormat
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * 한 장소 그룹에 속한 사진들을 날짜별로 묶어 보여준다. DetailActivity와 레이아웃/메뉴를 공유하고,
 * 장소 그룹은 (클러스터링 결과가 아니라) 사진 ID 목록으로 전달받아 매번 Repository에서 다시 걸러낸다 —
 * static 홀더에 의존하지 않아 프로세스가 재생성돼도 안전하다.
 */
class LocationDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDetailBinding
    private lateinit var adapter: DetailListAdapter
    private lateinit var photoIds: LongArray
    private var placeName: String = ""
    private val repository by lazy { PhotoRepository(this) }
    private var photosByDate: Map<LocalDate, List<Photo>> = emptyMap()
    private val selectedIds = mutableSetOf<Long>()

    private val deleteLauncher =
        registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                StreakTracker.recordOrganizedToday(this)
                selectedIds.clear()
                loadPhotos()
            }
        }
    private val deleter by lazy { PhotoDeleter(this, deleteLauncher) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val ids = intent.getLongArrayExtra(EXTRA_PHOTO_IDS)
        if (ids == null || ids.isEmpty()) {
            finish()
            return
        }
        photoIds = ids
        placeName = intent.getStringExtra(EXTRA_PLACE_NAME) ?: getString(R.string.title_location_detail)

        binding = ActivityDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        binding.toolbar.title = placeName
        binding.toolbar.setNavigationOnClickListener { finish() }
        binding.toolbar.setOnMenuItemClickListener { item ->
            if (item.itemId == R.id.action_delete) {
                confirmDelete()
                true
            } else false
        }

        adapter = DetailListAdapter(
            isSelected = { selectedIds.contains(it) },
            onPhotoClick = ::onPhotoClick,
            onPhotoLongClick = ::toggleSelection
        )

        val spanCount = 3
        val layoutManager = GridLayoutManager(this, spanCount)
        layoutManager.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
            override fun getSpanSize(position: Int): Int =
                if (adapter.isHeaderAt(position)) spanCount else 1
        }
        binding.recyclerPhotos.layoutManager = layoutManager
        binding.recyclerPhotos.adapter = adapter
    }

    override fun onResume() {
        super.onResume()
        loadPhotos()
    }

    private fun loadPhotos() {
        lifecycleScope.launch {
            val idSet = photoIds.toSet()
            val allPhotos = repository.loadAllPhotos()
            photosByDate = allPhotos.filter { it.id in idSet }
                .groupBy { it.dateTaken }
                .toSortedMap(compareByDescending { it })
            selectedIds.retainAll(photosByDate.values.flatten().map { it.id }.toSet())
            val sections = photosByDate.mapKeys { (date, _) -> DateFormat.fullDateLabel(date) }
            adapter.submit(sections)
            binding.emptyState.visibility = if (photosByDate.isEmpty()) View.VISIBLE else View.GONE
            binding.recyclerPhotos.visibility = if (photosByDate.isEmpty()) View.GONE else View.VISIBLE
            updateSelectionUi()
        }
    }

    private fun onPhotoClick(photo: Photo) {
        if (selectedIds.isNotEmpty()) {
            toggleSelection(photo)
            return
        }
        val datePhotos = photosByDate[photo.dateTaken].orEmpty()
        PhotoViewerHolder.photos = datePhotos
        val index = datePhotos.indexOfFirst { it.id == photo.id }.coerceAtLeast(0)
        startActivity(
            Intent(this, PhotoViewerActivity::class.java).apply {
                putExtra(PhotoViewerActivity.EXTRA_INDEX, index)
                putExtra(PhotoViewerActivity.EXTRA_PHOTO_IDS, datePhotos.map { it.id }.toLongArray())
            }
        )
    }

    private fun toggleSelection(photo: Photo) {
        if (!selectedIds.remove(photo.id)) selectedIds.add(photo.id)
        adapter.notifyDataSetChanged()
        updateSelectionUi()
    }

    private fun updateSelectionUi() {
        binding.toolbar.menu.findItem(R.id.action_delete)?.isVisible = selectedIds.isNotEmpty()
        binding.toolbar.title = if (selectedIds.isNotEmpty()) {
            getString(R.string.selected_count, selectedIds.size)
        } else {
            placeName
        }
    }

    private fun confirmDelete() {
        val urisToDelete = photosByDate.values.flatten()
            .filter { selectedIds.contains(it.id) }
            .map { it.uri }
        deleter.requestDelete(urisToDelete)
    }

    companion object {
        const val EXTRA_PHOTO_IDS = "extra_photo_ids"
        const val EXTRA_PLACE_NAME = "extra_place_name"
    }
}
