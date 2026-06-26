package com.github.mytv

import android.content.Context
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import xyz.doikki.videoplayer.exo.ExoMediaPlayer
import xyz.doikki.videoplayer.player.PlayerFactory

/**
 * 禁用 tunneling 模式的 ExoPlayer。
 *
 * 问题：AudioFlinger 持续报
 *   "mismatch between requested flags (00000008) and output flags (00000002)"
 * 0x8 = AUDIO_OUTPUT_FLAG_HW_AV_SYNC，此设备音频 HAL 不支持，
 * 导致部分频道 AudioTrack 创建失败，有画面无声音。
 *
 * 方案：继承 ExoMediaPlayer，在 setOptions() 里通过反射拿到内部 ExoPlayer 实例，
 * 调用 setTrackSelectionParameters 关闭 tunneling。
 * 不依赖任何 protected 字段名，兼容 DKPlayer 各版本。
 */
class NoTunnelingExoPlayer(context: Context) : ExoMediaPlayer(context) {

    override fun setOptions() {
        try {
            // 用反射找到内部 ExoPlayer 实例（字段可能叫 mInternalPlayer 或 mMediaPlayer）
            val exoPlayer = findExoPlayerByReflection() ?: return

            // 方式1：直接设置 TrackSelectionParameters 禁用 tunneling（无需替换 trackSelector）
            val currentParams = exoPlayer.trackSelectionParameters
            exoPlayer.trackSelectionParameters = currentParams
                .buildUpon()
                .setTunnelingEnabled(false)
                .build()

            // 方式2：设置音频属性，避免触发 HW_AV_SYNC flag
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                .build()
            exoPlayer.setAudioAttributes(audioAttributes, true)

        } catch (e: Exception) {
            // 反射失败时静默降级，不影响正常播放
            android.util.Log.w("NoTunnelingExoPlayer", "setOptions via reflection failed: $e")
        }
    }

    private fun findExoPlayerByReflection(): ExoPlayer? {
        val candidateFieldNames = listOf("mInternalPlayer", "mMediaPlayer", "exoPlayer", "player")
        for (name in candidateFieldNames) {
            try {
                val field = ExoMediaPlayer::class.java.getDeclaredField(name)
                field.isAccessible = true
                val value = field.get(this)
                if (value is ExoPlayer) return value
            } catch (_: NoSuchFieldException) {
                continue
            }
        }
        // 父类也找不到，往上再找一级
        try {
            val fields = ExoMediaPlayer::class.java.declaredFields +
                         ExoMediaPlayer::class.java.superclass?.declaredFields.orEmpty()
            for (field in fields) {
                field.isAccessible = true
                val value = runCatching { field.get(this) }.getOrNull()
                if (value is ExoPlayer) return value
            }
        } catch (_: Exception) {}
        return null
    }

    class Factory : PlayerFactory<NoTunnelingExoPlayer>() {
        companion object {
            fun create() = Factory()
        }
        override fun createPlayer(context: Context) = NoTunnelingExoPlayer(context)
    }
}
