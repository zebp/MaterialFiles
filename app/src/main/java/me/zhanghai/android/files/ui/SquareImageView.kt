package me.zhanghai.android.files.ui

import android.content.Context
import android.util.AttributeSet

class SquareImageView(context: Context, attributeSet: AttributeSet) : androidx.appcompat.widget.AppCompatImageView(context, attributeSet) {
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = MeasureSpec.getSize(heightMeasureSpec)
        if (width > height) {
            setMeasuredDimension(width, width)
        } else {
            setMeasuredDimension(height, height)
        }
    }
}