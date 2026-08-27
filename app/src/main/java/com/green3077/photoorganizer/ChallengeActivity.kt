package com.green3077.photoorganizer

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.green3077.photoorganizer.data.ChallengeSettings
import com.green3077.photoorganizer.data.PhotoMover
import com.green3077.photoorganizer.data.PhotoRepository
import com.green3077.photoorganizer.data.PhotoTrasher
import com.green3077.photoorganizer.data.StreakTracker
import com.green3077.photoorganizer.data.TrashTracker
import com.green3077.photoorganizer.databinding.ActivityChallengeBinding
import com.green3077.photoorganizer.model.Photo
import com.green3077.photoorganizer.notification.TrashWorkScheduler
import com.green3077.photoorganizer.notification.WorkScheduler
import com.green3077.photoorganizer.ui.DetailListAdapter
import com.green3077.photoorganizer.ui.PhotoViewerHolder
import com.green3077.photoorganizer.util.DateFormat
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth

/**
 * 연도+월을 한 번 고르면 그 달 1일부터 하루씩 "오늘의 챌린지"로 삼아 순서대로 정리한다.
 * 진행 커서(ChallengeSettings.currentChallengeDate)는 완료 버튼을 눌러야만 앞으로
 * 나아가므로, 며칠을 건너뛰어도 항상 멈춰있던 그 날짜부터 다시 보여준다.
 */
class ChallengeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityChallengeBinding
    private lateinit var adapter: DetailListAdapter
    private val repository by lazy { PhotoRepository(this) }
    private var currentDatePhotos: List<Photo> = emptyList()
    private val selectedIds = mutableSetOf<Long>()
    private var pendingMoveUris: List<Uri> = emptyList()
    private var pendingMoveFolder: String = ""
    private var pendingTrashIds: List<Long> = emptyList()
    private var pickedYear: Int? = null
    private var pickedMonth: Int? = null

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) onPermissionGranted() else showPermissionGate()
        }

    private val trashLauncher =
        registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                TrashTracker.recordTrashed(this, pendingTrashIds)
                StreakTracker.recordOrganizedToday(this)
                selectedIds.clear()
                loadCurrentDatePhotos()
            }
        }
    private val trasher by lazy { PhotoTrasher(this, trashLauncher) }

    private val moveLauncher: ActivityResultLauncher<IntentSenderRequest> =
        registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                mover.applyMove(pendingMoveUris, pendingMoveFolder)
                StreakTracker.recordOrganizedToday(this)
                selectedIds.clear()
                loadCurrentDatePhotos()
            }
        }
    private val mover: PhotoMover by lazy { PhotoMover(this, moveLauncher) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityChallengeBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        binding.toolbar.setNavigationOnClickListener { finish() }

        binding.btnGrantPermission.setOnClickListener { requestPermissionLauncher.launch(requiredPermission()) }
        binding.btnPickYear.setOnClickListener { showYearPicker() }
        binding.btnPickMonth.setOnClickListener { showMonthPicker() }
        binding.btnStartChallenge.setOnClickListener { startChallenge() }
        binding.btnPickNewMonth.setOnClickListener {
            ChallengeSettings.clearChallengeTarget(this)
            refreshState()
        }
        binding.btnCompleteMission.setOnClickListener { completeMission() }
        binding.btnDelete.setOnClickListener { confirmDelete() }
        binding.btnShare.setOnClickListener { confirmShare() }
        binding.btnMove.setOnClickListener { confirmMove() }

        adapter = DetailListAdapter(
            isSelected = { selectedIds.contains(it) },
            onPhotoClick = ::onPhotoClick,
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
        if (hasPermission()) onPermissionGranted() else showPermissionGate()
    }

    private fun requiredPermission(): String =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Manifest.permission.READ_MEDIA_IMAGES
        else Manifest.permission.READ_EXTERNAL_STORAGE

    private fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, requiredPermission()) == PackageManager.PERMISSION_GRANTED

    private fun onPermissionGranted() {
        WorkScheduler.scheduleIfNeeded(this)
        TrashWorkScheduler.scheduleIfNeeded(this)
        hideAllSections()
        refreshState()
    }

    private fun showPermissionGate() {
        hideAllSections()
        binding.permissionGate.visibility = View.VISIBLE
    }

    private fun hideAllSections() {
        binding.permissionGate.visibility = View.GONE
        binding.pickerSection.visibility = View.GONE
        binding.doneSection.visibility = View.GONE
        binding.challengeSection.visibility = View.GONE
    }

    private fun refreshState() {
        if (!hasPermission()) {
            showPermissionGate()
            return
        }
        if (!ChallengeSettings.isConfigured(this)) {
            showPickerState()
            return
        }
        val date = ChallengeSettings.currentChallengeDate(this)
        if (date == null) {
            hideAllSections()
            binding.doneSection.visibility = View.VISIBLE
        } else {
            hideAllSections()
            binding.challengeSection.visibility = View.VISIBLE
            showActiveState(date)
        }
    }

    private fun showPickerState() {
        hideAllSections()
        binding.pickerSection.visibility = View.VISIBLE
        pickedYear = null
        pickedMonth = null
        binding.btnPickYear.text = getString(R.string.select_year)
        binding.btnPickMonth.text = getString(R.string.select_month)
        binding.btnPickMonth.isEnabled = false
        binding.btnStartChallenge.isEnabled = false
    }

    private fun showYearPicker() {
        lifecycleScope.launch {
            val years = repository.loadAllPhotos().map { it.dateTaken.year }.distinct().sortedDescending()
            if (years.isEmpty()) {
                Toast.makeText(this@ChallengeActivity, R.string.no_photos_for_challenge, Toast.LENGTH_SHORT).show()
                return@launch
            }
            MaterialAlertDialogBuilder(this@ChallengeActivity)
                .setTitle(getString(R.string.select_year))
                .setItems(years.map { getString(R.string.title_year_detail, it) }.toTypedArray()) { _, index ->
                    pickedYear = years[index]
                    binding.btnPickYear.text = getString(R.string.title_year_detail, pickedYear)
                    binding.btnPickMonth.isEnabled = true
                    updateStartButtonState()
                }
                .show()
        }
    }

    private fun showMonthPicker() {
        val months = (1..12).map { getString(R.string.month_label, it) }.toTypedArray()
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.select_month))
            .setItems(months) { _, index ->
                pickedMonth = index + 1
                binding.btnPickMonth.text = getString(R.string.month_label, pickedMonth)
                updateStartButtonState()
            }
            .show()
    }

    private fun updateStartButtonState() {
        binding.btnStartChallenge.isEnabled = pickedYear != null && pickedMonth != null
    }

    private fun startChallenge() {
        val year = pickedYear ?: return
        val month = pickedMonth ?: return
        ChallengeSettings.setChallengeTarget(this, year, month)
        refreshState()
    }

    private fun showActiveState(date: LocalDate) {
        binding.textChallengeDate.text = DateFormat.fullDateLabel(date)
        val lastDay = YearMonth.of(date.year, date.monthValue).lengthOfMonth()
        loadCurrentDatePhotos(date, lastDay)
    }

    private fun loadCurrentDatePhotos(
        date: LocalDate? = ChallengeSettings.currentChallengeDate(this),
        lastDayOfMonth: Int? = date?.let { YearMonth.of(it.year, it.monthValue).lengthOfMonth() }
    ) {
        if (date == null || lastDayOfMonth == null) {
            refreshState()
            return
        }
        lifecycleScope.launch {
            val allPhotos = repository.loadAllPhotos()
            currentDatePhotos = repository.photosForExactDate(allPhotos, date)
            selectedIds.retainAll(currentDatePhotos.map { it.id }.toSet())
            binding.textChallengeSubtitle.text =
                getString(R.string.challenge_progress, date.dayOfMonth, lastDayOfMonth, currentDatePhotos.size)
            adapter.submit(mapOf(DateFormat.fullDateLabel(date) to currentDatePhotos))
            binding.emptyState.visibility = if (currentDatePhotos.isEmpty()) View.VISIBLE else View.GONE
            binding.recyclerPhotos.visibility = if (currentDatePhotos.isEmpty()) View.GONE else View.VISIBLE
        }
    }

    private fun completeMission() {
        StreakTracker.recordOrganizedToday(this)
        ChallengeSettings.advanceDay(this)
        selectedIds.clear()
        refreshState()
    }

    private fun onPhotoClick(photo: Photo) {
        PhotoViewerHolder.photos = currentDatePhotos
        val index = currentDatePhotos.indexOfFirst { it.id == photo.id }.coerceAtLeast(0)
        startActivity(
            Intent(this, PhotoViewerActivity::class.java).apply {
                putExtra(PhotoViewerActivity.EXTRA_INDEX, index)
                putExtra(PhotoViewerActivity.EXTRA_PHOTO_IDS, currentDatePhotos.map { it.id }.toLongArray())
            }
        )
    }

    private fun toggleSelection(photo: Photo) {
        if (!selectedIds.remove(photo.id)) selectedIds.add(photo.id)
        adapter.refreshSelectionState()
    }

    private fun selectedUris(): List<Uri> =
        currentDatePhotos.filter { selectedIds.contains(it.id) }.map { it.uri }

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
        pendingTrashIds = currentDatePhotos.filter { selectedIds.contains(it.id) }.map { it.id }
        trasher.requestTrash(uris)
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
