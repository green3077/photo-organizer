package com.green3077.photoorganizer

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.green3077.photoorganizer.data.PhotoDetailsLoader
import com.green3077.photoorganizer.data.PhotoMover
import com.green3077.photoorganizer.data.PhotoRepository
import com.green3077.photoorganizer.data.PhotoTrasher
import com.green3077.photoorganizer.data.StreakTracker
import com.green3077.photoorganizer.data.TrashTracker
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

    private var pendingTrashId: Long = -1L
    private val trashLauncher =
        registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
            if (result.resultCode == RESULT_OK) onDeleteConfirmed()
        }
    private val trasher by lazy { PhotoTrasher(this, trashLauncher) }

    private var pendingMoveUri: Uri? = null
    private var pendingMoveFolder: String = ""
    private val moveLauncher =
        registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
            if (result.resultCode == RESULT_OK) onMoveConfirmed()
        }
    private val mover by lazy { PhotoMover(this, moveLauncher) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPhotoViewerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        binding.toolbar.setNavigationOnClickListener { finish() }
        binding.btnDelete.setOnClickListener {
            photos.getOrNull(binding.pager.currentItem)?.let { photo -> confirmDelete(photo) }
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

    private fun confirmDelete(photo: Photo) {
        MaterialAlertDialogBuilder(this)
            .setMessage(getString(R.string.delete_confirm_message))
            .setNegativeButton(getString(android.R.string.cancel), null)
            .setPositiveButton(getString(R.string.delete)) { _, _ ->
                pendingTrashId = photo.id
                trasher.requestTrash(listOf(photo.uri))
            }
            .show()
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
                    R.id.action_move -> {
                        confirmMove(photo)
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

    private fun confirmMove(photo: Photo) {
        lifecycleScope.launch {
            val folders = mover.listExistingFolders()
            showFolderPicker(folders) { relativePath ->
                pendingMoveUri = photo.uri
                pendingMoveFolder = relativePath
                mover.requestMove(listOf(photo.uri))
            }
        }
    }

    private fun showFolderPicker(folders: List<String>, onPicked: (String) -> Unit) {
        val labels = (listOf(getString(R.string.move_new_folder)) + folders.map { it.trimEnd('/') }).toTypedArray()
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.move_dialog_title))
            .setItems(labels) { _, which ->
                if (which == 0) showNewFolderInput(onPicked) else onPicked(folders[which - 1])
            }
            .show()
    }

    private fun showNewFolderInput(onPicked: (String) -> Unit) {
        val input = EditText(this).apply {
            setText(PhotoMover.DEFAULT_FOLDER)
            setSelection(text.length)
            hint = getString(R.string.move_new_folder_hint)
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.move_dialog_title))
            .setView(input)
            .setPositiveButton(getString(R.string.move_confirm)) { _, _ ->
                onPicked(PhotoMover.relativePathFor(input.text.toString()))
            }
            .setNegativeButton(getString(android.R.string.cancel), null)
            .show()
    }

    private fun onMoveConfirmed() {
        val uri = pendingMoveUri ?: return
        mover.applyMove(listOf(uri), pendingMoveFolder)
        StreakTracker.recordOrganizedToday(this)
        val folderLabel = pendingMoveFolder.trimEnd('/').substringAfterLast('/').ifBlank { PhotoMover.DEFAULT_FOLDER }
        Toast.makeText(this, getString(R.string.move_done, 1, folderLabel), Toast.LENGTH_SHORT).show()
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
        if (pendingTrashId != -1L) TrashTracker.recordTrashed(this, listOf(pendingTrashId))
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
