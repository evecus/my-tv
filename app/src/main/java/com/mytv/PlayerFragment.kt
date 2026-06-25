package com.github.mytv

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import androidx.fragment.app.Fragment
import com.dueeeke.videoplayer.exo.ExoMediaPlayerFactory
import com.dueeeke.videoplayer.ijk.IjkPlayerFactory
import com.dueeeke.videoplayer.player.AbstractPlayer
import com.dueeeke.videoplayer.player.VideoView
import com.dueeeke.videoplayer.player.VideoViewConfig
import com.dueeeke.videoplayer.player.VideoViewManager
import com.github.mytv.databinding.PlayerBinding
import com.github.mytv.models.TVViewModel


class PlayerFragment : Fragment() {

    private var _binding: PlayerBinding? = null
    private var videoView: VideoView<AbstractPlayer>? = null
    private var tvViewModel: TVViewModel? = null
    private val aspectRatio = 16f / 9f

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = PlayerBinding.inflate(inflater, container, false)

        // 初始化 DKPlayer，根据 SP 配置选择内核
        val factory = if (SP.playerEngine == SP.PLAYER_ENGINE_IJK) {
            IjkPlayerFactory.create()
        } else {
            ExoMediaPlayerFactory.create()
        }
        VideoViewManager.setConfig(
            VideoViewConfig.newBuilder()
                .setPlayerFactory(factory)
                .build()
        )

        @Suppress("UNCHECKED_CAST")
        videoView = VideoView<AbstractPlayer>(requireContext()).also { vv ->
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

            vv.addOnStateChangeListener(object : VideoView.OnStateChangeListener {
                override fun onPlayerStateChanged(playerState: Int) {}

                override fun onPlayStateChanged(playState: Int) {
                    when (playState) {
                        VideoView.STATE_PLAYING -> {
                            tvViewModel?.setErrInfo("")
                        }
                        VideoView.STATE_ERROR -> {
                            Log.e(TAG, "PlaybackException: player error")
                            tvViewModel?.setErrInfo("播放错误")
                            tvViewModel?.changed("retry")
                        }
                    }
                }
            })

            _binding!!.playerContainer.addView(vv)
        }

        (activity as MainActivity).fragmentReady(TAG)
        return _binding!!.root
    }

    private fun adjustAspectRatio(vv: VideoView<AbstractPlayer>) {
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
        videoView?.run {
            release()
            // 切换内核时重建（切换内核需要重建 VideoView）
            rebuildIfEngineChanged()
            setUrl(tvViewModel.getVideoUrlCurrent())
            start()
        }
    }

    /**
     * 如果用户在设置里切换了播放器内核，重新初始化 VideoViewManager。
     * VideoView 本身不需要重建，只要在下次 play() 前更新全局配置即可。
     */
    private fun rebuildIfEngineChanged() {
        val factory = if (SP.playerEngine == SP.PLAYER_ENGINE_IJK) {
            IjkPlayerFactory.create()
        } else {
            ExoMediaPlayerFactory.create()
        }
        VideoViewManager.setConfig(
            VideoViewConfig.newBuilder()
                .setPlayerFactory(factory)
                .build()
        )
    }

    override fun onStart() {
        Log.i(TAG, "onStart")
        super.onStart()
        videoView?.let {
            if (!it.isPlaying) {
                it.resume()
            }
        }
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
