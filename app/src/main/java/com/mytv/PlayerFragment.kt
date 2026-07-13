package com.github.mytv

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.SurfaceHolder
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import androidx.annotation.OptIn
import androidx.fragment.app.Fragment
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.github.mytv.databinding.PlayerBinding
import com.github.mytv.models.TVViewModel


class PlayerFragment : Fragment() {

    private var _binding: PlayerBinding? = null
    private var playerView: PlayerView? = null
    private var ijkSurfaceView: android.view.SurfaceView? = null
    private var tvViewModel: TVViewModel? = null
    private val aspectRatio = 16f / 9f

    /** exo 内核实例，仅当 playerType=="exo" 时非空 */
    private var exoPlayer: ExoPlayer? = null
    /** ijk 内核实例，仅当 playerType=="ijk" 时非空 */
    private var ijkEngine: IjkPlayerEngine? = null

    /** 当前生效内核：exo / ijk。可能与 SP 不同（自动 fallback 后） */
    private var currentEngine: String = "exo"

    /** 自动 fallback 标记：本次 url 播放过程中是否已切换过内核，避免无限循环 */
    private var hasAutoSwitchedEngine = false

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = PlayerBinding.inflate(inflater, container, false)

        playerView = _binding!!.playerView
        ijkSurfaceView = _binding!!.ijkSurfaceView

