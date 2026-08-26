package com.green3077.photoorganizer.ui

import android.content.Context
import android.util.AttributeSet
import android.widget.FrameLayout

/** 바둑판(그리드) 보기의 각 칸을 정사각형으로 강제하는 컨테이너. 너비에 맞춰 높이를 맞춘다. */
class SquareFrameLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, widthMeasureSpec)
    }
}
