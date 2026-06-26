package com.github.mytv

import android.content.Context
import tv.danmaku.ijk.media.player.IjkMediaPlayer
import xyz.doikki.videoplayer.ijk.IjkPlayer
import xyz.doikki.videoplayer.player.PlayerFactory

class HardwareIjkPlayer(context: Context) : IjkPlayer(context) {

    override fun setOptions() {
        // ── 音频输出：关闭 OpenSL ES，走系统 AudioTrack ─────────
        // OpenSL ES 请求 DEEP_BUFFER flag，该设备 HAL 只支持 PRIMARY
        mMediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "opensles", 0)

        // ── 音频重采样：解决采样率/声道不匹配导致的静音 ──────────
        mMediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "soundtouch", 1)

        // ── 硬解 ─────────────────────────────────────────────────
        mMediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "mediacodec", 1)
        mMediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "mediacodec-auto-rotate", 1)
        mMediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "mediacodec-handle-resolution-change", 1)
        mMediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "mediacodec-hevc", 1)

        // ── 视频渲染格式（YV12） ──────────────────────────────────
        mMediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "overlay-format", 842225234L)

        // ── 准备好立即开始播放 ────────────────────────────────────
        mMediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "start-on-prepared", 1)

        // ── 不丢帧（framedrop=0 优先保证音频连续性） ─────────────
        mMediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "framedrop", 0)

        // ── 网络断线重连 ──────────────────────────────────────────
        mMediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "reconnect", 1)

        // ── 跳过解码 loop filter（降低 CPU 占用，不影响音频） ─────
        mMediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_CODEC, "skip_loop_filter", 48)

        // ── 直播低延迟 ────────────────────────────────────────────
        mMediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "max_cached_duration", 300)
        mMediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "flush_packets", 1)
        mMediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "min-frames", 1)
        mMediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_CODEC, "threads", "1")
        mMediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "dns_cache_timeout", -1)
        mMediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "safe", 0)
    }

    class Factory : PlayerFactory<HardwareIjkPlayer>() {
        companion object {
            fun create() = Factory()
        }
        override fun createPlayer(context: Context) = HardwareIjkPlayer(context)
    }
}
