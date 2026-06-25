package com.github.mytv

import android.os.Bundle
import android.util.Log
import android.view.GestureDetector
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.view.marginBottom
import androidx.core.view.marginStart
import androidx.core.view.marginTop
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.github.mytv.databinding.MenuBinding
import com.github.mytv.databinding.RowBinding
import com.github.mytv.models.ProgramType
import com.github.mytv.models.TVList
import com.github.mytv.models.TVListViewModel
import com.github.mytv.models.TVViewModel

class MainFragment : Fragment(), CardAdapter.ItemListener {

    private var itemPosition = 0
    private var rowList: MutableList<View> = mutableListOf()

    private var _binding: MenuBinding? = null
    private val binding get() = _binding!!

    var tvListViewModel = TVListViewModel()

    private var lastVideoUrl = ""

    private lateinit var application: MyTVApplication
    private lateinit var gestureDetector: GestureDetector

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val context = requireContext()
        _binding = MenuBinding.inflate(inflater, container, false)

        application = requireActivity().applicationContext as MyTVApplication

        binding.menu.layoutParams.width = application.shouldWidthPx()
        binding.menu.layoutParams.height = application.shouldHeightPx()

        binding.container.setOnClickListener { hideSelf() }

        gestureDetector = GestureDetector(context, GestureListener())

