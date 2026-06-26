package com.github.mytv

import android.content.Context
import android.view.Surface
import android.view.SurfaceHolder
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import xyz.doikki.videoplayer.player.AbstractPlayer
import xyz.doikki.videoplayer.player.PlayerFactory

/**
 * 禁用 tunneling 模式的 ExoPlayer 封装。
 *
 * 问题背景：日志显示 AudioFlinger 持续报
 *   "mismatch between requested flags (00000008) and output flags (00000002)"
 * 其中 0x8 = AUDIO_OUTPUT_FLAG_HW_AV_SYNC，0x2 = AUDIO_OUTPUT_FLAG_FAST。
 * ExoPlayer 默认开启 tunneling 时会请求 HW_AV_SYNC，但此设备音频 HAL 不支持，
 * 导致部分频道 AudioTrack 创建失败，画面正常但无声音。
 *
 * 解决方案：直接包装 ExoPlayer，通过 DKPlayer 的 AbstractPlayer 接口接入，
 * 完全绕开 ExoMediaPlayer 的内部字段，避免因版本差异导致编译失败。
 */
class NoTunnelingExoPlayer private constructor(
    private val exoPlayer: ExoPlayer
) : AbstractPlayer() {

    // ---- ExoPlayer 事件 → DKPlayer 状态机 ----

    private val listener = object : Player.Listener {

        override fun onPlaybackStateChanged(state: Int) {
            when (state) {
                Player.STATE_READY -> {
                    mPlayerEventListener?.onPrepared()
                    if (exoPlayer.playWhenReady) mPlayerEventListener?.onInfo(MEDIA_INFO_RENDERING_START, 0)
                }
                Player.STATE_ENDED -> mPlayerEventListener?.onCompletion()
                else -> { /* buffering / idle handled below */ }
            }
        }

        override fun onIsLoadingChanged(isLoading: Boolean) {
            if (isLoading) mPlayerEventListener?.onInfo(MEDIA_INFO_BUFFERING_START, 0)
            else mPlayerEventListener?.onInfo(MEDIA_INFO_BUFFERING_END, 0)
        }

        override fun onPlayerError(error: PlaybackException) {
            mPlayerEventListener?.onError()
        }

        override fun onVideoSizeChanged(videoSize: androidx.media3.common.VideoSize) {
            mPlayerEventListener?.onVideoSizeChanged(videoSize.width, videoSize.height)
        }

        override fun onRenderedFirstFrame() {
            mPlayerEventListener?.onInfo(MEDIA_INFO_VIDEO_RENDERING_START, 0)
        }
    }

    // ---- 生命周期 ----

    override fun initPlayer() {
        exoPlayer.addListener(listener)
    }

    override fun setDataSource(path: String?, headers: Map<String, String>?) {
        val mediaItem = MediaItem.fromUri(path ?: return)
        exoPlayer.setMediaItem(mediaItem)
        exoPlayer.prepare()
    }

    override fun start() {
        exoPlayer.play()
    }

    override fun pause() {
        exoPlayer.pause()
    }

    override fun stop() {
        exoPlayer.stop()
    }

    override fun reset() {
        exoPlayer.stop()
        exoPlayer.clearMediaItems()
    }

    override fun release() {
        exoPlayer.removeListener(listener)
        exoPlayer.release()
    }

    // ---- 状态查询 ----

    override fun isPlaying(): Boolean = exoPlayer.isPlaying

    override fun getCurrentPosition(): Long = exoPlayer.currentPosition

    override fun getDuration(): Long = exoPlayer.duration.coerceAtLeast(0)

    override fun getBufferedPercentage(): Int = exoPlayer.bufferedPercentage

    // ---- 控制 ----

    override fun seekTo(time: Long) {
        exoPlayer.seekTo(time)
    }

    override fun setVolume(v1: Float, v2: Float) {
        exoPlayer.volume = (v1 + v2) / 2f
    }

    override fun setLooping(isLooping: Boolean) {
        exoPlayer.repeatMode = if (isLooping) Player.REPEAT_MODE_ALL else Player.REPEAT_MODE_OFF
    }

    override fun setSpeed(speed: Float) {
        exoPlayer.playbackParameters = PlaybackParameters(speed)
    }

    override fun getSpeed(): Float = exoPlayer.playbackParameters.speed

    override fun setDisplay(holder: SurfaceHolder?) {
        exoPlayer.setVideoSurfaceHolder(holder)
    }

    override fun setSurface(surface: Surface?) {
        exoPlayer.setVideoSurface(surface)
    }

    override fun setOptions() {
        // 所有选项已在构造时通过 ExoPlayer.Builder 配置完毕
    }

    // ---- 工厂 ----

    class Factory private constructor() : PlayerFactory<NoTunnelingExoPlayer>() {

        companion object {
            fun create() = Factory()
        }

        override fun createPlayer(context: Context): NoTunnelingExoPlayer {
            // 关闭 tunneling，避免请求设备不支持的 HW_AV_SYNC (flag 0x8)
            val trackSelector = DefaultTrackSelector(context).apply {
                parameters = buildUponParameters()
                    .setTunnelingEnabled(false)
                    .build()
            }

            // 标准音频属性，不触发任何特殊硬件输出标志
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                .build()

            val exoPlayer = ExoPlayer.Builder(context)
                .setTrackSelector(trackSelector)
                .setAudioAttributes(audioAttributes, true)
                .build()

            return NoTunnelingExoPlayer(exoPlayer)
        }
    }
}
