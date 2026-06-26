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
        // 增大探测时间，确保 ffmpeg 能找到音频流
        mMediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "analyzeduration", 5000000L)
        mMediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "probesize", 5000000L)
        // 修复：禁用 OpenSL ES，避免触发设备不支持的 HW_AV_SYNC (0x8) flag
        // 日志显示 AudioFlinger 持续报 mismatch(0x8 vs 0x2)，根源是 opensles 请求了硬件AV同步
        mMediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "opensles", 0)
        // 允许所有格式扩展，防止部分流的音频轨道被过滤
        mMediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "allowed_extensions", "ALL")
        // 音频流自动选择
        mMediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "audio-stream-index", -1L)
    }

    class Factory : PlayerFactory<HardwareIjkPlayer>() {
        companion object {
            fun create() = Factory()
        }

        override fun createPlayer(context: Context) = HardwareIjkPlayer(context)
    }
}
