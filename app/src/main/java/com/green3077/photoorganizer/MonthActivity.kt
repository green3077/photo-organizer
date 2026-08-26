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
import com.green3077.photoorganizer.data.PhotoRepository
import com.green3077.photoorganizer.databinding.ActivityMonthBinding
import com.green3077.photoorganizer.model.Photo
import com.green3077.photoorganizer.ui.GroupViewMode
import com.green3077.photoorganizer.ui.MonthGroupAdapter
import kotlinx.coroutines.launch

/** "월별 정리" — 연도에 상관없이 월(1~12월) 단위로 사진을 모아 보여준다. */
class MonthActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMonthBinding
    private lateinit var adapter: MonthGroupAdapter
    private val repository by lazy { PhotoRepository(this) }
    private var allPhotos: List<Photo> = emptyList()
    private var groupViewMode: GroupViewMode = GroupViewMode.LIST

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            if (hasPermission()) onMediaPermissionGranted() else showPermissionGate()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMonthBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        binding.toolbar.setNavigationOnClickListener { finish() }

        groupViewMode = loadGroupViewMode()
        adapter = MonthGroupAdapter(groupViewMode) { group -> openDetail(group.month) }
        binding.recyclerGroups.layoutManager = layoutManagerFor(groupViewMode)
        binding.recyclerGroups.adapter = adapter
        binding.fastScrollbar.attachTo(binding.recyclerGroups)
        binding.fastScrollbar.labelProvider = adapter::labelAt

        binding.toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_toggle_view -> {
                    toggleGroupViewMode()
                    true
                }
                else -> false
            }
        }
        updateToggleMenuItem()

        binding.btnGrantPermission.setOnClickListener {
            requestPermissionLauncher.launch(requiredPermissions())
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

    private fun corePermission(): String =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Manifest.permission.READ_MEDIA_IMAGES
        else Manifest.permission.READ_EXTERNAL_STORAGE

    private fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, corePermission()) == PackageManager.PERMISSION_GRANTED

    private fun onMediaPermissionGranted() {
        loadPhotos()
        requestMissingSupplementaryPermissions()
    }

    private fun requestMissingSupplementaryPermissions() {
        val missing = requiredPermissions().filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            requestPermissionLauncher.launch(missing.toTypedArray())
        }
    }

    private fun loadPhotos() {
        showLoading()
        lifecycleScope.launch {
            allPhotos = repository.loadAllPhotos()
            binding.swipeRefresh.isRefreshing = false
            val groups = repository.buildMonthGroups(allPhotos)
            adapter.submit(groups)
            binding.fastScrollbar.refresh()
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
            binding.emptyTitle.text = getString(R.string.empty_month_title)
            binding.emptySubtitle.text = getString(R.string.empty_month_subtitle)
        }
    }

    private fun showPermissionGate() {
        binding.progress.visibility = View.GONE
        binding.emptyState.visibility = View.GONE
        binding.recyclerGroups.visibility = View.GONE
        binding.permissionGate.visibility = View.VISIBLE
        binding.swipeRefresh.isRefreshing = false
    }

    private fun openDetail(month: Int) {
        startActivity(
            Intent(this, MonthDetailActivity::class.java).apply {
                putExtra(MonthDetailActivity.EXTRA_MONTH, month)
            }
        )
    }

    private fun layoutManagerFor(mode: GroupViewMode): RecyclerView.LayoutManager =
        if (mode == GroupViewMode.GRID) GridLayoutManager(this, GRID_SPAN_COUNT) else LinearLayoutManager(this)

    private fun toggleGroupViewMode() {
        groupViewMode = if (groupViewMode == GroupViewMode.GRID) GroupViewMode.LIST else GroupViewMode.GRID
        saveGroupViewMode(groupViewMode)
        adapter.setViewMode(groupViewMode)
        binding.recyclerGroups.layoutManager = layoutManagerFor(groupViewMode)
        binding.fastScrollbar.refresh()
        updateToggleMenuItem()
    }

    private fun updateToggleMenuItem() {
        val item = binding.toolbar.menu.findItem(R.id.action_toggle_view) ?: return
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

    companion object {
        private const val GRID_SPAN_COUNT = 3
        private const val PREFS_NAME = "month_view_prefs"
        private const val KEY_VIEW_MODE = "month_group_view_mode"
    }
}
