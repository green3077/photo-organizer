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
import com.green3077.photoorganizer.data.PhotoRepository
import com.green3077.photoorganizer.databinding.ActivityCalendarBinding
import com.green3077.photoorganizer.model.CalendarCell
import com.green3077.photoorganizer.model.Photo
import com.green3077.photoorganizer.ui.CalendarDayAdapter
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth

/**
 * 홈 화면 "달력으로 보기" — 이번 달(기본값) 달력을 보여주고, 사진이 있는 날짜 칸에는
 * 그날의 대표 사진 한 장을 채워 넣는다(왼쪽 위 날짜, 오른쪽 아래 장수). 칸을 누르면
 * 그 날짜의 사진들을 모아 보는 DayDetailActivity로 이동한다.
 */
class CalendarActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCalendarBinding
    private lateinit var adapter: CalendarDayAdapter
    private val repository by lazy { PhotoRepository(this) }
    private var allPhotos: List<Photo> = emptyList()
    private var currentMonth: YearMonth = YearMonth.now()

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            if (hasPermission()) loadAndRender() else showPermissionGate()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCalendarBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        binding.toolbar.setNavigationOnClickListener { finish() }

        adapter = CalendarDayAdapter { date -> openDayDetail(date) }
        binding.recyclerCalendar.layoutManager = GridLayoutManager(this, COLUMN_COUNT)
        binding.recyclerCalendar.adapter = adapter

        binding.btnPrevMonth.setOnClickListener {
            currentMonth = currentMonth.minusMonths(1)
            renderMonth()
        }
        binding.btnNextMonth.setOnClickListener {
            currentMonth = currentMonth.plusMonths(1)
            renderMonth()
        }
        binding.btnGrantPermission.setOnClickListener {
            requestPermissionLauncher.launch(requiredPermissions())
        }
    }

    override fun onStart() {
        super.onStart()
        if (hasPermission()) loadAndRender() else showPermissionGate()
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

    private fun loadAndRender() {
        showLoading()
        lifecycleScope.launch {
            allPhotos = repository.loadAllPhotos()
            showContent()
            renderMonth()
        }
    }

    private fun renderMonth() {
        binding.textMonth.text = getString(R.string.calendar_month_title, currentMonth.year, currentMonth.monthValue)

        val photosByDate = allPhotos.groupBy { it.dateTaken }
        val firstDay = currentMonth.atDay(1)
        val leadingBlanks = firstDay.dayOfWeek.value % 7

        val cells = mutableListOf<CalendarCell>()
        repeat(leadingBlanks) { cells.add(CalendarCell.Blank) }
        for (day in 1..currentMonth.lengthOfMonth()) {
            val date = currentMonth.atDay(day)
            val dayPhotos = photosByDate[date].orEmpty()
            cells.add(CalendarCell.Day(date, dayPhotos.firstOrNull(), dayPhotos.size))
        }
        adapter.submit(cells)
    }

    private fun openDayDetail(date: LocalDate) {
        startActivity(
            Intent(this, DayDetailActivity::class.java).apply {
                putExtra(DayDetailActivity.EXTRA_EPOCH_DAY, date.toEpochDay())
            }
        )
    }

    private fun showLoading() {
        binding.progress.visibility = View.VISIBLE
        binding.permissionGate.visibility = View.GONE
        binding.recyclerCalendar.visibility = View.GONE
    }

    private fun showContent() {
        binding.progress.visibility = View.GONE
        binding.permissionGate.visibility = View.GONE
        binding.recyclerCalendar.visibility = View.VISIBLE
    }

    private fun showPermissionGate() {
        binding.progress.visibility = View.GONE
        binding.recyclerCalendar.visibility = View.GONE
        binding.permissionGate.visibility = View.VISIBLE
    }

    companion object {
        private const val COLUMN_COUNT = 7
    }
}
