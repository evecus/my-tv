package com.github.mytv

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
import com.github.mytv.speedtest.SpeedtestManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 测速进度弹窗。
 *
 * Rust 端完成全部测速 / 整理 / 写文件工作，Kotlin 只展示进度并在完成后
 * 通知 MainActivity 重新加载播放列表。
 */
class SpeedtestDialogFragment : DialogFragment() {

    private lateinit var tvPhase:    TextView
    private lateinit var progressBar: ProgressBar

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
            setLayout(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
            )
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NO_TITLE, 0)
        isCancelable = false
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        val view = inflater.inflate(R.layout.dialog_speedtest, container, false)
        tvPhase    = view.findViewById(R.id.tv_phase)
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
            val success = withContext(Dispatchers.IO) {
                SpeedtestManager.runSpeedtest(
                    context  = ctx,
                    listener = object : SpeedtestManager.ProgressListener {

                        override fun onProgress(phase: String) {
                            lifecycleScope.launch(Dispatchers.Main) {
                                if (!isAdded) return@launch
                                tvPhase.text = phase
                            }
                        }

                        override fun onFinished() {
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

            if (!isAdded) return@launch

            if (success) {
                (activity as? MainActivity)?.reloadChannels()
                Toast.makeText(ctx, "测速完成，播放列表已更新", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(ctx, "测速失败，未发现可用频道", Toast.LENGTH_SHORT).show()
            }

            dismissAllowingStateLoss()
        }
    }

    companion object {
        const val TAG = "SpeedtestDialogFragment"

        fun show(activity: androidx.fragment.app.FragmentActivity) {
            if (activity.supportFragmentManager.findFragmentByTag(TAG) != null) return
            SpeedtestDialogFragment()
                .show(activity.supportFragmentManager, TAG)
        }
    }
}
