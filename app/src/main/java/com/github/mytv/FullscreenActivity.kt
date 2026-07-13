package com.github.mytv

import android.content.pm.ActivityInfo
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.SurfaceHolder
import android.view.View
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.annotation.OptIn
import androidx.fragment.app.FragmentActivity
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView

class FullscreenActivity : FragmentActivity() {

    private lateinit var playerView: PlayerView
    private lateinit var ijkSurfaceView: android.view.SurfaceView
    private lateinit var controls: LinearLayout
    private lateinit var loading: ProgressBar
    private lateinit var channelNameView: TextView
    private lateinit var btnPlayPause: ImageButton
    private lateinit var btnRetry: ImageButton
    private lateinit var btnExit: ImageButton

    /** exo 内核实例，仅当 currentEngine=="exo" 时非空 */
    private var player: ExoPlayer? = null
    /** ijk 内核实例，仅当 currentEngine=="ijk" 时非空 */
    private var ijkEngine: IjkPlayerEngine? = null

    /** 当前生效内核：exo / ijk */
    private var currentEngine: String = "exo"

    /** 自动 fallback 标记 */
    private var hasAutoSwitchedEngine = false

    private var currentUrl: String = ""

    private val handler = Handler(Looper.getMainLooper())
    private val hideControlsRunnable = Runnable { hideControls() }
    private var isPlaying = true

    companion object {
        const val EXTRA_URL = "extra_url"
        const val EXTRA_CHANNEL_NAME = "extra_channel_name"
        private const val TAG = "FullscreenActivity"
    }

    @OptIn(UnstableApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        )

        setContentView(R.layout.activity_fullscreen)

        playerView = findViewById(R.id.fullscreen_player_view)
        ijkSurfaceView = findViewById(R.id.ijk_surface_view)
        controls = findViewById(R.id.fullscreen_controls)
        loading = findViewById(R.id.fullscreen_loading)
        channelNameView = findViewById(R.id.tv_fs_channel_name)
        btnPlayPause = findViewById(R.id.btn_fs_play_pause)
        btnRetry = findViewById(R.id.btn_fs_retry)
        btnExit = findViewById(R.id.btn_fs_exit)

        val url = intent.getStringExtra(EXTRA_URL) ?: ""
        val channelName = intent.getStringExtra(EXTRA_CHANNEL_NAME) ?: ""

        channelNameView.text = channelName
        channelNameView.visibility = View.VISIBLE
        handler.postDelayed({ channelNameView.visibility = View.GONE }, 3000)

        currentUrl = url

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

        ensureEngine(SP.playerType, url)

        // 点击显示/隐藏控制栏
        val toggleControls = View.OnClickListener {
            if (controls.visibility == View.VISIBLE) {
                hideControls()
            } else {
                showControls()
            }
        }
        playerView.setOnClickListener(toggleControls)
        ijkSurfaceView.setOnClickListener(toggleControls)

        btnPlayPause.setOnClickListener {
            if (currentEngine == "ijk") {
                if (ijkEngine?.isPlaying() == true) {
                    ijkEngine?.pause()
                    isPlaying = false
                    btnPlayPause.setImageResource(android.R.drawable.ic_media_play)
                } else {
                    ijkEngine?.start()
                    isPlaying = true
                    btnPlayPause.setImageResource(android.R.drawable.ic_media_pause)
                }
            } else {
                if (player?.isPlaying == true) {
                    player?.pause()
                    isPlaying = false
                    btnPlayPause.setImageResource(android.R.drawable.ic_media_play)
                } else {
                    player?.play()
                    isPlaying = true
                    btnPlayPause.setImageResource(android.R.drawable.ic_media_pause)
                }
            }
            scheduleHideControls()
        }

        btnRetry.setOnClickListener {
            hasAutoSwitchedEngine = false
            ensureEngine(SP.playerType, currentUrl)
            scheduleHideControls()
        }

        btnExit.setOnClickListener {
            finish()
        }
    }

    @OptIn(UnstableApi::class)
    private fun ensureEngine(target: String, url: String) {
        loading.visibility = View.VISIBLE
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
        mp.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying) loading.visibility = View.GONE
            }
            override fun onPlayerError(error: PlaybackException) {
                handleEngineError("exo", error.message ?: "exo error")
            }
        })
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
                loading.visibility = View.GONE
                isPlaying = true
                btnPlayPause.setImageResource(android.R.drawable.ic_media_pause)
            }
            override fun onError(what: Int, extra: Int) {
                handleEngineError("ijk", "error($what,$extra)")
            }
            override fun onCompletion() {}
            override fun onVideoSizeChanged(width: Int, height: Int) {}
            override fun onInfo(what: Int, extra: Int) {}
            override fun onBufferingUpdate(percent: Int) {}
        })
        ijkEngine = engine
    }

    /** 自动 fallback：出错时切到另一个内核重试，仅自动切换一次 */
    private fun handleEngineError(engine: String, errInfo: String) {
        if (!hasAutoSwitchedEngine) {
            hasAutoSwitchedEngine = true
            val fallback = if (engine == "exo") "ijk" else "exo"
            Log.w(TAG, "auto fallback $engine -> $fallback")
            if (currentUrl.isNotEmpty()) {
                ensureEngine(fallback, currentUrl)
                return
            }
        }
        loading.visibility = View.GONE
    }

    private fun switchToExoView() {
        playerView.visibility = View.VISIBLE
        ijkSurfaceView.visibility = View.GONE
    }

    private fun switchToIjkView() {
        playerView.visibility = View.GONE
        ijkSurfaceView.visibility = View.VISIBLE
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

    private fun showControls() {
        controls.visibility = View.VISIBLE
        scheduleHideControls()
    }

    private fun hideControls() {
        controls.visibility = View.GONE
    }

    private fun scheduleHideControls() {
        handler.removeCallbacks(hideControlsRunnable)
        handler.postDelayed(hideControlsRunnable, 3000)
    }

    override fun onPause() {
        super.onPause()
        when (currentEngine) {
            "exo" -> player?.pause()
            "ijk" -> ijkEngine?.pause()
        }
    }

    override fun onResume() {
        super.onResume()
        if (isPlaying) {
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
}
