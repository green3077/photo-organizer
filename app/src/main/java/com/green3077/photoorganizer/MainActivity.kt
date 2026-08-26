package com.green3077.photoorganizer

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.tabs.TabLayout
import com.green3077.photoorganizer.data.LocationRepository
import com.green3077.photoorganizer.data.PhotoRepository
import com.green3077.photoorganizer.data.StreakTracker
import com.green3077.photoorganizer.databinding.ActivityMainBinding
import com.green3077.photoorganizer.model.LocationGroup
import com.green3077.photoorganizer.model.Photo
import com.green3077.photoorganizer.ui.GroupViewMode
import com.green3077.photoorganizer.ui.LocationGroupAdapter
import com.green3077.photoorganizer.ui.MemoryGroupAdapter
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneOffset

class MainActivity : AppCompatActivity() {

    private enum class Tab { DATE, LOCATION }

    private lateinit var binding: ActivityMainBinding
    private lateinit var groupAdapter: MemoryGroupAdapter
    private lateinit var locationAdapter: LocationGroupAdapter
    private val repository by lazy { PhotoRepository(this) }
    private val locationRepository by lazy { LocationRepository(this) }
    private var allPhotos: List<Photo> = emptyList()
    private var locationGroups: List<LocationGroup>? = null
    private var currentTab: Tab = Tab.DATE
    private var groupViewMode: GroupViewMode = GroupViewMode.LIST

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            if (hasPermission()) onMediaPermissionGranted() else showPermissionGate()
        }

    private val requestLocationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) refreshCurrentTab() else showPermissionGate(forLocation = true)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)

        groupViewMode = loadGroupViewMode()
        groupAdapter = MemoryGroupAdapter(groupViewMode) { group -> openDetail(group.day) }
        locationAdapter = LocationGroupAdapter { group -> openLocationDetail(group) }
        binding.recyclerGroups.layoutManager = dateLayoutManager(groupViewMode)
        binding.recyclerGroups.adapter = groupAdapter
        binding.fastScrollbar.attachTo(binding.recyclerGroups)

        binding.toolbar.setNavigationOnClickListener { finish() }
        binding.toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_pick_date -> {
                    showDatePicker()
                    true
                }
                R.id.action_pick_year -> {
                    showYearPicker()
                    true
                }
                R.id.action_toggle_view -> {
                    toggleGroupViewMode()
                    true
                }
                else -> false
            }
        }
        updateToggleMenuItem()

        binding.tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                currentTab = if (tab.position == 0) Tab.DATE else Tab.LOCATION
                binding.recyclerGroups.layoutManager =
                    if (currentTab == Tab.DATE) dateLayoutManager(groupViewMode) else LinearLayoutManager(this@MainActivity)
                binding.recyclerGroups.adapter = if (currentTab == Tab.DATE) groupAdapter else locationAdapter
                binding.fastScrollbar.refresh()
                updateToggleMenuItem()
                refreshCurrentTab()
            }

            override fun onTabUnselected(tab: TabLayout.Tab) = Unit
            override fun onTabReselected(tab: TabLayout.Tab) = Unit
        })

        binding.btnGrantPermission.setOnClickListener {
            if (currentTab == Tab.DATE) {
                requestPermissionLauncher.launch(requiredPermissions())
            } else {
                requestLocationPermissionLauncher.launch(Manifest.permission.ACCESS_MEDIA_LOCATION)
            }
        }
        binding.swipeRefresh.setOnRefreshListener { loadPhotos() }
    }

    override fun onStart() {
        super.onStart()
        if (hasPermission()) onMediaPermissionGranted() else showPermissionGate()
    }

    private fun requiredPermissions(): Array<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)
        else arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)

    /** 동영상 권한(READ_MEDIA_VIDEO)은 있으면 좋은 부가 권한이라, 핵심 권한(사진) 기준으로만 게이트를 연다. */
    private fun corePermission(): String =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Manifest.permission.READ_MEDIA_IMAGES
        else Manifest.permission.READ_EXTERNAL_STORAGE

    private fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, corePermission()) == PackageManager.PERMISSION_GRANTED

    private fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_MEDIA_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    private fun onMediaPermissionGranted() {
        loadPhotos()
    }

    private fun loadPhotos() {
        showLoading()
        lifecycleScope.launch {
            allPhotos = repository.loadAllPhotos()
            locationGroups = null
            binding.swipeRefresh.isRefreshing = false
            val streak = StreakTracker.currentStreak(this@MainActivity)
            binding.toolbar.subtitle = if (streak >= 1) getString(R.string.streak_banner, streak) else null
            refreshCurrentTab()
        }
    }

    private fun refreshCurrentTab() {
        when (currentTab) {
            Tab.DATE -> {
                if (!hasPermission()) {
                    showPermissionGate()
                    return
                }
                val groups = repository.buildRecurringMemoryGroups(allPhotos)
                groupAdapter.submit(groups)
                binding.fastScrollbar.refresh()
                showContent(groups.isNotEmpty(), R.string.empty_recurring_title, R.string.empty_recurring_subtitle)
            }
            Tab.LOCATION -> {
                if (!hasLocationPermission()) {
                    showPermissionGate(forLocation = true)
                    return
                }
                val cached = locationGroups
                if (cached != null) {
                    locationAdapter.submit(cached)
                    binding.fastScrollbar.refresh()
                    showContent(cached.isNotEmpty(), R.string.empty_location_title, R.string.empty_location_subtitle)
                } else {
                    loadLocationGroups()
                }
            }
        }
    }

    private fun loadLocationGroups() {
        showLoading()
        lifecycleScope.launch {
            val groups = locationRepository.loadLocationGroups(allPhotos)
            locationGroups = groups
            if (currentTab == Tab.LOCATION) {
                locationAdapter.submit(groups)
                binding.fastScrollbar.refresh()
                showContent(groups.isNotEmpty(), R.string.empty_location_title, R.string.empty_location_subtitle)
            }
        }
    }

    private fun showLoading() {
        binding.progress.visibility = View.VISIBLE
        binding.permissionGate.visibility = View.GONE
        binding.emptyState.visibility = View.GONE
        binding.recyclerGroups.visibility = View.GONE
    }

    private fun showContent(hasGroups: Boolean, emptyTitleRes: Int, emptySubtitleRes: Int) {
        binding.progress.visibility = View.GONE
        binding.permissionGate.visibility = View.GONE
        binding.recyclerGroups.visibility = if (hasGroups) View.VISIBLE else View.GONE
        binding.emptyState.visibility = if (hasGroups) View.GONE else View.VISIBLE
        if (!hasGroups) {
            binding.emptyTitle.text = getString(emptyTitleRes)
            binding.emptySubtitle.text = getString(emptySubtitleRes)
        }
    }

    private fun showPermissionGate(forLocation: Boolean = false) {
        binding.progress.visibility = View.GONE
        binding.emptyState.visibility = View.GONE
        binding.recyclerGroups.visibility = View.GONE
        binding.permissionGate.visibility = View.VISIBLE
        binding.swipeRefresh.isRefreshing = false
        binding.permissionText.text = getString(
            if (forLocation) R.string.location_permission_rationale else R.string.permission_rationale
        )
    }

    private fun showDatePicker() {
        val picker = MaterialDatePicker.Builder.datePicker()
            .setTitleText(getString(R.string.pick_date))
            .build()
        picker.addOnPositiveButtonClickListener { selection ->
            val date = Instant.ofEpochMilli(selection).atZone(ZoneOffset.UTC).toLocalDate()
            openDetail(date.dayOfMonth)
        }
        picker.show(supportFragmentManager, "date_picker")
    }

    private fun openDetail(day: Int) {
        startActivity(
            Intent(this, DetailActivity::class.java).apply {
                putExtra(DetailActivity.EXTRA_DAY, day)
            }
        )
    }

    private fun showYearPicker() {
        val years = allPhotos.map { it.dateTaken.year }.distinct().sortedDescending()
        if (years.isEmpty()) return
        val items = years.map { getString(R.string.title_year_detail, it) }.toTypedArray()
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.pick_year))
            .setItems(items) { _, index -> openYearDetail(years[index]) }
            .show()
    }

    private fun openYearDetail(year: Int) {
        startActivity(
            Intent(this, YearDetailActivity::class.java).apply {
                putExtra(YearDetailActivity.EXTRA_YEAR, year)
            }
        )
    }

    private fun dateLayoutManager(mode: GroupViewMode): RecyclerView.LayoutManager =
        if (mode == GroupViewMode.GRID) GridLayoutManager(this, GRID_SPAN_COUNT) else LinearLayoutManager(this)

    private fun toggleGroupViewMode() {
        groupViewMode = if (groupViewMode == GroupViewMode.GRID) GroupViewMode.LIST else GroupViewMode.GRID
        saveGroupViewMode(groupViewMode)
        groupAdapter.setViewMode(groupViewMode)
        if (currentTab == Tab.DATE) binding.recyclerGroups.layoutManager = dateLayoutManager(groupViewMode)
        binding.fastScrollbar.refresh()
        updateToggleMenuItem()
    }

    private fun updateToggleMenuItem() {
        val item = binding.toolbar.menu.findItem(R.id.action_toggle_view) ?: return
        item.isVisible = currentTab == Tab.DATE
        if (groupViewMode == GroupViewMode.GRID) {
            item.setIcon(R.drawable.ic_view_list)
            item.setTitle(R.string.toggle_list_view)
        } else {
            item.setIcon(R.drawable.ic_grid_view)
            item.setTitle(R.string.toggle_grid_view)
        }
    }

    private fun loadGroupViewMode(): GroupViewMode {
        val saved = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getString(KEY_VIEW_MODE, null)
        return if (saved == GroupViewMode.GRID.name) GroupViewMode.GRID else GroupViewMode.LIST
    }

    private fun saveGroupViewMode(mode: GroupViewMode) {
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit().putString(KEY_VIEW_MODE, mode.name).apply()
    }

    private fun openLocationDetail(group: LocationGroup) {
        startActivity(
            Intent(this, LocationDetailActivity::class.java).apply {
                putExtra(LocationDetailActivity.EXTRA_PHOTO_IDS, group.photos.map { it.id }.toLongArray())
                putExtra(LocationDetailActivity.EXTRA_PLACE_NAME, group.placeName)
            }
        )
    }

    companion object {
        private const val GRID_SPAN_COUNT = 3
        private const val PREFS_NAME = "main_view_prefs"
        private const val KEY_VIEW_MODE = "date_group_view_mode"
    }
}
