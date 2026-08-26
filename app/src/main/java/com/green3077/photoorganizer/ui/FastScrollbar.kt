package com.green3077.photoorganizer.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.green3077.photoorganizer.R

/**
 * 목록/그리드 오른쪽 가장자리에 붙는 드래그용 손잡이. 스크롤할 때 잠깐 나타났다 사라지고,
 * 손잡이를 잡고 위아래로 끌면 전체 목록 길이에서의 위치 비율만큼 바로 점프한다(하나씩 안
 * 내려도 됨). 실제 손잡이는 얇지만, 터치 인식 영역은 오른쪽 가장자리 쪽으로 더 넓게 잡아서
 * 손가락으로 잡기 쉽게 했다. 손잡이 밖(왼쪽)을 누르면 그대로 밑에 있는 목록으로 터치가 넘어간다.
 */
class FastScrollbar @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private var recyclerView: RecyclerView? = null

    private val thumbWidthPx = dp(6f)
    private val thumbMinHeightPx = dp(48f)
    private val touchZoneWidthPx = dp(32f)
    private val marginEndPx = dp(4f)
    private val hideDelayMs = 1000L

    private val thumbPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.primary)
    }

    private var thumbTop = 0f
    private var thumbHeight = thumbMinHeightPx
    private var isDragging = false
    private var isScrollable = false

    private val hideRunnable = Runnable { animate().alpha(0f).setDuration(200).start() }

    private val scrollListener = object : RecyclerView.OnScrollListener() {
        override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
            updateThumb()
            if (!isDragging) showTemporarily()
        }
    }

    init {
        alpha = 0f
    }

    fun attachTo(rv: RecyclerView) {
        recyclerView = rv
        rv.addOnScrollListener(scrollListener)
        post { updateThumb() }
    }

    /** 어댑터 내용이 바뀐 뒤(submit 등) 손잡이 크기/위치를 다시 계산하고 싶을 때 호출한다. */
    fun refresh() {
        post { updateThumb() }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        updateThumb()
    }

    private fun updateThumb() {
        val rv = recyclerView ?: return
        val range = rv.computeVerticalScrollRange()
        val extent = rv.computeVerticalScrollExtent()
        val offset = rv.computeVerticalScrollOffset()
        isScrollable = range > extent && height > 0
        if (!isScrollable) return

        val proportion = extent.toFloat() / range.toFloat()
        thumbHeight = (height * proportion).coerceAtLeast(thumbMinHeightPx).coerceAtMost(height.toFloat())
        val maxThumbTop = height - thumbHeight
        val scrollProportion = offset.toFloat() / (range - extent).toFloat()
        thumbTop = (maxThumbTop * scrollProportion).coerceIn(0f, maxThumbTop)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        if (!isScrollable) return
        val left = width - marginEndPx - thumbWidthPx
        val rect = RectF(left, thumbTop, left + thumbWidthPx, thumbTop + thumbHeight)
        canvas.drawRoundRect(rect, thumbWidthPx / 2f, thumbWidthPx / 2f, thumbPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isScrollable) return false

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (!isDragging && event.x < width - touchZoneWidthPx) return false
                isDragging = true
                handler?.removeCallbacks(hideRunnable)
                animate().alpha(1f).setDuration(100).start()
                scrollToThumbPosition(event.y)
                parent?.requestDisallowInterceptTouchEvent(true)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (!isDragging) return false
                scrollToThumbPosition(event.y)
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (!isDragging) return false
                isDragging = false
                parent?.requestDisallowInterceptTouchEvent(false)
                showTemporarily()
                return true
            }
        }
        return false
    }

    private fun scrollToThumbPosition(y: Float) {
        val rv = recyclerView ?: return
        val itemCount = rv.adapter?.itemCount ?: return
        if (itemCount == 0) return
        val maxThumbTop = (height - thumbHeight).coerceAtLeast(1f)
        val proportion = ((y - thumbHeight / 2f) / maxThumbTop).coerceIn(0f, 1f)
        val targetPosition = (proportion * (itemCount - 1)).toInt()
        (rv.layoutManager as? LinearLayoutManager)?.scrollToPositionWithOffset(targetPosition, 0)
    }

    private fun showTemporarily() {
        animate().alpha(1f).setDuration(100).start()
        handler?.removeCallbacks(hideRunnable)
        handler?.postDelayed(hideRunnable, hideDelayMs)
    }

    private fun dp(value: Float): Float = value * resources.displayMetrics.density
}
