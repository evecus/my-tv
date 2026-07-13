package com.github.mytv

import android.content.Context
import android.graphics.Rect
import android.util.TypedValue
import android.view.View
import androidx.recyclerview.widget.RecyclerView

class ItemDecoration(private val context: Context) : RecyclerView.ItemDecoration() {
    override fun getItemOffsets(outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State) {
        super.getItemOffsets(outRect, view, parent, state)
        val space = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 4f,
            context.resources.displayMetrics).toInt()
        outRect.top = space; outRect.bottom = space
        outRect.left = space; outRect.right = space
    }
}
