package com.github.mytv

import android.os.Bundle
import android.view.GestureDetector
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.github.mytv.databinding.ListMenuBinding
import com.github.mytv.models.TV
import com.github.mytv.models.TVList

/**
 * 默认（非网格）频道列表：左侧分组、右侧该分组下的频道，两栏展示。
 * 类似经典机顶盒 EPG 列表：高亮显示当前播放的分组与频道，方向键在两栏间切换，
 * 确定键播放选中频道并退出列表进入全屏播放。
 */
class ListMenuFragment : Fragment() {

    private var _binding: ListMenuBinding? = null
    private val binding get() = _binding!!

    private lateinit var application: MyTVApplication
    private lateinit var gestureDetector: GestureDetector

    /** 分组名有序列表，与 TVList.list 的 key 顺序一致 */
    private var groupNames: List<String> = emptyList()

    /** 分组名 -> 频道列表 */
    private var groupChannels: List<List<TV>> = emptyList()

    private lateinit var groupAdapter: GroupListAdapter
    private lateinit var channelAdapter: ChannelListAdapter

    /** 当前正在播放频道所在的分组下标 */
    private var playingGroupPosition = 0

    /** 当前正在播放频道在其分组内的下标 */
    private var playingChannelPosition = 0

    /** 当前右侧列表展示的是哪个分组（可能只是浏览，不代表正在播放） */
    private var browsingGroupPosition = 0

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = ListMenuBinding.inflate(inflater, container, false)
        application = requireActivity().applicationContext as MyTVApplication

        binding.listContainer.setOnClickListener { hideSelf() }

        gestureDetector = GestureDetector(requireContext(), GestureListener())

        binding.listGroup.layoutManager = LinearLayoutManager(context)
        binding.listChannel.layoutManager = LinearLayoutManager(context)

        binding.listGroup.addOnItemTouchListener(object : RecyclerView.OnItemTouchListener {
            override fun onInterceptTouchEvent(rv: RecyclerView, e: MotionEvent): Boolean {
                gestureDetector.onTouchEvent(e)
                return false
            }
            override fun onTouchEvent(rv: RecyclerView, e: MotionEvent) {}
            override fun onRequestDisallowInterceptTouchEvent(b: Boolean) {}
        })

        return binding.root
    }

    private inner class GestureListener : GestureDetector.SimpleOnGestureListener() {
        override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
            return false
        }
    }

    private fun hideSelf() {
        requireActivity().supportFragmentManager.beginTransaction()
            .hide(this).commit()
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        buildLists()
    }

    /**
     * 根据 TVList.list 构建分组 / 频道数据源。
     * 可在测速完成后再次调用以刷新数据。
     */
    fun buildLists() {
        val groups = mutableListOf<String>()
        val channelsPerGroup = mutableListOf<List<TV>>()
        for ((k, v) in TVList.list) {
            groups.add(k)
            channelsPerGroup.add(v)
        }
        groupNames = groups
        groupChannels = channelsPerGroup

        groupAdapter = GroupListAdapter(groupNames)
        channelAdapter = ChannelListAdapter(emptyList())

        binding.listGroup.adapter = groupAdapter
        binding.listChannel.adapter = channelAdapter

        groupAdapter.setListener(object : GroupListAdapter.Listener {
            override fun onGroupFocused(position: Int) {
                showGroupChannels(position)
            }

            override fun onGroupConfirmed(position: Int) {
                // 分组本身不可播放，确定键直接把焦点交给右侧第一项（或正在播放项）
                showGroupChannels(position)
                val focusTarget =
                    if (position == playingGroupPosition) playingChannelPosition else 0
                channelAdapter.focusPosition(binding.listChannel, focusTarget)
            }

            override fun onNavigate(keyCode: Int): Boolean {
                if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                    val focusTarget =
                        if (browsingGroupPosition == playingGroupPosition) playingChannelPosition else 0
                    channelAdapter.focusPosition(binding.listChannel, focusTarget)
                    return true
                }
                return false
            }
        })

        channelAdapter.setListener(object : ChannelListAdapter.Listener {
            override fun onChannelConfirmed(position: Int) {
                playChannel(browsingGroupPosition, position)
            }

            override fun onNavigate(keyCode: Int): Boolean {
                if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                    groupAdapter.focusPosition(binding.listGroup, browsingGroupPosition)
                    return true
                }
                return false
            }
        })

        // 找到当前正在播放的频道所在分组与下标
        locatePlayingChannel()
    }

    /** 根据 SP.itemPosition（全局频道下标）反查其所在的分组与组内下标 */
    private fun locatePlayingChannel() {
        val currentId = SP.itemPosition
        var groupIdx = 0
        var itemIdx = 0
        outer@ for ((gi, channels) in groupChannels.withIndex()) {
            for ((ii, tv) in channels.withIndex()) {
                if (tv.id == currentId) {
                    groupIdx = gi
                    itemIdx = ii
                    break@outer
                }
            }
        }
        playingGroupPosition = groupIdx
        playingChannelPosition = itemIdx
        browsingGroupPosition = groupIdx

        groupAdapter.setSelectedPosition(groupIdx)
        showGroupChannels(groupIdx)
    }

    private fun showGroupChannels(groupPosition: Int) {
        browsingGroupPosition = groupPosition
        val channels = groupChannels.getOrNull(groupPosition) ?: emptyList()
        val playing = if (groupPosition == playingGroupPosition) playingChannelPosition else -1
        channelAdapter.updateChannels(channels, playing)
    }

    private fun playChannel(groupPosition: Int, channelPosition: Int) {
        val tv = groupChannels.getOrNull(groupPosition)?.getOrNull(channelPosition) ?: return
        playingGroupPosition = groupPosition
        playingChannelPosition = channelPosition
        groupAdapter.setSelectedPosition(groupPosition)
        channelAdapter.setPlayingPosition(channelPosition)
        (activity as? MainActivity)?.playById(tv.id)
        (activity as? MainActivity)?.switchMainFragment()
    }

    /** 供 MainActivity 在切换到列表时，把焦点定位到当前播放频道所在位置 */
    fun focusCurrentChannel() {
        if (!::groupAdapter.isInitialized) return
        locatePlayingChannel()
        groupAdapter.focusPosition(binding.listGroup, playingGroupPosition)
    }

    fun onKey(keyCode: Int): Boolean {
        if (this.isHidden) {
            when (keyCode) {
                KeyEvent.KEYCODE_DPAD_UP -> { (activity as MainActivity).onKey(keyCode); return true }
                KeyEvent.KEYCODE_DPAD_DOWN -> { (activity as MainActivity).onKey(keyCode); return true }
            }
        }
        return false
    }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        if (!hidden) {
            focusCurrentChannel()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val TAG = "ListMenuFragment"
    }
}
