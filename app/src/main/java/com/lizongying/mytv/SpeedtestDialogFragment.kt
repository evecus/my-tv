package com.lizongying.mytv

import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import com.lizongying.mytv.speedtest.SpeedtestManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 测速进度弹窗。
 *
 * 用法：
 *   SpeedtestDialogFragment.show(supportFragmentManager)
 *
 * 弹窗在测速完成/失败后自动关闭，外部无需手动 dismiss。
 * 若测速已在运行（SpeedtestManager.isRunning()），弹窗不会重复启动，
 * 直接 Toast 提示后返回。
 */
class SpeedtestDialogFragment : DialogFragment() {

    private lateinit var tvPhase:    TextView
    private lateinit var tvProgress: TextView
    private lateinit var progressBar: ProgressBar

    // ── 窗口样式：全屏 flags，避免导航栏跳出 ───────────────────

    override fun onStart() {
        super.onStart()
        dialog?.window?.apply {
            addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
            decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_IMMERSIVE
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                attributes.layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                setAttributes(attributes)
            }
            // 弹窗尺寸：固定宽度，高度自适应，居中
            setLayout(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
            )
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NO_TITLE, 0)
        isCancelable = false   // 测速期间不允许点背景关闭
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        val view = inflater.inflate(R.layout.dialog_speedtest, container, false)
        tvPhase    = view.findViewById(R.id.tv_phase)
        tvProgress = view.findViewById(R.id.tv_progress)
        progressBar = view.findViewById(R.id.progress_bar)
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        if (SpeedtestManager.isRunning()) {
            Toast.makeText(context, "测速正在进行中…", Toast.LENGTH_SHORT).show()
            dismissAllowingStateLoss()
            return
        }

        val ctx = requireContext().applicationContext

        lifecycleScope.launch {
            val count = withContext(Dispatchers.IO) {
                SpeedtestManager.runSpeedtest(
                    context  = ctx,
                    listener = object : SpeedtestManager.ProgressListener {

                        override fun onProgress(
                            completed: Int, total: Int, valid: Int, phase: String,
                        ) {
                            lifecycleScope.launch(Dispatchers.Main) {
                                if (!isAdded) return@launch
                                // 去掉 Rust 前缀，只取有意义的部分
                                val cleaned = phase
                                    .removePrefix("[android]")
                                    .trim()
                                    .ifEmpty { phase.trim() }
                                tvPhase.text = cleaned

                                if (total > 0) {
                                    progressBar.isIndeterminate = false
                                    progressBar.max = total
                                    progressBar.progress = completed
                                    tvProgress.visibility = View.VISIBLE
                                    tvProgress.text = "$completed / $total  有效: $valid"
                                } else {
                                    progressBar.isIndeterminate = true
                                    tvProgress.visibility = View.GONE
                                }
                            }
                        }

                        override fun onFinished(channelCount: Int) {
                            SP.lastSpeedtest = System.currentTimeMillis()
                        }

                        override fun onError(message: String) {
                            lifecycleScope.launch(Dispatchers.Main) {
                                if (!isAdded) return@launch
                                tvPhase.text = message
                            }
                        }
                    }
                )
            }

            // 测速结束，回主线程
            if (!isAdded) return@launch

            if (count > 0) {
                (activity as? MainActivity)?.reloadChannels()
                Toast.makeText(
                    ctx,
                    "测速完成，共 $count 个频道",
                    Toast.LENGTH_LONG,
                ).show()
            } else {
                Toast.makeText(ctx, "未发现可用频道", Toast.LENGTH_SHORT).show()
            }

            dismissAllowingStateLoss()
        }
    }

    companion object {
        const val TAG = "SpeedtestDialogFragment"

        /** 便捷入口：由 MainActivity / SettingFragment 调用 */
        fun show(activity: androidx.fragment.app.FragmentActivity) {
            // 防止重复弹出
            if (activity.supportFragmentManager.findFragmentByTag(TAG) != null) return
            SpeedtestDialogFragment()
                .show(activity.supportFragmentManager, TAG)
        }
    }
}
