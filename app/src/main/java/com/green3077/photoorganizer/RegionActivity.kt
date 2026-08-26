package com.green3077.photoorganizer

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.green3077.photoorganizer.data.LocationRepository
import com.green3077.photoorganizer.data.PhotoRepository
import com.green3077.photoorganizer.databinding.ActivityRegionBinding
import com.green3077.photoorganizer.model.LocationGroup
import com.green3077.photoorganizer.model.Photo
import com.green3077.photoorganizer.ui.LocationGroupAdapter
import kotlinx.coroutines.launch

/**
 * "지역별 정리" — 국내에서 집(가장 자주 찍히는 위치)을 벗어나 GPS와 함께 찍힌 사진을
 * 다녀온 장소·날짜 단위(구글 포토의 "여행"과 비슷)로 모아 보여준다.
 */
class RegionActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRegionBinding
    private lateinit var adapter: LocationGroupAdapter
    private val repository by lazy { PhotoRepository(this) }
    private val locationRepository by lazy { LocationRepository(this) }

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) loadPhotos() else showPermissionGate()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRegionBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        binding.toolbar.setNavigationOnClickListener { finish() }

        adapter = LocationGroupAdapter { group -> openTripDetail(group) }
        binding.recyclerGroups.layoutManager = LinearLayoutManager(this)
        binding.recyclerGroups.adapter = adapter
        binding.fastScrollbar.attachTo(binding.recyclerGroups)
        binding.fastScrollbar.labelProvider = adapter::labelAt

        binding.btnGrantPermission.setOnClickListener {
            requestPermissionLauncher.launch(Manifest.permission.ACCESS_MEDIA_LOCATION)
        }
        binding.swipeRefresh.setOnRefreshListener { loadPhotos() }
    }

    override fun onStart() {
        super.onStart()
        if (hasPermission()) loadPhotos() else showPermissionGate()
    }

    private fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_MEDIA_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    private fun loadPhotos() {
        showLoading()
        lifecycleScope.launch {
            val allPhotos = repository.loadAllPhotos()
            val groups = locationRepository.loadDomesticTripGroups(allPhotos)
            adapter.submit(groups)
            binding.fastScrollbar.refresh()
            binding.swipeRefresh.isRefreshing = false
            showContent(groups.isNotEmpty())
        }
    }

    private fun showLoading() {
        binding.progress.visibility = View.VISIBLE
        binding.permissionGate.visibility = View.GONE
        binding.emptyState.visibility = View.GONE
        binding.recyclerGroups.visibility = View.GONE
    }

    private fun showContent(hasGroups: Boolean) {
        binding.progress.visibility = View.GONE
        binding.permissionGate.visibility = View.GONE
        binding.recyclerGroups.visibility = if (hasGroups) View.VISIBLE else View.GONE
        binding.emptyState.visibility = if (hasGroups) View.GONE else View.VISIBLE
        if (!hasGroups) {
            binding.emptyTitle.text = getString(R.string.empty_region_title)
            binding.emptySubtitle.text = getString(R.string.empty_region_subtitle)
        }
    }

    private fun showPermissionGate() {
        binding.progress.visibility = View.GONE
        binding.emptyState.visibility = View.GONE
        binding.recyclerGroups.visibility = View.GONE
        binding.permissionGate.visibility = View.VISIBLE
        binding.swipeRefresh.isRefreshing = false
    }

    private fun openTripDetail(group: LocationGroup) {
        startActivity(
            Intent(this, LocationDetailActivity::class.java).apply {
                putExtra(LocationDetailActivity.EXTRA_PHOTO_IDS, group.photos.map { it.id }.toLongArray())
                putExtra(LocationDetailActivity.EXTRA_PLACE_NAME, group.placeName)
            }
        )
    }
}
