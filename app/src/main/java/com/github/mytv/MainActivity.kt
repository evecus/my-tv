package com.github.mytv

import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.SurfaceHolder
import android.view.View
import android.view.WindowManager
import android.widget.*
import androidx.annotation.OptIn
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.github.mytv.models.TVList
import com.github.mytv.models.TVListViewModel
import com.github.mytv.models.TVViewModel
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch

class MainActivity : FragmentActivity(), OnSharedPreferenceChangeListener {

    private lateinit var playerView: PlayerView
    private lateinit var ijkSurfaceView: android.view.SurfaceView
    private lateinit var loadingOverlay: FrameLayout
    private lateinit var errorOverlay: LinearLayout
    private lateinit var errorMsg: TextView
    private lateinit var portraitControls: LinearLayout
    private lateinit var btnPlayPause: ImageButton
    private lateinit var btnRetry: ImageButton
    private lateinit var btnFullscreen: ImageButton
    private lateinit var tvChannelName: TextView
    private lateinit var sourceRow: LinearLayout
    private lateinit var tvSourceLabel: TextView
    // 源切换改为 TextView（显示 < > 文字箭头）
    private lateinit var btnSourcePrev: TextView
    private lateinit var btnSourceNext: TextView
    private lateinit var btnSettings: ImageButton
    private lateinit var groupList: ListView
    private lateinit var channelList: ListView

    /** exo 内核实例，仅当 currentEngine=="exo" 时非空 */
    private var player: ExoPlayer? = null
    /** ijk 内核实例，仅当 currentEngine=="ijk" 时非空 */
    private var ijkEngine: IjkPlayerEngine? = null

    /** 当前生效内核：exo / ijk。可能与 SP 不同（自动 fallback 后） */
    private var currentEngine: String = "exo"

    /** 自动 fallback 标记：本次 url 播放过程中是否已切换过内核，避免无限循环 */
    private var hasAutoSwitchedEngine = false

    /** IJK 视频尺寸（用于保持画面比例） */
    private var ijkVideoWidth = 0
    private var ijkVideoHeight = 0

    private var isPlayerPlaying = true

    /** 是否已经开始过播放（用于区分冷启动黑屏与用户手动选台） */
    private var hasStartedPlayback = false

    private var tvListViewModel = TVListViewModel()
    private var currentPosition = 0
    private var currentGroupIndex = 0
    private val groupNames = mutableListOf<String>()
    private val groupAdapter by lazy { GroupAdapter() }
    private var channelAdapter: ChannelAdapter? = null

    private val handler = Handler(Looper.getMainLooper())
    private val hideControlsRunnable = Runnable { portraitControls.visibility = View.GONE }

    private val isTablet: Boolean
        get() = resources.configuration.smallestScreenWidthDp >= 600

    @OptIn(UnstableApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(R.layout.activity_main)

        SP.setOnSharedPreferenceChangeListener(this)

        bindViews()
        setupPlayer()
        setupListeners()
        setupGroupList()

        syncTime()
        TVList.load(this)
        buildChannelData()

        if (SP.lastSpeedtest == 0L || SP.autoSpeedtest) {
            SpeedtestDialogFragment.show(this)
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        setContentView(R.layout.activity_main)
        bindViews()
        setupPlayer()
        setupListeners()
        setupGroupList()
        buildChannelData()
    }

    override fun onSharedPreferenceChanged(key: String) {
        when (key) {
            SP.KEY_PLAYER_TYPE, SP.KEY_IJK_DECODER, SP.KEY_EXO_DECODER -> {
                onPlayerConfigChanged()
            }
        }
    }

    /** 播放器配置变更后立即重新起播当前 URL */
    private fun onPlayerConfigChanged() {
        if (!hasStartedPlayback) return
        val vm = tvListViewModel.getTVViewModel(currentPosition) ?: return
        val url = vm.getVideoUrlCurrent()
        if (url.isEmpty()) return
        hasAutoSwitchedEngine = false
        loadingOverlay.visibility = View.VISIBLE
        errorOverlay.visibility = View.GONE
        ensureEngine(SP.playerType, url)
    }

    private fun bindViews() {
        playerView       = findViewById(R.id.player_view)
        ijkSurfaceView   = findViewById(R.id.ijk_surface_view)
        loadingOverlay   = findViewById(R.id.loading_overlay)
        errorOverlay     = findViewById(R.id.error_overlay)
        errorMsg         = findViewById(R.id.error_msg)
        portraitControls = findViewById(R.id.portrait_controls)
        btnPlayPause     = findViewById(R.id.btn_play_pause)
        btnRetry         = findViewById(R.id.btn_retry)
        btnFullscreen    = findViewById(R.id.btn_fullscreen)
        tvChannelName    = findViewById(R.id.tv_channel_name)
        sourceRow        = findViewById(R.id.source_row)
        tvSourceLabel    = findViewById(R.id.tv_source_label)
        btnSourcePrev    = findViewById(R.id.btn_source_prev)   // TextView
        btnSourceNext    = findViewById(R.id.btn_source_next)   // TextView
        btnSettings      = findViewById(R.id.btn_settings)
        groupList        = findViewById(R.id.group_list)
        channelList      = findViewById(R.id.channel_list)
    }

    @OptIn(UnstableApi::class)
    private fun setupPlayer() {
        if (player == null) {
            buildExo()
        } else {
            playerView.player = player
        }

        // IJK SurfaceView 回调
        ijkSurfaceView.holder.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceCreated(holder: SurfaceHolder) {
                ijkEngine?.setSurface(holder.surface)
            }
            override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {}
            override fun surfaceDestroyed(holder: SurfaceHolder) {
                ijkEngine?.setSurface(null)
            }
        })