        // ijk 的 SurfaceView 准备好后挂给 IjkPlayerEngine
        ijkSurfaceView?.holder?.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceCreated(holder: SurfaceHolder) {
                ijkEngine?.setSurface(holder.surface)
            }
            override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {}
            override fun surfaceDestroyed(holder: SurfaceHolder) {
                ijkEngine?.setSurface(null)
            }
        })

        playerView?.viewTreeObserver?.addOnGlobalLayoutListener(object :
            ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                playerView!!.viewTreeObserver.removeOnGlobalLayoutListener(this)
                // 初始内核由 SP 决定，延迟到 play() 时再创建
                (activity as MainActivity).fragmentReady(TAG)
            }
        })
        return _binding!!.root
    }

    @OptIn(UnstableApi::class)
    fun play(tvViewModel: TVViewModel) {
        this.tvViewModel = tvViewModel
        val url = tvViewModel.getVideoUrlCurrent()
        if (url.isEmpty()) {
            Log.w(TAG, "play url empty")
            return
        }
        // 切换频道/源：重置自动 fallback 标记
        hasAutoSwitchedEngine = false
        // 重新读 SP 中的内核选择
        val spEngine = SP.playerType
        // 切到目标内核
        ensureEngine(spEngine, url)
    }

    /** 立即应用播放器/解码方式设置变更（不切源，重新起播当前 URL） */
    fun onPlayerConfigChanged() {
        val vm = tvViewModel ?: return
        val url = vm.getVideoUrlCurrent()
        if (url.isEmpty()) return
        // 用户手动改设置，重置 fallback 标记
        hasAutoSwitchedEngine = false
        ensureEngine(SP.playerType, url)
    }

    @OptIn(UnstableApi::class)
    private fun ensureEngine(target: String, url: String) {
        // 释放另一个引擎
        if (target == "ijk") {
            releaseExo()
            switchToIjkView()
            if (ijkEngine == null) {
                buildIjk()
            }
            currentEngine = "ijk"
            ijkEngine?.play(url, null)
        } else {
            releaseIjk()
            switchToExoView()
            if (exoPlayer == null) {
                buildExo()
            }
            currentEngine = "exo"
            exoPlayer?.run {
                setMediaItem(MediaItem.fromUri(url))
                prepare()
                playWhenReady = true
            }
        }
    }

    @OptIn(UnstableApi::class)
    private fun buildExo() {
        val ctx = activity ?: return
        val renderersFactory = DefaultRenderersFactory(ctx).apply {
            // "software" 模式优先扩展渲染器（未来集成 ffmpeg-extension 后生效），
            // "default" 模式不启用扩展
            setExtensionRendererMode(
                if (SP.exoDecoder == "software")
                    DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER
                else
                    DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF
            )
        }
        val mp = ExoPlayer.Builder(ctx).setRenderersFactory(renderersFactory).build()
        mp.playWhenReady = true
        mp.addListener(object : Player.Listener {
            override fun onVideoSizeChanged(videoSize: VideoSize) {
                adjustAspectByViewSize(playerView?.measuredWidth, playerView?.measuredHeight)
            }
            override fun onPlayerError(error: PlaybackException) {
                Log.e(TAG, "ExoPlayer error $error")
                handleEngineError("exo", error.message ?: "exo error")
            }
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying) tvViewModel?.setErrInfo("")
            }
        })
        playerView?.player = mp
        exoPlayer = mp
    }

    private fun buildIjk() {
        val engine = IjkPlayerEngine()
        engine.setDecoder(SP.ijkDecoder)
        // 把当前 SurfaceView 的 surface 立即挂上（如果已经 created）
        ijkSurfaceView?.holder?.surface?.let { engine.setSurface(it) }
        engine.setListener(object : IjkPlayerEngine.Listener {
            override fun onPrepared() {
                engine.start()
                tvViewModel?.setErrInfo("")
            }
            override fun onError(what: Int, extra: Int) {
                Log.e(TAG, "IJK error what=$what extra=$extra")
                handleEngineError("ijk", "error($what,$extra)")
            }
            override fun onCompletion() {}
            override fun onVideoSizeChanged(width: Int, height: Int) {
                adjustAspectByViewSize(ijkSurfaceView?.width, ijkSurfaceView?.height)
            }
            override fun onInfo(what: Int, extra: Int) {}
            override fun onBufferingUpdate(percent: Int) {}
        })
        ijkEngine = engine
    }

    /** 自动 fallback：出错时切到另一个内核重试，仅自动切换一次 */
    private fun handleEngineError(engine: String, errInfo: String) {
        val vm = tvViewModel ?: return
        if (!hasAutoSwitchedEngine) {
            hasAutoSwitchedEngine = true
            val fallback = if (engine == "exo") "ijk" else "exo"
            Log.w(TAG, "auto fallback $engine -> $fallback")
            val url = vm.getVideoUrlCurrent()
            if (url.isNotEmpty()) {
                ensureEngine(fallback, url)
                return
            }
        }
        // 已经 fallback 过，按原流程报错让上层重试
        vm.setErrInfo("播放错误")
        vm.changed("retry")
    }

    private fun switchToExoView() {
        playerView?.visibility = View.VISIBLE
        ijkSurfaceView?.visibility = View.GONE
    }

    private fun switchToIjkView() {
        playerView?.visibility = View.GONE
        ijkSurfaceView?.visibility = View.VISIBLE
    }

    /** 按 playerView / ijkSurfaceView 实际尺寸调整长宽，保持 16:9 */
    private fun adjustAspectByViewSize(width: Int?, height: Int?) {
        if (width == null || height == null || width <= 0 || height <= 0) return
        val ratio = width.toFloat() / height.toFloat()
        val lp = (if (currentEngine == "ijk") ijkSurfaceView else playerView)?.layoutParams ?: return
        if (ratio < aspectRatio) {
            lp.height = (width / aspectRatio).toInt()
        } else if (ratio > aspectRatio) {
            lp.width = (height * aspectRatio).toInt()
        } else {
            return
        }
        (if (currentEngine == "ijk") ijkSurfaceView else playerView)?.layoutParams = lp
    }

    private fun releaseExo() {
        exoPlayer?.let { mp ->
            mp.release()
            exoPlayer = null
            playerView?.player = null
        }
    }

    private fun releaseIjk() {
        ijkEngine?.let { eng ->
            eng.release()
            ijkEngine = null
        }
    }

    override fun onStart() {
        Log.i(TAG, "onStart")
        super.onStart()
        // ExoPlayer 通过 playWhenReady 自动恢复
        // IJK 在 onPause 时只是 pause，回来时需要手动 start
        if (currentEngine == "ijk") ijkEngine?.start()
    }

    override fun onResume() {
        Log.i(TAG, "onResume")
        super.onResume()
    }

    override fun onPause() {
        super.onPause()
        when (currentEngine) {
            "exo" -> if (exoPlayer?.isPlaying == true) exoPlayer?.pause()
            "ijk" -> ijkEngine?.pause()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        releaseExo()
        releaseIjk()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val TAG = "PlayerFragment"
    }
}
