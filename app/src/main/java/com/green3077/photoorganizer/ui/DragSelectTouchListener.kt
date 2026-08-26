package com.green3077.photoorganizer.ui

import android.view.GestureDetector
import android.view.MotionEvent
import androidx.recyclerview.widget.RecyclerView

/**
 * 사진 하나를 길게 눌러 선택한 뒤, 손을 떼지 않고 그대로 드래그하면 지나가는 사진들이
 * 잇달아 선택(혹은 해제)되게 한다. 첫 롱프레스로 눌린 항목의 상태 반대값을 "이번 드래그가
 * 선택인지 해제인지"로 고정해두고, 드래그가 지나가는 항목마다 그 값으로 맞춰준다.
 */
class DragSelectTouchListener(
    private val recyclerView: RecyclerView,
    private val isSelectablePosition: (Int) -> Boolean,
    private val isSelected: (Int) -> Boolean,
    private val setSelected: (Int, Boolean) -> Unit
) : RecyclerView.OnItemTouchListener {

    private var isDragging = false
    private var selectValue = true
    private var lastPosition = RecyclerView.NO_POSITION

    private val gestureDetector =
        GestureDetector(recyclerView.context, object : GestureDetector.SimpleOnGestureListener() {
            override fun onLongPress(e: MotionEvent) {
                val child = recyclerView.findChildViewUnder(e.x, e.y) ?: return
                val position = recyclerView.getChildAdapterPosition(child)
                if (position == RecyclerView.NO_POSITION || !isSelectablePosition(position)) return

                isDragging = true
                selectValue = !isSelected(position)
                lastPosition = position
                setSelected(position, selectValue)
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
        val child = recyclerView.findChildViewUnder(x, y) ?: return
        val position = recyclerView.getChildAdapterPosition(child)
        if (position == RecyclerView.NO_POSITION || position == lastPosition) return
        if (!isSelectablePosition(position)) return
        lastPosition = position
        setSelected(position, selectValue)
    }

    private fun endDrag() {
        isDragging = false
        lastPosition = RecyclerView.NO_POSITION
    }
}
