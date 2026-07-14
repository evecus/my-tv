package com.github.mytv

import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.github.mytv.models.TV

/**
 * 两栏频道列表右侧「频道」列表适配器，展示当前选中分组下的频道。
 */
class ChannelListAdapter(
    private var channels: List<TV>,
) : RecyclerView.Adapter<ChannelListAdapter.ViewHolder>() {

    /** 当前正在播放的频道在本分组内的下标，-1 表示当前分组没有正在播放的频道 */
    private var playingPosition = -1
    private var listener: Listener? = null

    interface Listener {
        fun onChannelConfirmed(position: Int)
        fun onNavigate(keyCode: Int): Boolean
    }

    fun setListener(listener: Listener) {
        this.listener = listener
    }

    fun updateChannels(channels: List<TV>, playingPosition: Int) {
        this.channels = channels
        this.playingPosition = playingPosition
        notifyDataSetChanged()
    }

    fun setPlayingPosition(position: Int) {
        if (position == playingPosition) return
        val old = playingPosition
        playingPosition = position
        if (old in channels.indices) notifyItemChanged(old)
        if (playingPosition in channels.indices) notifyItemChanged(playingPosition)
    }

    fun getPlayingPosition() = playingPosition

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_list_channel, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val tv = channels.getOrNull(position) ?: return
        holder.number.text = (position + 1).toString()
        holder.name.text = tv.title
        holder.applyHighlight(position == playingPosition, holder.itemView.isFocused)

        holder.itemView.setOnFocusChangeListener { _, hasFocus ->
            holder.applyHighlight(position == playingPosition, hasFocus)
        }

        holder.itemView.setOnClickListener {
            listener?.onChannelConfirmed(position)
        }

        holder.itemView.setOnKeyListener { _, keyCode, event ->
            if (event.action != KeyEvent.ACTION_DOWN) return@setOnKeyListener false
            when (keyCode) {
                KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                    listener?.onChannelConfirmed(position)
                    true
                }
                KeyEvent.KEYCODE_DPAD_LEFT -> {
                    listener?.onNavigate(keyCode) ?: false
                }
                else -> false
            }
        }
    }

    override fun getItemCount() = channels.size

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val row: View = view.findViewById(R.id.channel_row)
        val number: TextView = view.findViewById(R.id.channel_num)
        val name: TextView = view.findViewById(R.id.channel_name)

        fun applyHighlight(isSelected: Boolean, hasFocus: Boolean) {
            when {
                hasFocus -> row.setBackgroundResource(R.drawable.rounded_light_bottom)
                isSelected -> row.setBackgroundResource(R.drawable.rounded_selected_bottom)
                else -> row.setBackgroundResource(R.drawable.rounded_dark_bottom)
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
