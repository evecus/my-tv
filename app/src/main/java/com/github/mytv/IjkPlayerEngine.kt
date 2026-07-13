package com.github.mytv

import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Surface
import tv.danmaku.ijk.media.player.IMediaPlayer
import tv.danmaku.ijk.media.player.IjkLibLoader
import tv.danmaku.ijk.media.player.IjkMediaPlayer

/**
 * IJK 播放器封装。
 *
 * - 解码方式：auto / hard / soft
 * - 直播调优 option 借鉴 TVBoxOS：max_cached_duration=300, flush_packets=1, min-frames=1, threads=1
 * - 回调统一通过 [Listener] 派发到主线程
 */
class IjkPlayerEngine {

    interface Listener {
        /** prepared 完成，可调用 start */
        fun onPrepared()
        /** 播放出错 */
        fun onError(what: Int, extra: Int)
        /** 播放完成 */
        fun onCompletion()
        /** 视频尺寸变化 */
        fun onVideoSizeChanged(width: Int, height: Int)
        /** 缓冲开始/结束、渲染开始等 */
        fun onInfo(what: Int, extra: Int)
        /** 缓冲百分比 0-100 */
        fun onBufferingUpdate(percent: Int)
    }

    private var player: IjkMediaPlayer? = null
    private var surface: Surface? = null
    private var listener: Listener? = null
    private var decoder: String = "auto"
    private val mainHandler = Handler(Looper.getMainLooper())

    fun setListener(l: Listener) { listener = l }

    fun setSurface(s: Surface?) { surface = s }

    /** 设置解码方式：auto / hard / soft */
    fun setDecoder(decoder: String) {
        this.decoder = when (decoder) {
            "hard", "soft", "auto" -> decoder
            else -> "auto"
        }
    }

    /** 开始播放一个 URL */
    fun play(url: String, headers: Map<String, String>? = null) {
        releaseInternal()
        val mp = IjkMediaPlayer()
        player = mp
        mp.setOnPreparedListener { mainHandler.post { listener?.onPrepared() } }
        mp.setOnErrorListener { _, what, extra ->
            mainHandler.post { listener?.onError(what, extra) }
            true
        }
        mp.setOnCompletionListener { mainHandler.post { listener?.onCompletion() } }
        mp.setOnVideoSizeChangedListener { _, w, h, _, _ ->
            if (w > 0 && h > 0) mainHandler.post { listener?.onVideoSizeChanged(w, h) }
        }
        mp.setOnInfoListener { _, what, extra ->
            mainHandler.post { listener?.onInfo(what, extra) }
            true
        }
        mp.setOnBufferingUpdateListener { _, percent ->
            mainHandler.post { listener?.onBufferingUpdate(percent) }
        }
        mp.setOnNativeInvokeListener { _, _ -> true }

        // 日志静默，避免 release 包刷屏
        IjkMediaPlayer.native_setLogLevel(IjkMediaPlayer.IJK_LOG_SILENT)

        applyDecoderOptions(mp)
        applyLiveOptions(mp)
        applyHeaders(mp, headers)

        surface?.let { mp.setSurface(it) }

        try {
            mp.setDataSource(url)
            mp.prepareAsync()
        } catch (t: Throwable) {
            Log.e(TAG, "setDataSource/prepareAsync failed", t)
            mainHandler.post { listener?.onError(-1, -1) }
        }
    }

    fun start() {
        try { player?.start() } catch (e: IllegalStateException) { Log.e(TAG, "start", e) }
    }

    fun pause() {
        try { player?.pause() } catch (e: IllegalStateException) { Log.e(TAG, "pause", e) }
    }

    fun stop() {
        try { player?.stop() } catch (e: IllegalStateException) { Log.e(TAG, "stop", e) }
    }

    fun release() {
        releaseInternal()
    }

    fun seekTo(ms: Long) {
        try { player?.seekTo(ms) } catch (e: IllegalStateException) { Log.e(TAG, "seekTo", e) }
    }

    fun setVolume(vol: Float) {
        try { player?.setVolume(vol, vol) } catch (e: IllegalStateException) { Log.e(TAG, "setVolume", e) }
    }

    fun isPlaying(): Boolean = try { player?.isPlaying == true } catch (e: IllegalStateException) { false }

    fun getCurrentPosition(): Long = try { player?.currentPosition ?: 0L } catch (e: IllegalStateException) { 0L }

    fun getDuration(): Long = try { player?.duration ?: 0L } catch (e: IllegalStateException) { 0L }

