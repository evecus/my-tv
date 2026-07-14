package com.github.mytv

import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

/**
 * 两栏频道列表左侧「分组」列表适配器。
 * 高亮显示当前正在播放频道所属的分组。
 */
class GroupListAdapter(
    private val groups: List<String>,
) : RecyclerView.Adapter<GroupListAdapter.ViewHolder>() {

    private var selectedPosition = 0
    private var listener: Listener? = null

    interface Listener {
        /** 焦点移动到某个分组时触发（用于联动右侧频道列表） */
        fun onGroupFocused(position: Int)

        /** 在分组列表上按下确定键/点击 */
        fun onGroupConfirmed(position: Int)

        /** 在分组列表上按左右键，用于在两栏之间切换焦点 */
        fun onNavigate(keyCode: Int): Boolean
    }

    fun setListener(listener: Listener) {
        this.listener = listener
    }

    /** 设置当前高亮的分组下标（对应正在播放的频道所在分组） */
    fun setSelectedPosition(position: Int) {
        if (position == selectedPosition) return
        val old = selectedPosition
        selectedPosition = position
        notifyItemChanged(old)
        notifyItemChanged(selectedPosition)
    }

    fun getSelectedPosition() = selectedPosition

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_list_group, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.textView.text = groups.getOrNull(position) ?: ""
        holder.applyHighlight(position == selectedPosition, holder.itemView.isFocused)

        holder.itemView.setOnFocusChangeListener { view, hasFocus ->
            holder.applyHighlight(position == selectedPosition, hasFocus)
            if (hasFocus) {
                listener?.onGroupFocused(position)
            }
        }

        holder.itemView.setOnClickListener {
            listener?.onGroupConfirmed(position)
        }

        holder.itemView.setOnKeyListener { _, keyCode, event ->
            if (event.action != KeyEvent.ACTION_DOWN) return@setOnKeyListener false
            when (keyCode) {
                KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                    listener?.onGroupConfirmed(position)
                    true
                }
                KeyEvent.KEYCODE_DPAD_RIGHT -> {
                    listener?.onNavigate(keyCode) ?: false
                }
                else -> false
            }
        }
    }

    override fun getItemCount() = groups.size

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val textView: TextView = view as TextView

        fun applyHighlight(isSelected: Boolean, hasFocus: Boolean) {
            when {
                hasFocus -> textView.setBackgroundResource(R.drawable.rounded_light_bottom)
                isSelected -> textView.setBackgroundResource(R.drawable.rounded_selected_bottom)
                else -> textView.setBackgroundResource(R.drawable.rounded_dark_bottom)
            }
        }
    }

    fun focusPosition(recyclerView: RecyclerView, position: Int) {
        recyclerView.post {
            recyclerView.scrollToPosition(position)
            recyclerView.post {
                recyclerView.findViewHolderForAdapterPosition(position)?.itemView?.requestFocus()
            }
        }
    }
}