        return binding.root
    }

    private inner class GestureListener : GestureDetector.SimpleOnGestureListener() {
        override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
            hideSelf()
            return true
        }
    }

    private fun hideSelf() {
        requireActivity().supportFragmentManager.beginTransaction()
            .hide(this).commit()
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        itemPosition = SP.itemPosition

        buildChannelList()
    }

    /**
     * 根据 TVList.list 构建频道行列表。
     * 可在测速完成后再次调用以刷新 UI（需先 TVList.load()）。
     */
    fun buildChannelList() {
        // 清除旧数据
        tvListViewModel = TVListViewModel()
        rowList.clear()

        view?.post {
            val content = binding.content
            content.removeAllViews()

            if (TVList.isEmpty()) {
                (activity as MainActivity).fragmentReady(TAG)
                return@post
            }

            var rowIdx: Long = 0
            for ((k, v) in TVList.list) {
                val itemBinding = RowBinding.inflate(layoutInflater, content, false)
                val rowViewModel = TVListViewModel()

                for ((idx2, tv) in v.withIndex()) {
                    val tvViewModel = TVViewModel(tv)
                    tvViewModel.setRowPosition(rowIdx.toInt())
                    tvViewModel.setItemPosition(idx2)
                    rowViewModel.addTVViewModel(tvViewModel)
                    tvListViewModel.addTVViewModel(tvViewModel)
                }
                tvListViewModel.maxNum.add(v.size)

                val adapter = CardAdapter(itemBinding.items, this, rowViewModel)
                rowList.add(itemBinding.items)
                adapter.setItemListener(this)

                itemBinding.header.text = k
                itemBinding.items.tag = rowIdx.toInt()
                itemBinding.items.adapter = adapter

                itemBinding.items.addOnScrollListener(object : RecyclerView.OnScrollListener() {
                    override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
                        (activity as MainActivity).mainActive()
                    }
                })

                context?.let { ctx ->
                    val decoration = ItemDecoration(ctx)
                    itemBinding.items.addItemDecoration(decoration)
                }

                if (SP.grid) {
                    itemBinding.items.layoutManager = GridLayoutManager(context, 6)
                    itemBinding.items.layoutParams.height =
                        application.dp2Px(110 * ((rowViewModel.size() + 5) / 6) + 5)
                } else {
                    itemBinding.items.layoutManager =
                        LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
                }

                val lp = itemBinding.row.layoutParams as ViewGroup.MarginLayoutParams
                lp.topMargin = application.dp2Px(11)
                itemBinding.row.layoutParams = lp
                itemBinding.row.setOnClickListener { hideSelf() }

                itemBinding.items.addOnItemTouchListener(object : RecyclerView.OnItemTouchListener {
                    override fun onInterceptTouchEvent(rv: RecyclerView, e: MotionEvent): Boolean {
                        gestureDetector.onTouchEvent(e)
                        return false
                    }
                    override fun onTouchEvent(rv: RecyclerView, e: MotionEvent) {}
                    override fun onRequestDisallowInterceptTouchEvent(b: Boolean) {}
                })

                val headerLp = itemBinding.header.layoutParams as ViewGroup.MarginLayoutParams
                headerLp.topMargin    = application.px2Px(itemBinding.header.marginTop)
                headerLp.bottomMargin = application.px2Px(itemBinding.header.marginBottom)
                headerLp.marginStart  = application.px2Px(itemBinding.header.marginStart)
                itemBinding.header.layoutParams = headerLp
                itemBinding.header.textSize = application.px2PxFont(itemBinding.header.textSize)

                content.addView(itemBinding.row)
                rowIdx++
            }

            if (itemPosition >= tvListViewModel.size()) itemPosition = 0

            tvListViewModel.setItemPosition(itemPosition)
            tvListViewModel.tvListViewModel.value?.forEach { tvViewModel ->
                tvViewModel.errInfo.observe(viewLifecycleOwner) { _ ->
                    if (tvViewModel.getTV().id == itemPosition) {
                        if (tvViewModel.errInfo.value == "") {
                            (activity as? MainActivity)?.showPlayerFragment()
                            (activity as? MainActivity)?.hideErrorFragment()
                            (activity as? MainActivity)?.hideLoadingFragment()
                        } else {
                            (activity as? MainActivity)?.hidePlayerFragment()
                            (activity as? MainActivity)?.hideLoadingFragment()
                            (activity as? MainActivity)?.showErrorFragment(
                                tvViewModel.errInfo.value.toString()
                            )
                        }
                    }
                }
                tvViewModel.ready.observe(viewLifecycleOwner) { _ ->
                    if (tvViewModel.ready.value != null
                        && tvViewModel.getTV().id == itemPosition
                        && check(tvViewModel)
                    ) {
                        (activity as? MainActivity)?.play(tvViewModel)
                    }
                }
                tvViewModel.change.observe(viewLifecycleOwner) { _ ->
                    if (tvViewModel.change.value != null) {
                        if (tvViewModel.getTV().programType == ProgramType.CUSTOM) {
                            val from = tvViewModel.change.value
                            // 换源时直接播放已有 URL，不再 addVideoUrl（否则每次换源都会新增一条，导致总数不断增加）
                            if (from != "source") {
                                val url = tvViewModel.getVideoUrlCurrent().ifEmpty {
                                    tvViewModel.getTV().videoUrl.firstOrNull() ?: return@observe
                                }
                                tvViewModel.addVideoUrl(url)
                            }
                            tvViewModel.allReady()
                            if (check(tvViewModel)) {
                                (activity as? MainActivity)?.play(tvViewModel)
                                // 换源时不重复显示频道信息卡片
                                if (from != "source") {
                                    (activity as? MainActivity)?.showInfoFragment(tvViewModel)
                                }
                                setPosition(tvViewModel.getRowPosition(), tvViewModel.getItemPosition())
                            }
                        }
                    }
                }
                // 换源指示器
                tvViewModel.sourceChanged.observe(viewLifecycleOwner) { pair ->
                    if (pair != null && tvViewModel.getTV().id == itemPosition) {
                        (activity as? MainActivity)?.showSourceIndicator(pair.first, pair.second)
                    }
                }
            }
            (activity as MainActivity).fragmentReady(TAG)
        }
    }

    fun changeMenu() {
        if (SP.grid) {
            for (i in rowList) {
                if (i is RecyclerView) {
                    i.layoutManager = GridLayoutManager(context, 6)
                    i.layoutParams.height =
                        application.dp2Px(110 * (((i.adapter as CardAdapter).getItemCount() + 5) / 6) + 5)
                }
            }
        } else {
            for (i in rowList) {
                if (i is RecyclerView) {
                    i.layoutManager =
                        LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
                    i.layoutParams.height = application.dp2Px(115)
                }
            }
        }
    }

    override fun onKey(keyCode: Int): Boolean {
        if (this.isHidden) {
            when (keyCode) {
                KeyEvent.KEYCODE_DPAD_UP   -> { (activity as MainActivity).onKey(keyCode); return true }
                KeyEvent.KEYCODE_DPAD_DOWN -> { (activity as MainActivity).onKey(keyCode); return true }
            }
        }
        return false
    }

    override fun onItemHasFocus(tvViewModel: TVViewModel) {
        val row = tvViewModel.getRowPosition()
        for (i in rowList) {
            val adapter = (i as RecyclerView).adapter as CardAdapter
            if (i.tag as Int != row) { adapter.focusable = false; adapter.clear() }
            else adapter.focusable = true
        }
        (activity as MainActivity).mainActive()
    }

    override fun onItemClicked(tvViewModel: TVViewModel) {
        if (this.isHidden) {
            (activity as? MainActivity)?.switchMainFragment()
            return
        }
        if (itemPosition != tvViewModel.getTV().id) {
            itemPosition = tvViewModel.getTV().id
            tvListViewModel.setItemPosition(itemPosition)
            tvListViewModel.getTVViewModel(itemPosition)?.changed("menu")
        }
        (activity as? MainActivity)?.switchMainFragment()
    }

    fun setPosition() {
        val vm = tvListViewModel.getTVViewModel(itemPosition) ?: return
        setPosition(vm.getRowPosition(), vm.getItemPosition())
    }

    fun setPosition(rowPosition: Int, itemPosition: Int) {
        if (rowPosition >= rowList.size) return
        rowList[rowPosition].post {
            when (val lm = (rowList[rowPosition] as RecyclerView).layoutManager) {
                is GridLayoutManager   -> lm.findViewByPosition(itemPosition)?.requestFocus()
                is LinearLayoutManager -> lm.findViewByPosition(itemPosition)?.requestFocus()
            }
        }
    }

    fun check(tvViewModel: TVViewModel): Boolean {
        val videoUrl = tvViewModel.videoIndex.value?.let { tvViewModel.videoUrl.value?.get(it) }
        if (videoUrl.isNullOrEmpty()) return false
        if (videoUrl == lastVideoUrl) return false
        return true
    }

    fun fragmentReady() {
        if (TVList.isEmpty()) return
        tvListViewModel.getTVViewModel(itemPosition)?.changed("init")
    }

    fun play(itemPosition: Int) {
        view?.post {
            if (itemPosition in 0 until tvListViewModel.size()) {
                this.itemPosition = itemPosition
                tvListViewModel.setItemPosition(itemPosition)
                tvListViewModel.getTVViewModel(itemPosition)?.changed("num")
            } else {
                Toast.makeText(context, "频道不存在", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun prev() {
        view?.post {
            itemPosition = if (itemPosition == 0) tvListViewModel.size() - 1 else itemPosition - 1
            tvListViewModel.setItemPosition(itemPosition)
            tvListViewModel.getTVViewModel(itemPosition)?.changed("prev")
        }
    }

    fun next() {
        view?.post {
            itemPosition = (itemPosition + 1) % tvListViewModel.size()
            tvListViewModel.setItemPosition(itemPosition)
            tvListViewModel.getTVViewModel(itemPosition)?.changed("next")
        }
    }

    /** 全屏状态下左右键：切换当前频道的源 */
    fun switchSource(delta: Int) {
        view?.post {
            tvListViewModel.getTVViewModel(itemPosition)?.switchSource(delta)
        }
    }

    fun shouldHasFocus(tvModel: TVViewModel) =
        tvModel == tvListViewModel.getTVViewModel(itemPosition)

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        if (!hidden) {
            val vm = tvListViewModel.getTVViewModel(itemPosition) ?: return
            for (i in rowList) {
                if (i.tag as Int == vm.getRowPosition()) {
                    val adapter = (i as RecyclerView).adapter as CardAdapter
                    adapter.updateEPG()
                    adapter.focusable = true
                    adapter.toPosition(vm.getItemPosition())
                    break
                }
            }
        } else {
            view?.post {
                for (i in rowList) {
                    ((i as RecyclerView).adapter as CardAdapter).focusable = false
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val TAG = "MainFragment"
    }
}
