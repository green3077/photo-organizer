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
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.tabs.TabLayout
import com.green3077.photoorganizer.data.LocationRepository
import com.green3077.photoorganizer.data.PhotoRepository
import com.green3077.photoorganizer.data.StreakTracker
import com.green3077.photoorganizer.databinding.ActivityMainBinding
import com.green3077.photoorganizer.model.LocationGroup
import com.green3077.photoorganizer.model.Photo
import com.green3077.photoorganizer.notification.WorkScheduler
import com.green3077.photoorganizer.ui.LocationGroupAdapter
import com.green3077.photoorganizer.ui.MemoryGroupAdapter
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.MonthDay
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

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) onMediaPermissionGranted() else showPermissionGate()
        }

    private val requestLocationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) refreshCurrentTab() else showPermissionGate(forLocation = true)
        }

    private val requestNotificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)

        groupAdapter = MemoryGroupAdapter { group -> openDetail(group.monthDay) }
        locationAdapter = LocationGroupAdapter { group -> openLocationDetail(group) }
        binding.recyclerGroups.layoutManager = LinearLayoutManager(this)
        binding.recyclerGroups.adapter = groupAdapter

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
                R.id.action_settings -> {
                    startActivity(Intent(this, SettingsActivity::class.java))
                    true
                }
                else -> false
            }
        }

        binding.tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                currentTab = if (tab.position == 0) Tab.DATE else Tab.LOCATION
                binding.recyclerGroups.adapter = if (currentTab == Tab.DATE) groupAdapter else locationAdapter
                refreshCurrentTab()
            }

            override fun onTabUnselected(tab: TabLayout.Tab) = Unit
            override fun onTabReselected(tab: TabLayout.Tab) = Unit
        })

        binding.btnGrantPermission.setOnClickListener {
            if (currentTab == Tab.DATE) {
                requestPermissionLauncher.launch(requiredPermission())
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

    private fun requiredPermission(): String =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Manifest.permission.READ_MEDIA_IMAGES
        else Manifest.permission.READ_EXTERNAL_STORAGE

    private fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, requiredPermission()) == PackageManager.PERMISSION_GRANTED

    private fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_MEDIA_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    private fun onMediaPermissionGranted() {
        loadPhotos()
        WorkScheduler.scheduleIfNeeded(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
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
                val groups = repository.buildRecurringMemoryGroups(allPhotos, MonthDay.now())
                groupAdapter.submit(groups)
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
            openDetail(MonthDay.from(date))
        }
        picker.show(supportFragmentManager, "date_picker")
    }

    private fun openDetail(monthDay: MonthDay) {
        startActivity(
            Intent(this, DetailActivity::class.java).apply {
                putExtra(DetailActivity.EXTRA_MONTH, monthDay.monthValue)
                putExtra(DetailActivity.EXTRA_DAY, monthDay.dayOfMonth)
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

    private fun openLocationDetail(group: LocationGroup) {
        startActivity(
            Intent(this, LocationDetailActivity::class.java).apply {
                putExtra(LocationDetailActivity.EXTRA_PHOTO_IDS, group.photos.map { it.id }.toLongArray())
                putExtra(LocationDetailActivity.EXTRA_PLACE_NAME, group.placeName)
            }
        )
    }
}
