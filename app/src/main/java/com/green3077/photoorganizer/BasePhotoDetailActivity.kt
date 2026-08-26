package com.green3077.photoorganizer

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.green3077.photoorganizer.data.PhotoDeleter
import com.green3077.photoorganizer.data.PhotoMover
import com.green3077.photoorganizer.data.PhotoRepository
import com.green3077.photoorganizer.data.StreakTracker
import com.green3077.photoorganizer.databinding.ActivityDetailBinding
import com.green3077.photoorganizer.model.Photo
import com.green3077.photoorganizer.ui.DetailListAdapter
import com.green3077.photoorganizer.ui.PhotoViewerHolder
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * "사진을 날짜별 섹션으로 묶어 그리드로 보여주고, 길게 눌러 선택한 뒤 삭제/공유/이동한다"는
 * DetailActivity / LocationDetailActivity / YearDetailActivity 공통 골격.
 * 무엇을(어떤 조건으로) 불러올지, 섹션 라벨을 뭐라 붙일지만 하위 클래스가 정한다.
 */
abstract class BasePhotoDetailActivity : AppCompatActivity() {

    protected lateinit var binding: ActivityDetailBinding
    private lateinit var adapter: DetailListAdapter
    protected val repository by lazy { PhotoRepository(this) }
    private var photosByDate: Map<LocalDate, List<Photo>> = emptyMap()
    private val selectedIds = mutableSetOf<Long>()
    private var pendingMoveUris: List<Uri> = emptyList()
    private var pendingMoveFolder: String = ""

    private val deleteLauncher =
        registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                StreakTracker.recordOrganizedToday(this)
                selectedIds.clear()
                loadPhotos()
            }
        }
    private val deleter by lazy { PhotoDeleter(this, deleteLauncher) }

    private val moveLauncher: ActivityResultLauncher<IntentSenderRequest> =
        registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                mover.applyMove(pendingMoveUris, pendingMoveFolder)
                StreakTracker.recordOrganizedToday(this)
                selectedIds.clear()
                loadPhotos()
            }
        }
    private val mover: PhotoMover by lazy { PhotoMover(this, moveLauncher) }

    /** 잘못된 인텐트면 false를 반환한다 — 호출한 쪽에서 바로 finish() 처리한다. */
    protected abstract fun parseExtras(): Boolean
    protected abstract fun screenTitle(): String
    protected abstract suspend fun loadPhotosByDate(): Map<LocalDate, List<Photo>>
    protected abstract fun sectionLabel(date: LocalDate): String

    final override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!parseExtras()) {
            finish()
            return
        }

        binding = ActivityDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        binding.toolbar.title = screenTitle()
        binding.toolbar.setNavigationOnClickListener { finish() }
        binding.toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_delete -> {
                    confirmDelete()
                    true
                }
                R.id.action_share -> {
                    confirmShare()
                    true
                }
                R.id.action_move -> {
                    confirmMove()
                    true
                }
                else -> false
            }
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
            photosByDate = loadPhotosByDate()
            selectedIds.retainAll(photosByDate.values.flatten().map { it.id }.toSet())
            val sections = photosByDate.mapKeys { (date, _) -> sectionLabel(date) }
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
        val hasSelection = selectedIds.isNotEmpty()
        binding.toolbar.title = if (hasSelection) getString(R.string.selected_count, selectedIds.size) else screenTitle()
    }

    private fun selectedUris(): List<Uri> =
        photosByDate.values.flatten().filter { selectedIds.contains(it.id) }.map { it.uri }

    private fun requireSelection(): List<Uri>? {
        val uris = selectedUris()
        if (uris.isEmpty()) {
            Toast.makeText(this, R.string.select_photos_first, Toast.LENGTH_SHORT).show()
            return null
        }
        return uris
    }

    private fun confirmDelete() {
        val uris = requireSelection() ?: return
        deleter.requestDelete(uris)
    }

    private fun confirmShare() {
        val uris = requireSelection() ?: return
        val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "image/*"
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, getString(R.string.share_title)))
    }

    private fun confirmMove() {
        val uris = requireSelection() ?: return
        val input = EditText(this).apply {
            setText(PhotoMover.DEFAULT_FOLDER)
            setSelection(text.length)
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.move_dialog_title))
            .setView(input)
            .setPositiveButton(getString(R.string.move_confirm)) { _, _ ->
                pendingMoveUris = uris
                pendingMoveFolder = input.text.toString()
                mover.requestMove(uris)
            }
            .setNegativeButton(getString(android.R.string.cancel), null)
            .show()
    }
}
