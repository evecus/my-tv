package com.lizongying.mytv

import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.View.GONE
import android.view.View.VISIBLE
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import com.lizongying.mytv.databinding.SettingBinding
import com.lizongying.mytv.speedtest.SpeedtestManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
        binding.version.text = "https://github.com/lizongying/my-tv"

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
        binding.tvSpeedtestStatus.textSize = application.px2PxFont(12f)
        binding.switchAutoSpeedtest.textSize = textSize

        binding.exit.setOnClickListener {
            requireActivity().finishAffinity()
        }

        return binding.root
    }

    // ── 测速流程 ─────────────────────────────────────────────────

    private fun startSpeedtest() {
        if (SpeedtestManager.isRunning()) {
            Toast.makeText(context, "测速正在进行中…", Toast.LENGTH_SHORT).show()
            return
        }

        binding.btnSpeedtest.isEnabled = false
        binding.tvSpeedtestStatus.visibility = VISIBLE
        binding.tvSpeedtestStatus.text = "准备开始测速…"

        val ctx = requireContext().applicationContext

        lifecycleScope.launch {
            val count = withContext(Dispatchers.IO) {
                SpeedtestManager.runSpeedtest(
                    context  = ctx,
                    listener = object : SpeedtestManager.ProgressListener {
                        override fun onProgress(
                            completed: Int, total: Int, valid: Int, phase: String
                        ) {
                            val msg = if (total > 0)
                                "$phase $completed/$total（有效 $valid）"
                            else
                                phase
                            lifecycleScope.launch(Dispatchers.Main) {
                                binding.tvSpeedtestStatus.text = msg
                            }
                        }

                        override fun onFinished(channelCount: Int) {
                            SP.lastSpeedtest = System.currentTimeMillis()
                        }

                        override fun onError(message: String) {
                            lifecycleScope.launch(Dispatchers.Main) {
                                binding.tvSpeedtestStatus.text = message
                            }
                        }
                    }
                )
            }

            // 回到主线程更新 UI
            binding.btnSpeedtest.isEnabled = true
            if (count > 0) {
                binding.tvSpeedtestStatus.text = "测速完成，共 $count 个频道，重启生效"
                Toast.makeText(ctx, "测速完成，共 $count 个频道，重启 App 即可使用", Toast.LENGTH_LONG).show()
                // 通知 MainActivity 重新加载频道列表
                (activity as? MainActivity)?.reloadChannels()
            } else {
                binding.tvSpeedtestStatus.text = "未发现可用频道"
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "SettingFragment"
    }
}
