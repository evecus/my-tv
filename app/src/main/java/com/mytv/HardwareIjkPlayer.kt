package com.github.mytv

import android.content.Context
import tv.danmaku.ijk.media.player.IjkMediaPlayer
import xyz.doikki.videoplayer.ijk.IjkPlayer
import xyz.doikki.videoplayer.player.PlayerFactory

/**
 * 开启硬解的 IJKPlayer 实现。
 * setOptions() 在 initPlayer() 内、播放器初始化后立即调用，此时 mMediaPlayer 已就绪。
 */
class HardwareIjkPlayer(context: Context) : IjkPlayer(context) {

    override fun setOptions() {
        // 开启 MediaCodec 硬解
        mMediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "mediacodec", 1)
        // 允许硬解自动旋转
        mMediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "mediacodec-auto-rotate", 1)
        // 允许硬解处理分辨率变化（直播流换源时需要）
        mMediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "mediacodec-handle-resolution-change", 1)
        // 直播低延迟优化
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
