package com.green3077.photoorganizer

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.EditText
import androidx.activity.addCallback
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
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
 * "사진을 날짜별 섹션으로 묶어 그리드로 보여주고, 선택한 뒤 삭제/공유/이동한다"는
 * DetailActivity / LocationDetailActivity / YearDetailActivity 공통 골격.
 * 무엇을(어떤 조건으로) 불러올지, 섹션 라벨을 뭐라 붙일지만 하위 클래스가 정한다.
 *
 * 툴바의 "선택" 버튼(항상 보임)을 누르거나 사진을 길게 누르면 선택 모드로 들어가고,
 * 그때부터는 사진을 탭할 때마다 체크박스가 토글된다 — 길게 누르는 제스처를 몰라도
 * 바로 발견할 수 있게 하기 위함.
 */
abstract class BasePhotoDetailActivity : AppCompatActivity() {

    protected lateinit var binding: ActivityDetailBinding
    private lateinit var adapter: DetailListAdapter
    protected val repository by lazy { PhotoRepository(this) }
    private var photosByDate: Map<LocalDate, List<Photo>> = emptyMap()
    private val selectedIds = mutableSetOf<Long>()
    private var selectionMode = false
    private var pendingMoveUris: List<Uri> = emptyList()
    private var pendingMoveFolder: String = ""

    private val deleteLauncher =
        registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                StreakTracker.recordOrganizedToday(this)
                exitSelectionMode()
                loadPhotos()
            }
        }
    private val deleter by lazy { PhotoDeleter(this, deleteLauncher) }

    private val moveLauncher: ActivityResultLauncher<IntentSenderRequest> =
        registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                mover.applyMove(pendingMoveUris, pendingMoveFolder)
                StreakTracker.recordOrganizedToday(this)
                exitSelectionMode()
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
        binding.toolbar.setNavigationOnClickListener {
            if (selectionMode) exitSelectionMode() else finish()
        }
        binding.toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_select -> {
                    enterSelectionMode()
                    true
                }
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
        onBackPressedDispatcher.addCallback(this) {
            if (selectionMode) exitSelectionMode() else finish()
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
        if (selectionMode) {
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
        if (!selectionMode) {
            selectionMode = true
            adapter.setSelectionMode(true)
            binding.toolbar.navigationIcon = ContextCompat.getDrawable(this, R.drawable.ic_close)
        }
        if (!selectedIds.remove(photo.id)) selectedIds.add(photo.id)
        adapter.notifyDataSetChanged()
        updateSelectionUi()
    }

    private fun enterSelectionMode() {
        if (selectionMode) return
        selectionMode = true
        adapter.setSelectionMode(true)
        binding.toolbar.navigationIcon = ContextCompat.getDrawable(this, R.drawable.ic_close)
        updateSelectionUi()
    }

    private fun exitSelectionMode() {
        selectionMode = false
        selectedIds.clear()
        adapter.setSelectionMode(false)
        binding.toolbar.navigationIcon = ContextCompat.getDrawable(this, R.drawable.ic_back)
        updateSelectionUi()
    }

    private fun updateSelectionUi() {
        val hasSelection = selectedIds.isNotEmpty()
        binding.toolbar.menu.findItem(R.id.action_select)?.isVisible = !selectionMode
        binding.toolbar.menu.findItem(R.id.action_delete)?.isVisible = selectionMode && hasSelection
        binding.toolbar.menu.findItem(R.id.action_share)?.isVisible = selectionMode && hasSelection
        binding.toolbar.menu.findItem(R.id.action_move)?.isVisible = selectionMode && hasSelection
        binding.toolbar.title = when {
            hasSelection -> getString(R.string.selected_count, selectedIds.size)
            selectionMode -> getString(R.string.select_hint)
            else -> screenTitle()
        }
    }

    private fun selectedUris(): List<Uri> =
        photosByDate.values.flatten().filter { selectedIds.contains(it.id) }.map { it.uri }

    private fun confirmDelete() {
        deleter.requestDelete(selectedUris())
    }

    private fun confirmShare() {
        val uris = selectedUris()
        if (uris.isEmpty()) return
        val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "image/*"
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, getString(R.string.share_title)))
    }

    private fun confirmMove() {
        val uris = selectedUris()
        if (uris.isEmpty()) return
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
