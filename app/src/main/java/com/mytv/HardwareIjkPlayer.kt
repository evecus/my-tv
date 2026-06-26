package com.github.mytv

import android.content.Context
import tv.danmaku.ijk.media.player.IjkMediaPlayer
import xyz.doikki.videoplayer.ijk.IjkPlayer
import xyz.doikki.videoplayer.player.PlayerFactory

class HardwareIjkPlayer(context: Context) : IjkPlayer(context) {

    override fun setOptions() {
        // ── 硬解 ────────────────────────────────────────────────
        mMediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "mediacodec", 1)
        mMediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "mediacodec-auto-rotate", 1)
        mMediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "mediacodec-handle-resolution-change", 1)

        // ── 音频输出：关闭 OpenSL ES，走系统 AudioTrack ────────
        // OpenSL ES 会请求 DEEP_BUFFER flag，该设备 HAL 只支持 PRIMARY，
        // flag 不匹配导致 createTrack 失败从而静音
        mMediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "opensles", 0)

        // ── 音频格式兼容：强制软解音频，覆盖所有常见格式 ───────
        // 部分 IPTV 流使用 AC3/EAC3(杜比)/DTS 音频，IJK 默认不启用这些解码器
        // 开启后由 FFmpeg 软解，确保有声
        mMediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "framedrop", 1)
        // 允许解码任何音频格式，不校验 profile/level
        mMediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_CODEC, "skip_loop_filter", 48)
        // 强制音频解码器不跳帧
        mMediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "skip-calc-frame-num", 0)

        // 开启 AC3 / EAC3 软解（国内 IPTV 部分频道使用杜比音频）
        mMediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "ac3", 1)
        mMediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "eac3", 1)

        // 音频重采样：统一输出为 48000Hz 立体声，避免采样率/声道不匹配导致静音
        mMediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "soundtouch", 1)

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