    private fun releaseInternal() {
        val mp = player ?: return
        try {
            mp.setOnPreparedListener(null)
            mp.setOnErrorListener(null)
            mp.setOnCompletionListener(null)
            mp.setOnVideoSizeChangedListener(null)
            mp.setOnInfoListener(null)
            mp.setOnBufferingUpdateListener(null)
            mp.setOnNativeInvokeListener(null)
            mp.release()
        } catch (t: Throwable) {
            Log.e(TAG, "release", t)
        } finally {
            player = null
        }
    }

    /** 应用解码方式相关 option */
    private fun applyDecoderOptions(mp: IjkMediaPlayer) {
        when (decoder) {
            "hard" -> {
                // 强制硬解
                mp.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "mediacodec", 1)
                mp.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "mediacodec-auto-rotate", 1)
                mp.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "mediacodec-handle-resolution-change", 1)
            }
            "soft" -> {
                // 强制软解
                mp.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "mediacodec", 0)
                mp.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "mediacodec-auto-rotate", 0)
                mp.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "mediacodec-handle-resolution-change", 0)
            }
            else -> {
                // auto：交给 ijk 自行判断
                mp.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "mediacodec", 1)
                mp.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "mediacodec-auto-rotate", 1)
                mp.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "mediacodec-handle-resolution-change", 1)
            }
        }
    }

    /** 直播调优 option，借鉴 TVBoxOS */
    private fun applyLiveOptions(mp: IjkMediaPlayer) {
        mp.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "max_cached_duration", 300)
        mp.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "flush_packets", 1)
        mp.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "min-frames", 1)
        mp.setOption(IjkMediaPlayer.OPT_CATEGORY_CODEC, "threads", "1")
        mp.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "dns_cache_clear", 1)
        mp.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "dns_cache_timeout", -1)
        mp.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "safe", 0)
        mp.setOption(
            IjkMediaPlayer.OPT_CATEGORY_FORMAT,
            "protocol_whitelist",
            "ijkio,ffio,async,cache,crypto,file,dash,http,https,ijkhttphook,ijkinject,ijklivehook,ijklongurl,ijksegment,ijktcphook,pipe,rtp,tcp,tls,udp,ijkurlhook,data"
        )
    }

    /** 处理 UA 与其他 headers */
    private fun applyHeaders(mp: IjkMediaPlayer, headers: Map<String, String>?) {
        if (headers.isNullOrEmpty()) {
            mp.setOption(
                IjkMediaPlayer.OPT_CATEGORY_FORMAT,
                "user_agent",
                DEFAULT_USER_AGENT
            )
            return
        }
        val ua = headers.entries.firstOrNull { it.key.equals("User-Agent", true) }?.value
        mp.setOption(
            IjkMediaPlayer.OPT_CATEGORY_FORMAT,
            "user_agent",
            ua?.takeIf { it.isNotBlank() } ?: DEFAULT_USER_AGENT
        )
        // 拼接其余 headers
        val sb = StringBuilder()
        headers.entries.forEach { (k, v) ->
            if (k.equals("User-Agent", true)) return@forEach
            sb.append(k).append(": ").append(v).append("\r\n")
        }
        if (sb.isNotEmpty()) {
            mp.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "headers", sb.toString())
        }
    }

    companion object {
        private const val TAG = "IjkPlayerEngine"
        private const val DEFAULT_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 12) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

        @Volatile
        private var libLoaded = false

        /**
         * 预加载 ijk 原生库。失败不会抛出，调用方应自行判断是否禁用 ijk 内核。
         * 返回 true 表示加载成功（或之前已加载）。
         */
        fun loadLibrariesOnce(): Boolean {
            if (libLoaded) return true
            synchronized(this) {
                if (libLoaded) return true
                var allOk = true
                val loader = IjkLibLoader { name ->
                    try {
                        System.loadLibrary(name)
                    } catch (t: Throwable) {
                        Log.e(TAG, "loadLibrary $name failed", t)
                        allOk = false
                        // 抛出去让 IjkMediaPlayer.loadLibrariesOnce 内部 catch，剩余 lib 不会继续加载
                        throw t
                    }
                }
                try {
                    IjkMediaPlayer.loadLibrariesOnce(loader)
                } catch (t: Throwable) {
                    Log.e(TAG, "loadLibrariesOnce failed", t)
                }
                libLoaded = allOk
                return allOk
            }
        }
    }
}
