package com.github.mytv

import android.content.Context
import tv.danmaku.ijk.media.player.IjkMediaPlayer
import xyz.doikki.videoplayer.ijk.IjkPlayer
import xyz.doikki.videoplayer.player.PlayerFactory

/**
 * 开启硬解的 IJKPlayer 实现。
 */
class HardwareIjkPlayer(context: Context) : IjkPlayer(context) {

    override fun setOptions() {
        // ── 硬解 ────────────────────────────────────────────────
        mMediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "mediacodec", 1)
        mMediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "mediacodec-auto-rotate", 1)
        mMediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "mediacodec-handle-resolution-change", 1)

        // ── 音频输出：关闭 OpenSL ES，走系统默认 AudioTrack ────
        // OpenSL ES 会请求 DEEP_BUFFER flag，而该设备 audio HAL 只支持 PRIMARY，
        // flag 不匹配会导致 createTrack 失败从而静音。
        mMediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "opensles", 0)

        // ── 直播低延迟 ─────────────────────────────────────────
        mMediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "packet-buffering", 0)
        mMediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "max-buffer-size", 0)
        mMediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "fflags", "nobuffer")
        mMediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "analyzeduration", 100L)
    }

    class Factory : PlayerFactory<HardwareIjkPlayer>() {
        companion object {
            fun create() = Factory()
        }
        override fun createPlayer(context: Context) = HardwareIjkPlayer(context)
    }
}
