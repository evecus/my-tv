package com.github.mytv.speedtest

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

object SpeedtestManager {

    private const val TAG = "SpeedtestManager"

    private val isRunning = AtomicBoolean(false)
    fun isRunning() = isRunning.get()

    interface ProgressListener {
        fun onProgress(phase: String)
        fun onFinished()
        fun onError(message: String)
    }

    /**
     * 启动 Rust 测速二进制，等待其输出 m3u8 文件。
     * 成功后 m3u8 文件已在 filesDir/iptv_sources.m3u8，可直接播放。
     * 返回 true 表示成功，false 表示失败或重复启动。
     */
    suspend fun runSpeedtest(
        context: Context,
        listener: ProgressListener? = null,
    ): Boolean = withContext(Dispatchers.IO) {
        if (!isRunning.compareAndSet(false, true)) {
            Log.w(TAG, "already running")
            return@withContext false
        }
        try {
            doRun(context, listener)
        } finally {
            isRunning.set(false)
        }
    }

    private fun doRun(context: Context, listener: ProgressListener?): Boolean {
        listener?.onProgress("正在启动测速引擎…")

        try {
            NativeSpeedtestRunner.run(
                context     = context,
                workers     = 60,
                top         = 10,
                logCallback = { line ->
                    val phase = line.removePrefix("[android]").trim()
                    if (phase.isNotEmpty()) listener?.onProgress(phase)
                },
            )
        } catch (e: Exception) {
            Log.e(TAG, "speedtest failed: ${e.message}")
            listener?.onError("测速失败：${e.message}")
            return false
        }

        Log.i(TAG, "speedtest done, m3u8 ready")
        listener?.onFinished()
        return true
    }
}
