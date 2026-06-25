package com.lizongying.mytv

import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import com.lizongying.mytv.databinding.SettingBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SettingFragment : DialogFragment() {

    private var _binding: SettingBinding? = null
    private val binding get() = _binding!!

    override fun onStart() {
        super.onStart()
        dialog?.window?.apply {
            addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
            decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                attributes.layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                setAttributes(attributes)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NO_TITLE, 0)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val context = requireContext()
        _binding = SettingBinding.inflate(inflater, container, false)
        binding.versionName.text = "当前版本: v${context.appVersionName}"
        binding.version.text = "https://github.com/evecus/my-tv"

        binding.switchChannelReversal.run {
            isChecked = SP.channelReversal
            setOnCheckedChangeListener { _, isChecked ->
                SP.channelReversal = isChecked
                (activity as MainActivity).settingDelayHide()
            }
        }

        binding.switchChannelNum.run {
            isChecked = SP.channelNum
            setOnCheckedChangeListener { _, isChecked ->
                SP.channelNum = isChecked
                (activity as MainActivity).settingDelayHide()
            }
        }

        binding.switchTime.run {
            isChecked = SP.time
            setOnCheckedChangeListener { _, isChecked ->
                SP.time = isChecked
                (activity as MainActivity).settingDelayHide()
            }
        }

        binding.switchBootStartup.run {
            isChecked = SP.bootStartup
            setOnCheckedChangeListener { _, isChecked ->
                SP.bootStartup = isChecked
                (activity as MainActivity).settingDelayHide()
            }
        }

        binding.switchGrid.run {
            isChecked = SP.grid
            setOnCheckedChangeListener { _, isChecked ->
                SP.grid = isChecked
                (activity as MainActivity).settingDelayHide()
            }
        }

        // ── 自动测速开关 ────────────────────────────────────────
        binding.switchAutoSpeedtest.run {
            isChecked = SP.autoSpeedtest
            setOnCheckedChangeListener { _, isChecked ->
                SP.autoSpeedtest = isChecked
                (activity as MainActivity).settingDelayHide()
            }
        }

        // ── 手动测速按钮 ────────────────────────────────────────
        binding.btnSpeedtest.setOnClickListener {
            startSpeedtest()
        }

        binding.clear.setOnClickListener {
            // 原来的"恢复默认"逻辑保留（清 guid），
            // 不再触发杨视频 API，仅重置
            (requireActivity() as MainActivity).syncTime()
        }

        val application = requireActivity().applicationContext as MyTVApplication
        val textSize = application.px2PxFont(binding.switchChannelReversal.textSize)

        // 尺寸缩放（保持原来的逻辑）
        binding.content.layoutParams.width = application.px2Px(binding.content.layoutParams.width)
        binding.content.setPadding(
            application.px2Px(binding.content.paddingLeft),
            application.px2Px(binding.content.paddingTop),
            application.px2Px(binding.content.paddingRight),
            application.px2Px(binding.content.paddingBottom)
        )
        binding.name.textSize = application.px2PxFont(binding.name.textSize)
        binding.version.textSize = textSize
        binding.checkVersion.textSize = textSize
        binding.versionName.textSize = textSize
        binding.clear.textSize = textSize
        binding.exit.textSize = textSize
        binding.btnSpeedtest.textSize = textSize
        binding.switchAutoSpeedtest.textSize = textSize

        binding.exit.setOnClickListener {
            requireActivity().finishAffinity()
        }

        return binding.root
    }

    // ── 测速流程 ─────────────────────────────────────────────────

    private fun startSpeedtest() {
        SpeedtestDialogFragment.show(requireActivity() as MainActivity)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "SettingFragment"
    }
}
