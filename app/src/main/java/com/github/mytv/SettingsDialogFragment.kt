package com.github.mytv

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.widget.SwitchCompat
import androidx.fragment.app.DialogFragment

/**
 * 平板端设置弹窗：使用居中 DialogFragment，
 * 宽度固定 480dp，高度自适应，避免 BottomSheet 在平板横屏下显示不完整。
 */
class SettingsDialogFragment : DialogFragment() {

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return Dialog(requireContext(), R.style.Theme_MyTV_Dialog)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.dialog_settings, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val ctx = requireContext()

        view.findViewById<TextView>(R.id.tv_version_name).text =
            "当前版本: v${ctx.appVersionName}"

        view.findViewById<View>(R.id.btn_close_settings).setOnClickListener {
            dismissAllowingStateLoss()
        }

        view.findViewById<SwitchCompat>(R.id.switch_auto_speedtest).apply {
            isChecked = SP.autoSpeedtest
            setOnCheckedChangeListener { _, checked -> SP.autoSpeedtest = checked }
        }

        view.findViewById<SwitchCompat>(R.id.switch_channel_reversal).apply {
            isChecked = SP.channelReversal
            setOnCheckedChangeListener { _, checked -> SP.channelReversal = checked }
        }

        view.findViewById<SwitchCompat>(R.id.switch_boot_startup).apply {
            isChecked = SP.bootStartup
            setOnCheckedChangeListener { _, checked -> SP.bootStartup = checked }
        }

        view.findViewById<View>(R.id.btn_speedtest).setOnClickListener {
            dismissAllowingStateLoss()
            SpeedtestDialogFragment.show(requireActivity())
        }

        view.findViewById<View>(R.id.btn_check_version).setOnClickListener {
            val versionCode = ctx.appVersionCode
            UpdateManager(requireActivity(), versionCode).checkAndUpdate()
        }

        view.findViewById<View>(R.id.btn_exit).setOnClickListener {
            requireActivity().finishAffinity()
        }

        // ── 播放器内核 / 解码方式 ────────────────────────────────
        setupPlayerConfigButtons(view)
    }

    private fun setupPlayerConfigButtons(view: View) {
        refreshPlayerConfigButtonTexts(view)
        view.findViewById<View>(R.id.btn_player_type).setOnClickListener {
            SP.playerType = if (SP.playerType == "exo") "ijk" else "exo"
            refreshPlayerConfigButtonTexts(view)
        }
        view.findViewById<View>(R.id.btn_ijk_decoder).setOnClickListener {
            SP.ijkDecoder = when (SP.ijkDecoder) {
                "auto" -> "hard"
                "hard" -> "soft"
                else -> "auto"
            }
            refreshPlayerConfigButtonTexts(view)
        }
        view.findViewById<View>(R.id.btn_exo_decoder).setOnClickListener {
            SP.exoDecoder = if (SP.exoDecoder == "default") "software" else "default"
            refreshPlayerConfigButtonTexts(view)
        }
    }

    private fun refreshPlayerConfigButtonTexts(view: View) {
        view.findViewById<Button>(R.id.btn_player_type).text =
            getString(R.string.title_player_type) + ": " +
            getString(if (SP.playerType == "ijk") R.string.player_type_ijk else R.string.player_type_exo)
        view.findViewById<Button>(R.id.btn_ijk_decoder).text =
            getString(R.string.title_ijk_decoder) + ": " +
            when (SP.ijkDecoder) {
                "hard" -> getString(R.string.decoder_hard)
                "soft" -> getString(R.string.decoder_soft)
                else -> getString(R.string.decoder_auto)
            }
        view.findViewById<Button>(R.id.btn_exo_decoder).text =
            getString(R.string.title_exo_decoder) + ": " +
            if (SP.exoDecoder == "software")
                getString(R.string.decoder_software)
            else
                getString(R.string.decoder_default)
    }

    override fun onStart() {
        super.onStart()
        // 固定宽度 480dp，高度自适应（最大 80% 屏幕高度由 ScrollView 保证）
        dialog?.window?.apply {
            val widthPx = (480 * resources.displayMetrics.density).toInt()
            setLayout(widthPx, WindowManager.LayoutParams.WRAP_CONTENT)
            setBackgroundDrawableResource(R.drawable.rounded_dialog)
        }
    }

    companion object {
        const val TAG = "SettingsDialogFragment"

        fun show(activity: androidx.fragment.app.FragmentActivity) {
            if (activity.supportFragmentManager.findFragmentByTag(TAG) != null) return
            SettingsDialogFragment().show(activity.supportFragmentManager, TAG)
        }
    }
}
