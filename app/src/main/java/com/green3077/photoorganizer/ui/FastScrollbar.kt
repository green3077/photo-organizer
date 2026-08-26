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
 *
 * 드래그 중에는 [labelProvider]가 돌려주는 값(연도/날짜 등)을 손잡이 왼쪽에 말풍선으로
 * 보여줘서, 지금 어디까지 왔는지 놓치지 않게 한다.
 */
class FastScrollbar @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    /** 드래그 중인 목표 어댑터 position을 받아 라벨(연도/날짜 등)을 돌려준다. null이면 말풍선을 안 그린다. */
    var labelProvider: ((Int) -> String?)? = null

    private var recyclerView: RecyclerView? = null

    private val thumbWidthPx = dp(6f)
    private val thumbMinHeightPx = dp(48f)
    private val touchZoneWidthPx = dp(32f)
    private val marginEndPx = dp(4f)
    private val labelMarginEndPx = dp(14f)
    private val labelPaddingHPx = dp(14f)
    private val labelPaddingVPx = dp(8f)
    private val hideDelayMs = 1000L

    private val thumbPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.primary)
    }
    private val labelBackgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.primary)
    }
    private val labelTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.white)
        textSize = dp(15f)
        textAlign = Paint.Align.RIGHT
    }

    private var thumbTop = 0f
    private var thumbHeight = thumbMinHeightPx
    private var isDragging = false
    private var isScrollable = false
    private var currentLabel: String? = null

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

        val label = currentLabel
        if (isDragging && label != null) drawLabelBubble(canvas, label, left, thumbTop + thumbHeight / 2f)
    }

    private fun drawLabelBubble(canvas: Canvas, label: String, thumbLeft: Float, centerY: Float) {
        val textWidth = labelTextPaint.measureText(label)
        val fm = labelTextPaint.fontMetrics
        val textHeight = fm.descent - fm.ascent

        val bubbleRight = thumbLeft - labelMarginEndPx
        val bubbleLeft = bubbleRight - textWidth - labelPaddingHPx * 2f
        val bubbleTop = centerY - textHeight / 2f - labelPaddingVPx
        val bubbleBottom = centerY + textHeight / 2f + labelPaddingVPx

        val cornerRadius = (bubbleBottom - bubbleTop) / 2f
        canvas.drawRoundRect(
            RectF(bubbleLeft, bubbleTop, bubbleRight, bubbleBottom),
            cornerRadius, cornerRadius, labelBackgroundPaint
        )
        canvas.drawText(label, bubbleRight - labelPaddingHPx, centerY - (fm.ascent + fm.descent) / 2f, labelTextPaint)
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
                currentLabel = null
                parent?.requestDisallowInterceptTouchEvent(false)
                showTemporarily()
                invalidate()
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
        currentLabel = labelProvider?.invoke(targetPosition)
        invalidate()
    }

    private fun showTemporarily() {
        animate().alpha(1f).setDuration(100).start()
        handler?.removeCallbacks(hideRunnable)
        handler?.postDelayed(hideRunnable, hideDelayMs)
    }

    private fun dp(value: Float): Float = value * resources.displayMetrics.density
}