        // 恢复当前引擎对应的视图可见性
        if (currentEngine == "ijk") {
            switchToIjkView()
        } else {
            switchToExoView()
        }
    }

    @OptIn(UnstableApi::class)
    private fun buildExo() {
        val renderersFactory = DefaultRenderersFactory(this).apply {
            setExtensionRendererMode(
                if (SP.exoDecoder == "software")
                    DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER
                else
                    DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF
            )
        }
        val mp = ExoPlayer.Builder(this).setRenderersFactory(renderersFactory).build()
        mp.addListener(playerListener)
        playerView.player = mp
        player = mp
    }

    private fun buildIjk() {
        val engine = IjkPlayerEngine()
        engine.setDecoder(SP.ijkDecoder)
        ijkSurfaceView.holder.surface?.let { engine.setSurface(it) }
        engine.setListener(object : IjkPlayerEngine.Listener {
            override fun onPrepared() {
                engine.start()
                loadingOverlay.visibility = View.GONE
                errorOverlay.visibility = View.GONE
                isPlayerPlaying = true
                btnPlayPause.setImageResource(R.drawable.ic_pause)
            }
            override fun onError(what: Int, extra: Int) {
                handleEngineError("ijk", "error($what,$extra)")
            }
            override fun onCompletion() {}
            override fun onVideoSizeChanged(width: Int, height: Int) {
                ijkVideoWidth = width
                ijkVideoHeight = height
                adjustIjkAspectRatio()
            }
            override fun onInfo(what: Int, extra: Int) {}
            override fun onBufferingUpdate(percent: Int) {}
        })
        ijkEngine = engine
    }

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (isPlaying) {
                loadingOverlay.visibility = View.GONE
                errorOverlay.visibility   = View.GONE
            }
        }
        override fun onPlayerError(error: PlaybackException) {
            handleEngineError("exo", error.message ?: "exo error")
        }
        override fun onPlaybackStateChanged(state: Int) {
            if (state == Player.STATE_BUFFERING) {
                loadingOverlay.visibility = View.VISIBLE
                errorOverlay.visibility   = View.GONE
            }
        }
    }

    @OptIn(UnstableApi::class)
    private fun ensureEngine(target: String, url: String) {
        if (target == "ijk") {
            releaseExo()
            switchToIjkView()
            if (ijkEngine == null) {
                buildIjk()
            }
            currentEngine = "ijk"
            ijkEngine?.play(url)
        } else {
            releaseIjk()
            switchToExoView()
            if (player == null) {
                buildExo()
            }
            currentEngine = "exo"
            player?.run {
                setMediaItem(MediaItem.fromUri(url))
                prepare()
                playWhenReady = true
            }
        }
    }

    /** 自动 fallback：出错时切到另一个内核重试，仅自动切换一次 */
    private fun handleEngineError(engine: String, errInfo: String) {
        if (!hasAutoSwitchedEngine) {
            hasAutoSwitchedEngine = true
            val fallback = if (engine == "exo") "ijk" else "exo"
            val vm = tvListViewModel.getTVViewModel(currentPosition) ?: return
            val url = vm.getVideoUrlCurrent()
            if (url.isNotEmpty()) {
                ensureEngine(fallback, url)
                return
            }
        }
        // 已经 fallback 过，显示错误
        loadingOverlay.visibility = View.GONE
        errorOverlay.visibility = View.VISIBLE
        errorMsg.text = "播放错误，请尝试换线路"
    }

    private fun switchToExoView() {
        playerView.visibility = View.VISIBLE
        ijkSurfaceView.visibility = View.GONE
    }

    private fun switchToIjkView() {
        playerView.visibility = View.GONE
        ijkSurfaceView.visibility = View.VISIBLE
        ijkSurfaceView.post { adjustIjkAspectRatio() }
    }

    /** 按 IJK 视频实际比例调整 SurfaceView 尺寸，保持 fit（不拉伸） */
    private fun adjustIjkAspectRatio() {
        if (ijkVideoWidth <= 0 || ijkVideoHeight <= 0) return
        val container = ijkSurfaceView.parent as? FrameLayout ?: return
        val cw = container.width
        val ch = container.height
        if (cw <= 0 || ch <= 0) return

        val videoRatio = ijkVideoWidth.toFloat() / ijkVideoHeight
        val containerRatio = cw.toFloat() / ch

        val lp = ijkSurfaceView.layoutParams as FrameLayout.LayoutParams
        if (videoRatio > containerRatio) {
            // 视频更宽 → 宽撑满，高度按比例缩放
            lp.width = cw
            lp.height = (cw / videoRatio).toInt()
        } else {
            // 视频更高 → 高撑满，宽度按比例缩放
            lp.width = (ch * videoRatio).toInt()
            lp.height = ch
        }
        lp.gravity = Gravity.CENTER
        ijkSurfaceView.layoutParams = lp
    }

    private fun releaseExo() {
        player?.let { mp ->
            mp.release()
            player = null
            playerView.player = null
        }
    }

    private fun releaseIjk() {
        ijkEngine?.let { eng ->
            eng.release()
            ijkEngine = null
        }
    }

    private fun setupListeners() {
        // 播放区点击显示/隐藏控制栏（仅在已开始播放时响应）
        playerView.setOnClickListener {
            if (!hasStartedPlayback) return@setOnClickListener
            if (portraitControls.visibility == View.VISIBLE) {
                portraitControls.visibility = View.GONE
            } else {
                portraitControls.visibility = View.VISIBLE
                scheduleHideControls()
            }
        }
        // IJK 播放时 playerView 被隐藏，需要给 ijkSurfaceView 绑定同样的点击逻辑
        ijkSurfaceView.setOnClickListener {
            if (!hasStartedPlayback) return@setOnClickListener
            if (portraitControls.visibility == View.VISIBLE) {
                portraitControls.visibility = View.GONE
            } else {
                portraitControls.visibility = View.VISIBLE
                scheduleHideControls()
            }
        }
        loadingOverlay.setOnClickListener { }

        btnPlayPause.setOnClickListener {
            if (currentEngine == "ijk") {
                if (ijkEngine?.isPlaying() == true) {
                    ijkEngine?.pause()
                    isPlayerPlaying = false
                    btnPlayPause.setImageResource(R.drawable.ic_play)
                } else {
                    ijkEngine?.start()
                    isPlayerPlaying = true
                    btnPlayPause.setImageResource(R.drawable.ic_pause)
                }
            } else {
                if (player?.isPlaying == true) {
                    player?.pause()
                    isPlayerPlaying = false
                    btnPlayPause.setImageResource(R.drawable.ic_play)
                } else {
                    player?.play()
                    isPlayerPlaying = true
                    btnPlayPause.setImageResource(R.drawable.ic_pause)
                }
            }
            scheduleHideControls()
        }

        btnRetry.setOnClickListener {
            tvListViewModel.getTVViewModel(currentPosition)?.let { playChannel(it) }
            scheduleHideControls()
        }

        btnFullscreen.setOnClickListener {
            val vm = tvListViewModel.getTVViewModel(currentPosition) ?: return@setOnClickListener
            val url = vm.getVideoUrlCurrent()
            if (url.isEmpty()) return@setOnClickListener
            startActivity(Intent(this, FullscreenActivity::class.java).apply {
                putExtra(FullscreenActivity.EXTRA_URL, url)
                putExtra(FullscreenActivity.EXTRA_CHANNEL_NAME, vm.getTV().title)
            })
        }

        btnSourcePrev.setOnClickListener {
            tvListViewModel.getTVViewModel(currentPosition)?.switchSource(-1)
        }
        btnSourceNext.setOnClickListener {
            tvListViewModel.getTVViewModel(currentPosition)?.switchSource(+1)
        }

        btnSettings.setOnClickListener {
            if (isTablet) {
                SettingsDialogFragment.show(this)
            } else {
                SettingsBottomSheet.show(this)
            }
        }
    }

    private fun setupGroupList() {
        groupList.adapter = groupAdapter
        groupList.setOnItemClickListener { _, _, position, _ ->
            currentGroupIndex = position
            groupAdapter.setSelected(position)
            showGroupChannels(position)
        }
    }

    fun buildChannelData() {
        tvListViewModel = TVListViewModel()
        groupNames.clear()
        groupAdapter.clear()

        if (TVList.isEmpty()) return

        for ((group, channels) in TVList.list) {
            groupNames.add(group)
            for ((idx, tv) in channels.withIndex()) {
                val vm = TVViewModel(tv)
                vm.setRowPosition(groupNames.size - 1)
                vm.setItemPosition(idx)
                tvListViewModel.addTVViewModel(vm)
            }
            tvListViewModel.maxNum.add(channels.size)
        }

        groupAdapter.setItems(groupNames)

        // 恢复上次选中位置，但不自动播放
        currentPosition = SP.itemPosition.coerceIn(0, (tvListViewModel.size() - 1).coerceAtLeast(0))
        currentGroupIndex = tvListViewModel.getTVViewModel(currentPosition)?.getRowPosition() ?: 0
        groupAdapter.setSelected(currentGroupIndex)
        showGroupChannels(currentGroupIndex)

        // 仅注册观察者，不触发 changed("init")，避免冷启动自动播放
        attachObservers()
    }

    private fun attachObservers() {
        tvListViewModel.tvListViewModel.value?.forEach { vm ->
            vm.change.observe(this) { from ->
                if (from != null && vm.getTV().id == currentPosition) {
                    val url = vm.getVideoUrlCurrent().ifEmpty {
                        vm.getTV().videoUrl.firstOrNull() ?: return@observe
                    }
                    if (from != "source") vm.addVideoUrl(url)
                    vm.allReady()
                }
            }
            vm.ready.observe(this) {
                if (vm.getTV().id == currentPosition && vm.ready.value == true) {
                    playChannel(vm)
                }
            }
            vm.sourceChanged.observe(this) { pair ->
                if (pair != null && vm.getTV().id == currentPosition) {
                    updateSourceLabel(pair.first, pair.second)
                    if (pair.first > 0) playChannel(vm)
                }
            }
        }
    }

    private fun showGroupChannels(groupIndex: Int) {
        if (groupIndex >= groupNames.size) return
        val group = groupNames[groupIndex]
        val channels = TVList.list[group] ?: return

        var startId = 0
        for (i in 0 until groupIndex) {
            startId += tvListViewModel.maxNum.getOrElse(i) { 0 }
        }

        val vms = channels.indices.mapNotNull { idx ->
            tvListViewModel.getTVViewModel(startId + idx)
        }
        channelAdapter = ChannelAdapter(vms)
        channelList.adapter = channelAdapter

        val currentVm = tvListViewModel.getTVViewModel(currentPosition)
        if (currentVm?.getRowPosition() == groupIndex) {
            val localIdx = currentVm.getItemPosition()
            channelAdapter?.setSelected(localIdx)
            channelList.post { channelList.setSelection(localIdx) }
        } else {
            channelAdapter?.setSelected(-1)
        }

        channelList.setOnItemClickListener { _, _, position, _ ->
            val clickedVm = channelAdapter?.getViewModel(position) ?: return@setOnItemClickListener
            channelAdapter?.setSelected(position)
            val newId = clickedVm.getTV().id

            // ── 修复 sourceIndex bug：切换频道时重置源索引到 0 ──
            clickedVm.getTV().sourceIndex = 0

            hasStartedPlayback = true   // 用户手动点台，允许播放

            if (newId != currentPosition) {
                currentPosition = newId
                tvListViewModel.setItemPosition(currentPosition)
                clickedVm.changed("menu")
                attachObservers()
            } else {
                // 同一频道再次点击也触发播放（例如重试）
                playChannel(clickedVm)
            }
        }
    }

    @OptIn(UnstableApi::class)
    private fun playChannel(vm: TVViewModel) {
        val url = vm.getVideoUrlCurrent()
        if (url.isEmpty()) return

        tvChannelName.text = vm.getTV().title
        updateSourceUI(vm)

        loadingOverlay.visibility = View.VISIBLE
        errorOverlay.visibility   = View.GONE

        // 切换频道/源：重置自动 fallback 标记
        hasAutoSwitchedEngine = false
        // 重新读 SP 中的内核选择
        ensureEngine(SP.playerType, url)

        isPlayerPlaying = true
        btnPlayPause.setImageResource(R.drawable.ic_pause)
    }

    private fun updateSourceUI(vm: TVViewModel) {
        val total = vm.getTV().videoUrl.size
        if (total > 1) {
            sourceRow.visibility = View.VISIBLE
            // ── 修复：使用 sourceIndex 而非 videoIndex LiveData ──
            val idx = vm.getTV().sourceIndex
            tvSourceLabel.text = "线路 ${idx + 1}/$total"
        } else {
            sourceRow.visibility = View.GONE
        }
    }

    private fun updateSourceLabel(current: Int, total: Int) {
        if (total <= 1) { sourceRow.visibility = View.GONE; return }
        sourceRow.visibility = View.VISIBLE
        tvSourceLabel.text = when (current) {
            0  -> "已是第一条线路"
            -1 -> "已是最后一条线路"
            else -> "线路 $current/$total"
        }
    }

    private fun scheduleHideControls() {
        handler.removeCallbacks(hideControlsRunnable)
        handler.postDelayed(hideControlsRunnable, 3000)
    }

    fun reloadChannels() {
        TVList.load(this)
        buildChannelData()
        Toast.makeText(this, "频道列表已更新", Toast.LENGTH_SHORT).show()
    }

    private fun syncTime() {
        lifecycleScope.launch(Dispatchers.IO) {
            val job = async(start = CoroutineStart.LAZY) { Utils.init() }
            job.start()
        }
    }

    override fun onPause() {
        super.onPause()
        when (currentEngine) {
            "exo" -> if (player?.isPlaying == true) player?.pause()
            "ijk" -> ijkEngine?.pause()
        }
    }

    override fun onResume() {
        super.onResume()
        if (isPlayerPlaying && hasStartedPlayback) {
            when (currentEngine) {
                "exo" -> player?.play()
                "ijk" -> ijkEngine?.start()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        releaseExo()
        releaseIjk()
    }

    // ── GroupAdapter ──────────────────────────────────────────────

    inner class GroupAdapter : BaseAdapter() {
        private val items = mutableListOf<String>()
        private var selectedPos = 0

        fun setItems(list: List<String>) { items.clear(); items.addAll(list); notifyDataSetChanged() }
        fun clear() { items.clear(); notifyDataSetChanged() }
        fun setSelected(pos: Int) { selectedPos = pos; notifyDataSetChanged() }

        override fun getCount() = items.size
        override fun getItem(pos: Int): Any = items[pos]
        override fun getItemId(pos: Int) = pos.toLong()

        override fun getView(pos: Int, convertView: View?, parent: android.view.ViewGroup): View {
            val tv = (convertView as? TextView)
                ?: layoutInflater.inflate(R.layout.item_group, parent, false) as TextView
            tv.text = items[pos]
            if (pos == selectedPos) {
                tv.setBackgroundResource(R.drawable.bg_group_selected)
                tv.setTextColor(getColor(R.color.selected_text))
                tv.setTypeface(null, android.graphics.Typeface.BOLD)
            } else {
                tv.setBackgroundResource(android.R.color.transparent)
                tv.setTextColor(getColor(R.color.on_surface_secondary))
                tv.setTypeface(null, android.graphics.Typeface.NORMAL)
            }
            return tv
        }
    }

    // ── ChannelAdapter ────────────────────────────────────────────

    inner class ChannelAdapter(private val items: List<TVViewModel>) : BaseAdapter() {
        private var selectedPos = -1

        fun setSelected(pos: Int) { selectedPos = pos; notifyDataSetChanged() }
        fun getViewModel(pos: Int): TVViewModel? = items.getOrNull(pos)

        override fun getCount() = items.size
        override fun getItem(pos: Int): Any = items[pos]
        override fun getItemId(pos: Int) = pos.toLong()

        override fun getView(pos: Int, convertView: View?, parent: android.view.ViewGroup): View {
            val row = convertView
                ?: layoutInflater.inflate(R.layout.item_channel, parent, false)
            val vm = items[pos]
            val tv = vm.getTV()
            val numView  = row.findViewById<TextView>(R.id.tv_channel_num)
            val nameView = row.findViewById<TextView>(R.id.tv_channel_name)
            numView.text  = "${tv.id + 1}"
            nameView.text = tv.title

            if (pos == selectedPos) {
                row.setBackgroundResource(R.drawable.bg_channel_selected)
                numView.setTextColor(getColor(R.color.selected_text))
                nameView.setTextColor(getColor(R.color.selected_text))
                nameView.setTypeface(null, android.graphics.Typeface.BOLD)
            } else {
                row.setBackgroundResource(android.R.color.transparent)
                numView.setTextColor(getColor(R.color.on_surface_secondary))
                nameView.setTextColor(getColor(R.color.on_surface))
                nameView.setTypeface(null, android.graphics.Typeface.NORMAL)
            }
            return row
        }
    }
}
