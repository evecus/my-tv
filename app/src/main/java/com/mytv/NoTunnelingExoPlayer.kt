package com.github.mytv

import android.content.Context
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import xyz.doikki.videoplayer.exo.ExoMediaPlayer
import xyz.doikki.videoplayer.player.PlayerFactory

/**
 * 禁用 tunneling 模式的 ExoPlayer 工厂。
 *
 * 问题背景：日志显示 AudioFlinger 持续报
 *   "mismatch between requested flags (00000008) and output flags (00000002)"
 * 其中 0x8 = AUDIO_OUTPUT_FLAG_HW_AV_SYNC，0x2 = AUDIO_OUTPUT_FLAG_FAST。
 * ExoPlayer 默认开启 tunneling 时会请求 HW_AV_SYNC，但此设备音频 HAL 不支持，
 * 导致部分频道 AudioTrack 创建失败，画面正常但无声音。
 *
 * 解决方案：继承 ExoMediaPlayer，重写 initPlayer() 注入自定义配置。
 */
class NoTunnelingExoPlayer(context: Context) : ExoMediaPlayer(context) {

    override fun initPlayer() {
        // 关闭 tunneling，避免请求设备不支持的 HW_AV_SYNC (flag 0x8)
        val trackSelector = DefaultTrackSelector(mAppContext).apply {
            parameters = buildUponParameters()
                .setTunnelingEnabled(false)
                .build()
        }

        // 设置标准音频属性，不触发任何特殊硬件输出标志
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
            .build()

        mMediaPlayer = ExoPlayer.Builder(mAppContext)
            .setTrackSelector(trackSelector)
            .setAudioAttributes(audioAttributes, /* handleAudioFocus= */ true)
            .build()
    }

    class Factory : PlayerFactory<NoTunnelingExoPlayer>() {
        companion object {
            fun create() = Factory()
        }

        override fun createPlayer(context: Context) = NoTunnelingExoPlayer(context)
    }
}
