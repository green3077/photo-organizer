package com.green3077.photoorganizer.ui

import android.view.GestureDetector
import android.view.MotionEvent
import androidx.recyclerview.widget.RecyclerView
import kotlin.math.abs

/**
 * 갤러리 앱들의 "길게 눌러 드래그로 범위 선택" 제스처.
 *
 * 손가락이 실제로 지나간 항목만 선택하면 대각선으로 끌 때 빈틈이 생기므로, 대신
 * "롱프레스로 잡은 시작 항목(anchor)"과 "지금 손가락 아래 있는 항목" 사이를 어댑터
 * 위치 순서(그리드의 위→아래, 왼→오른쪽 읽기 순서와 동일)로 통째로 채워 선택한다.
 * 예: 3열 그리드에서 1행1열 → 3행1열로 끌면 1~2행 전체 + 3행1열까지 선택된다.
 *
 * 드래그를 시작할 때의 선택 상태를 스냅샷으로 저장해뒀다가, 범위가 줄어들면(손가락을
 * 다시 시작점 쪽으로 되돌리면) 범위 밖으로 빠진 항목을 스냅샷 상태로 되돌린다.
 */
class DragSelectTouchListener(
    private val recyclerView: RecyclerView,
    private val isSelectablePosition: (Int) -> Boolean,
    private val isSelected: (Int) -> Boolean,
    private val setSelected: (Int, Boolean) -> Unit
) : RecyclerView.OnItemTouchListener {

    private var isDragging = false
    private var anchorPosition = RecyclerView.NO_POSITION
    private var selectValue = true
    private var appliedRange: IntRange? = null
    private var selectionSnapshot: Map<Int, Boolean> = emptyMap()

    private val edgeZonePx = (recyclerView.resources.displayMetrics.density * 56).toInt()
    private val maxScrollPxPerMove = (recyclerView.resources.displayMetrics.density * 16).toInt()

    private val gestureDetector =
        GestureDetector(recyclerView.context, object : GestureDetector.SimpleOnGestureListener() {
            override fun onLongPress(e: MotionEvent) {
                val position = positionAt(e.x, e.y) ?: return
                if (!isSelectablePosition(position)) return

                isDragging = true
                anchorPosition = position
                selectValue = !isSelected(position)
                selectionSnapshot = (0 until (recyclerView.adapter?.itemCount ?: 0))
                    .filter(isSelectablePosition)
                    .associateWith(isSelected)
                appliedRange = null
                applyRange(position)
                recyclerView.parent?.requestDisallowInterceptTouchEvent(true)
            }
        })

    fun attach() {
        recyclerView.addOnItemTouchListener(this)
    }

    override fun onInterceptTouchEvent(rv: RecyclerView, e: MotionEvent): Boolean {
        gestureDetector.onTouchEvent(e)
        if (e.actionMasked == MotionEvent.ACTION_UP || e.actionMasked == MotionEvent.ACTION_CANCEL) endDrag()
        return isDragging
    }

    override fun onTouchEvent(rv: RecyclerView, e: MotionEvent) {
        gestureDetector.onTouchEvent(e)
        when (e.actionMasked) {
            MotionEvent.ACTION_MOVE -> if (isDragging) handleMove(e.x, e.y)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> endDrag()
        }
    }

    override fun onRequestDisallowInterceptTouchEvent(disallowIntercept: Boolean) = Unit

    private fun handleMove(x: Float, y: Float) {
        autoScrollIfNeeded(y)
        val position = positionAt(x, y) ?: return
        applyRange(position)
    }

    private fun applyRange(currentPosition: Int) {
        val newRange = minOf(anchorPosition, currentPosition)..maxOf(anchorPosition, currentPosition)

        appliedRange?.forEach { position ->
            if (position !in newRange && isSelectablePosition(position)) {
                setSelected(position, selectionSnapshot[position] ?: false)
            }
        }
        newRange.forEach { position ->
            if (isSelectablePosition(position)) setSelected(position, selectValue)
        }
        appliedRange = newRange
    }

    /** 손가락 아래의 실제 항목 위치를 찾되, 빈 칸(마지막 줄의 빈 셀 등) 위라면 가장 가까운 항목으로 대체한다. */
    private fun positionAt(x: Float, y: Float): Int? {
        val clampedY = y.coerceIn(0f, (recyclerView.height - 1).coerceAtLeast(0).toFloat())
        val child = recyclerView.findChildViewUnder(x, clampedY)
            ?: (0 until recyclerView.childCount)
                .map { recyclerView.getChildAt(it) }
                .minByOrNull { abs((it.top + it.bottom) / 2f - clampedY) }
            ?: return null
        val position = recyclerView.getChildAdapterPosition(child)
        return if (position == RecyclerView.NO_POSITION) null else position
    }

    private fun autoScrollIfNeeded(y: Float) {
        val height = recyclerView.height
        if (height <= 0) return
        when {
            y < edgeZonePx -> {
                val strength = 1f - (y / edgeZonePx).coerceIn(0f, 1f)
                recyclerView.scrollBy(0, (-maxScrollPxPerMove * strength).toInt())
            }
            y > height - edgeZonePx -> {
                val strength = ((y - (height - edgeZonePx)) / edgeZonePx).coerceIn(0f, 1f)
                recyclerView.scrollBy(0, (maxScrollPxPerMove * strength).toInt())
            }
        }
    }

    private fun endDrag() {
        isDragging = false
        anchorPosition = RecyclerView.NO_POSITION
        appliedRange = null
        selectionSnapshot = emptyMap()
    }
}
