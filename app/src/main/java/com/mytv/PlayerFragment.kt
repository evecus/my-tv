package com.github.mytv

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import androidx.fragment.app.Fragment
import com.google.android.exoplayer2.DefaultRenderersFactory
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector
import xyz.doikki.videoplayer.exo.ExoMediaPlayer
import xyz.doikki.videoplayer.exo.ExoMediaPlayerFactory
import xyz.doikki.videoplayer.player.BaseVideoView
import xyz.doikki.videoplayer.player.VideoView
import xyz.doikki.videoplayer.player.VideoViewConfig
import xyz.doikki.videoplayer.player.VideoViewManager
import com.github.mytv.databinding.PlayerBinding
import com.github.mytv.models.TVViewModel


class PlayerFragment : Fragment() {

    private var _binding: PlayerBinding? = null
    private var videoView: VideoView? = null
    private var tvViewModel: TVViewModel? = null
    private val aspectRatio = 16f / 9f

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = PlayerBinding.inflate(inflater, container, false)
        initVideoView()
        (activity as MainActivity).fragmentReady(TAG)
        return _binding!!.root
    }

    private fun initVideoView() {
        applyPlayerEngine()

        val vv = VideoView(requireContext())
        vv.layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )

        vv.viewTreeObserver.addOnGlobalLayoutListener(object :
            ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                vv.viewTreeObserver.removeOnGlobalLayoutListener(this)
                adjustAspectRatio(vv)
            }
        })

        vv.addOnStateChangeListener(object : BaseVideoView.OnStateChangeListener {
            override fun onPlayerStateChanged(playerState: Int) {}

            override fun onPlayStateChanged(playState: Int) {
                when (playState) {
                    BaseVideoView.STATE_PLAYING -> tvViewModel?.setErrInfo("")
                    BaseVideoView.STATE_ERROR -> {
                        Log.e(TAG, "playback error")
                        tvViewModel?.setErrInfo("播放错误")
                        tvViewModel?.changed("retry")
                    }
                }
            }
        })

        _binding!!.playerContainer.addView(vv)
        videoView = vv
    }

    private fun applyPlayerEngine() {
        val factory = if (SP.playerEngine == SP.PLAYER_ENGINE_IJK) {
            HardwareIjkPlayer.Factory.create()
        } else {
            object : ExoMediaPlayerFactory() {
                override fun createPlayer(context: android.content.Context): ExoMediaPlayer {
                    return ExoMediaPlayer(context).also { player ->
                        // 启用解码器 fallback，避免格式不支持时直接放弃
                        val renderersFactory = DefaultRenderersFactory(context).apply {
                            setEnableDecoderFallback(true)
                        }
                        player.setRenderersFactory(renderersFactory)

                        // 强制 TrackSelector 不过滤音频轨道：
                        // 部分频道音频采样率/声道与设备能力有偏差，默认会被排除导致静音
                        val trackSelector = DefaultTrackSelector(context).apply {
                            setParameters(
                                buildUponParameters()
                                    // 允许混合声道数的音频自适应（如 mono/stereo 混合流）
                                    .setAllowAudioMixedChannelCountAdaptiveness(true)
                                    // 允许混合采样率的音频自适应
                                    .setAllowAudioMixedSampleRateAdaptiveness(true)
                                    // 不限制音频比特率
                                    .setMaxAudioBitrate(Int.MAX_VALUE)
                                    // 禁用音频格式的设备能力检查，强制尝试播放所有音轨
                                    .setExceedAudioConstraintsIfNecessary(true)
                                    .build()
                            )
                        }
                        player.setTrackSelector(trackSelector)
                    }
                }
            }
        }
        VideoViewManager.setConfig(
            VideoViewConfig.newBuilder()
                .setPlayerFactory(factory)
                .build()
        )
    }

    private fun adjustAspectRatio(vv: VideoView) {
        val w = vv.measuredWidth
        val h = vv.measuredHeight
        if (h == 0) return
        val ratio = w.toFloat() / h.toFloat()
        val lp = vv.layoutParams
        when {
            ratio < aspectRatio -> lp.height = (w / aspectRatio).toInt()
            ratio > aspectRatio -> lp.width = (h * aspectRatio).toInt()
        }
        vv.layoutParams = lp
    }

    fun play(tvViewModel: TVViewModel) {
        this.tvViewModel = tvViewModel
        videoView?.let { vv ->
            vv.release()
            applyPlayerEngine()
            vv.setUrl(tvViewModel.getVideoUrlCurrent())
            vv.start()
        }
    }

    override fun onStart() {
        Log.i(TAG, "onStart")
        super.onStart()
        videoView?.let { if (!it.isPlaying) it.resume() }
    }

    override fun onResume() {
        Log.i(TAG, "onResume")
        super.onResume()
    }

    override fun onPause() {
        super.onPause()
        videoView?.pause()
    }

    override fun onDestroy() {
        super.onDestroy()
        videoView?.release()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val TAG = "PlayerFragment"
    }
}
