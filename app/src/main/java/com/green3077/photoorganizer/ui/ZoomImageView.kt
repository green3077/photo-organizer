package com.green3077.photoorganizer.ui

import android.content.Context
import android.graphics.Matrix
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import androidx.appcompat.widget.AppCompatImageView
import kotlin.math.min

/**
 * ViewPager2 페이지 안에 들어가는 사진 뷰. 핀치/더블탭으로 확대·축소하고 확대 상태에서는
 * 손가락으로 끌어(pan) 볼 수 있다. 확대돼 있을 때만 부모(ViewPager2)의 좌우 페이지 스와이프를
 * 막아서, 평소(확대 안 한 상태)엔 그대로 옆 사진으로 스와이프된다.
 */
class ZoomImageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : AppCompatImageView(context, attrs) {

    /** 확대/이동 제스처가 아닌 순수한 한 번 탭. 툴바·하단 바 표시를 토글하는 데 쓴다. */
    var onSingleTap: (() -> Unit)? = null

    private val baseMatrix = Matrix()
    private val drawMatrix = Matrix()

    private val minScale = 1f
    private val maxScale = 4f
    private var currentScale = 1f

    private val scaleDetector =
        ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                val targetScale = (currentScale * detector.scaleFactor).coerceIn(minScale, maxScale)
                val factor = targetScale / currentScale
                currentScale = targetScale
                drawMatrix.postScale(factor, factor, detector.focusX, detector.focusY)
                constrainTranslation()
                imageMatrix = drawMatrix
                return true
            }
        })

    private val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDoubleTap(e: MotionEvent): Boolean {
            val target = if (currentScale > minScale + EPSILON) minScale else min(maxScale, minScale * 2.5f)
            val factor = target / currentScale
            currentScale = target
            drawMatrix.postScale(factor, factor, e.x, e.y)
            constrainTranslation()
            imageMatrix = drawMatrix
            return true
        }

        override fun onScroll(e1: MotionEvent?, e2: MotionEvent, distanceX: Float, distanceY: Float): Boolean {
            if (currentScale <= minScale + EPSILON) return false
            drawMatrix.postTranslate(-distanceX, -distanceY)
            constrainTranslation()
            imageMatrix = drawMatrix
            return true
        }

        override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
            onSingleTap?.invoke()
            return true
        }
    })

    init {
        scaleType = ScaleType.MATRIX
    }

    override fun setImageDrawable(drawable: Drawable?) {
        super.setImageDrawable(drawable)
        resetToBaseMatrix()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        resetToBaseMatrix()
    }

    private fun resetToBaseMatrix() {
        val d = drawable ?: return
        if (width == 0 || height == 0) return
        val dw = d.intrinsicWidth.toFloat()
        val dh = d.intrinsicHeight.toFloat()
        if (dw <= 0f || dh <= 0f) return

        val scale = min(width / dw, height / dh)
        val dx = (width - dw * scale) / 2f
        val dy = (height - dh * scale) / 2f

        baseMatrix.reset()
        baseMatrix.postScale(scale, scale)
        baseMatrix.postTranslate(dx, dy)

        drawMatrix.set(baseMatrix)
        currentScale = minScale
        imageMatrix = drawMatrix
    }

    private fun constrainTranslation() {
        val d = drawable ?: return
        val rect = RectF(0f, 0f, d.intrinsicWidth.toFloat(), d.intrinsicHeight.toFloat())
        drawMatrix.mapRect(rect)

        val dx = when {
            rect.width() <= width -> (width - rect.width()) / 2f - rect.left
            rect.left > 0f -> -rect.left
            rect.right < width -> width - rect.right
            else -> 0f
        }
        val dy = when {
            rect.height() <= height -> (height - rect.height()) / 2f - rect.top
            rect.top > 0f -> -rect.top
            rect.bottom < height -> height - rect.bottom
            else -> 0f
        }
        drawMatrix.postTranslate(dx, dy)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)
        gestureDetector.onTouchEvent(event)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN ->
                parent?.requestDisallowInterceptTouchEvent(currentScale > minScale + EPSILON || event.pointerCount > 1)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (currentScale <= minScale + EPSILON) {
                    currentScale = minScale
                    drawMatrix.set(baseMatrix)
                    imageMatrix = drawMatrix
                }
                parent?.requestDisallowInterceptTouchEvent(false)
            }
        }
        return true
    }

    companion object {
        private const val EPSILON = 0.01f
    }
}
