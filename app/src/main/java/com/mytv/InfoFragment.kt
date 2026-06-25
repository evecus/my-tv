package com.github.mytv

import android.os.Bundle
import android.os.Handler
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.marginBottom
import androidx.core.view.marginStart
import androidx.core.view.marginTop
import androidx.core.view.setPadding
import androidx.fragment.app.Fragment
import com.bumptech.glide.Glide
import com.github.mytv.databinding.InfoBinding
import com.github.mytv.models.TVViewModel

class InfoFragment : Fragment() {
    private var _binding: InfoBinding? = null
    private val binding get() = _binding!!

    private val handler = Handler()
    private val delay: Long = 5000
    private val sourceDelay: Long = 3000   // 换源提示显示时长

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = InfoBinding.inflate(inflater, container, false)

        val application = requireActivity().applicationContext as MyTVApplication

        binding.info.layoutParams.width  = application.px2Px(binding.info.layoutParams.width)
        binding.info.layoutParams.height = application.px2Px(binding.info.layoutParams.height)

        val layoutParams = binding.info.layoutParams as ViewGroup.MarginLayoutParams
        layoutParams.bottomMargin = application.px2Px(binding.info.marginBottom)
        binding.info.layoutParams = layoutParams

        binding.logo.layoutParams.width = application.px2Px(binding.logo.layoutParams.width)
        binding.logo.setPadding(application.px2Px(binding.logo.paddingTop))
        binding.main.layoutParams.width = application.px2Px(binding.main.layoutParams.width)
        binding.main.setPadding(application.px2Px(binding.main.paddingTop))

        val layoutParamsMain = binding.main.layoutParams as ViewGroup.MarginLayoutParams
        layoutParamsMain.marginStart = application.px2Px(binding.main.marginStart)
        binding.main.layoutParams = layoutParamsMain

        val layoutParamsDesc = binding.desc.layoutParams as ViewGroup.MarginLayoutParams
        layoutParamsDesc.topMargin = application.px2Px(binding.desc.marginTop)
        binding.desc.layoutParams = layoutParamsDesc

        binding.title.textSize = application.px2PxFont(binding.title.textSize)
        binding.desc.textSize  = application.px2PxFont(binding.desc.textSize)

        binding.container.layoutParams.width  = application.shouldWidthPx()
        binding.container.layoutParams.height = application.shouldHeightPx()

        _binding!!.root.visibility = View.GONE

        (activity as MainActivity).fragmentReady(TAG)
        return binding.root
    }

    /** 切换频道时调用，显示频道名+台标+节目，5s 后隐藏 */
    fun show(tvViewModel: TVViewModel) {
        if (_binding == null) return
        binding.title.text = tvViewModel.getTV().title

        Glide.with(this)
            .load(tvViewModel.getTV().logo)
            .into(binding.logo)

        Log.i(TAG, "${tvViewModel.getTV().title} ${tvViewModel.epg.value}")
        val epg = tvViewModel.epg.value?.filter { it.beginTime < Utils.getDateTimestamp() }
        binding.desc.text = if (!epg.isNullOrEmpty()) epg.last().title else ""

        handler.removeCallbacks(removeRunnable)
        view?.visibility = View.VISIBLE
        handler.postDelayed(removeRunnable, delay)
    }

    /**
     * 切换源时调用，右上角显示"源 current/total"，3s 后消失。
     * 整个 InfoFragment 的 root 也需要可见（show 可能已触发，也可能没触发）。
     */
    /**
     * 切换源时调用，右上角显示源信息，3s 后消失。
     *   current >  0 → 正常切换，显示"源 current/total"
     *   current == 0 → 已是第一个源，显示"已是第一个源!"
     *   current == -1→ 已是最后一个源，显示"已是最后一个源!"
     */
    fun showSourceIndicator(current: Int, total: Int) {
        if (_binding == null) return
        if (total <= 1) return   // 只有一个源不显示

        val indicator = binding.tvSourceIndicator
        indicator.text = when (current) {
            0    -> "已是第一个源!"
            -1   -> "已是最后一个源!"
            else -> "源 $current/$total"
        }
        indicator.visibility = View.VISIBLE

        // root 可见（如果频道信息卡片已隐藏，让 root 单独显示源指示器）
        view?.visibility = View.VISIBLE

        handler.removeCallbacks(hideSourceRunnable)
        handler.postDelayed(hideSourceRunnable, sourceDelay)
    }

    private val hideSourceRunnable = Runnable {
        _binding?.tvSourceIndicator?.visibility = View.GONE
        // 如果频道信息卡片也已隐藏，一起隐藏 root
        if (_binding?.info?.visibility == View.GONE) {
            view?.visibility = View.GONE
        }
    }

    override fun onResume() {
        super.onResume()
        handler.postDelayed(removeRunnable, delay)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(removeRunnable)
        handler.removeCallbacks(hideSourceRunnable)
    }

    private val removeRunnable = Runnable {
        _binding?.info?.visibility = View.GONE
        // 如果源指示器也消失了，隐藏 root
        if (_binding?.tvSourceIndicator?.visibility == View.GONE) {
            view?.visibility = View.GONE
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val TAG = "InfoFragment"
    }
}
