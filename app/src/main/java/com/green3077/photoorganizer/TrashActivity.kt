package com.green3077.photoorganizer

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.green3077.photoorganizer.data.PhotoDeleter
import com.green3077.photoorganizer.data.PhotoRepository
import com.green3077.photoorganizer.data.PhotoTrasher
import com.green3077.photoorganizer.data.TrashTracker
import com.green3077.photoorganizer.databinding.ActivityTrashBinding
import com.green3077.photoorganizer.model.Photo
import com.green3077.photoorganizer.notification.TrashWorkScheduler
import com.green3077.photoorganizer.ui.DetailListAdapter
import com.green3077.photoorganizer.util.DateFormat
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

/**
 * 삭제한 사진이 바로 완전삭제되지 않고 모이는 화면. 항목마다 남은 보관 일수를
 * 섹션으로 묶어 보여주고, 선택한 사진을 복원하거나 완전삭제할 수 있다.
 * [TrashTracker.RETENTION_DAYS]일이 지난 사진은 화면을 열 때마다 자동으로
 * 완전삭제를 시도한다(시스템 확인 절차 특성상 앱을 열어야 실제로 지워진다).
 */
class TrashActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTrashBinding
    private lateinit var adapter: DetailListAdapter
    private val repository by lazy { PhotoRepository(this) }
    private var trashedPhotos: List<Photo> = emptyList()
    private val selectedIds = mutableSetOf<Long>()
    private var pendingDeleteIds: List<Long> = emptyList()
    private var pendingRestoreIds: List<Long> = emptyList()

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                TrashWorkScheduler.scheduleIfNeeded(this)
                loadTrash()
            } else {
                Toast.makeText(this, R.string.trash_permission_denied, Toast.LENGTH_SHORT).show()
                finish()
            }
        }

    private val deleteLauncher =
        registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                TrashTracker.clear(this, pendingDeleteIds)
                selectedIds.removeAll(pendingDeleteIds.toSet())
                loadTrash()
            }
        }
    private val deleter by lazy { PhotoDeleter(this, deleteLauncher) }

    private val restoreLauncher =
        registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                TrashTracker.clear(this, pendingRestoreIds)
                selectedIds.removeAll(pendingRestoreIds.toSet())
                loadTrash()
            }
        }
    private val trasher by lazy { PhotoTrasher(this, restoreLauncher) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTrashBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        binding.toolbar.setNavigationOnClickListener { finish() }
        binding.toolbar.setOnMenuItemClickListener { item ->
            if (item.itemId == R.id.action_empty_trash) {
                confirmEmptyTrash()
                true
            } else {
                false
            }
        }
        binding.btnRestore.setOnClickListener { confirmRestore() }
        binding.btnPermanentDelete.setOnClickListener { confirmPermanentDelete() }

        adapter = DetailListAdapter(
            isSelected = { selectedIds.contains(it) },
            onPhotoClick = ::toggleSelection,
            onToggleSelect = ::toggleSelection
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

    override fun onStart() {
        super.onStart()
        if (hasPermission()) {
            TrashWorkScheduler.scheduleIfNeeded(this)
            loadTrash()
        } else {
            requestPermissionLauncher.launch(requiredPermission())
        }
    }

    private fun requiredPermission(): String =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Manifest.permission.READ_MEDIA_IMAGES
        else Manifest.permission.READ_EXTERNAL_STORAGE

    private fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, requiredPermission()) == PackageManager.PERMISSION_GRANTED

    private fun loadTrash() {
        lifecycleScope.launch {
            val all = repository.loadTrashedPhotos()
            val now = System.currentTimeMillis()
            val withElapsedDays = all.map { photo ->
                val trashedAt = TrashTracker.trashedAtOrNow(this@TrashActivity, photo.id)
                photo to TimeUnit.MILLISECONDS.toDays(now - trashedAt).toInt()
            }
            val expired = withElapsedDays.filter { it.second >= TrashTracker.RETENTION_DAYS }.map { it.first }
            val valid = withElapsedDays.filterNot { it.second >= TrashTracker.RETENTION_DAYS }

            if (expired.isNotEmpty()) {
                Toast.makeText(this@TrashActivity, getString(R.string.trash_auto_purging, expired.size), Toast.LENGTH_LONG)
                    .show()
                pendingDeleteIds = expired.map { it.id }
                deleter.requestDelete(expired.map { it.uri })
            }

            trashedPhotos = valid.map { it.first }
            selectedIds.retainAll(trashedPhotos.map { it.id }.toSet())
            renderList(valid)
        }
    }

    private fun renderList(valid: List<Pair<Photo, Int>>) {
        val sections = valid
            .groupBy { (_, daysElapsed) -> TrashTracker.RETENTION_DAYS - daysElapsed }
            .toSortedMap()
            .entries
            .associate { (daysLeft, pairs) -> DateFormat.trashCountdownLabel(daysLeft) to pairs.map { it.first } }
        adapter.submit(sections)
        binding.toolbar.subtitle =
            if (trashedPhotos.isNotEmpty()) getString(R.string.trash_subtitle, trashedPhotos.size) else null
        binding.emptyState.visibility = if (trashedPhotos.isEmpty()) View.VISIBLE else View.GONE
        binding.recyclerPhotos.visibility = if (trashedPhotos.isEmpty()) View.GONE else View.VISIBLE
    }

    private fun toggleSelection(photo: Photo) {
        if (!selectedIds.remove(photo.id)) selectedIds.add(photo.id)
        adapter.refreshSelectionState()
    }

    private fun requireSelection(): List<Photo>? {
        val selected = trashedPhotos.filter { selectedIds.contains(it.id) }
        if (selected.isEmpty()) {
            Toast.makeText(this, R.string.select_photos_first, Toast.LENGTH_SHORT).show()
            return null
        }
        return selected
    }

    private fun confirmRestore() {
        val selected = requireSelection() ?: return
        pendingRestoreIds = selected.map { it.id }
        trasher.requestRestore(selected.map { it.uri })
    }

    private fun confirmPermanentDelete() {
        val selected = requireSelection() ?: return
        MaterialAlertDialogBuilder(this)
            .setMessage(getString(R.string.confirm_permanent_delete_message, selected.size))
            .setPositiveButton(getString(R.string.permanent_delete)) { _, _ ->
                pendingDeleteIds = selected.map { it.id }
                deleter.requestDelete(selected.map { it.uri })
            }
            .setNegativeButton(getString(android.R.string.cancel), null)
            .show()
    }

    private fun confirmEmptyTrash() {
        if (trashedPhotos.isEmpty()) return
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.confirm_empty_trash_title))
            .setMessage(getString(R.string.confirm_empty_trash_message, trashedPhotos.size))
            .setPositiveButton(getString(R.string.empty_trash_action)) { _, _ ->
                pendingDeleteIds = trashedPhotos.map { it.id }
                deleter.requestDelete(trashedPhotos.map { it.uri })
            }
            .setNegativeButton(getString(android.R.string.cancel), null)
            .show()
    }
}
