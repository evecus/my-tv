package com.lizongying.mytv.speedtest

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
        fun onProgress(completed: Int, total: Int, valid: Int, phase: String)
        fun onFinished(channelCount: Int)
        fun onError(message: String)
    }

    suspend fun runSpeedtest(
        context: Context,
        listener: ProgressListener? = null,
    ): Int = withContext(Dispatchers.IO) {
        if (!isRunning.compareAndSet(false, true)) {
            Log.w(TAG, "already running")
            return@withContext -1
        }
        try {
            doRun(context, listener)
        } finally {
            isRunning.set(false)
        }
    }

    private fun doRun(context: Context, listener: ProgressListener?): Int {
        listener?.onProgress(0, 0, 0, "正在启动测速引擎…")

        val entries = try {
            NativeSpeedtestRunner.run(
                context     = context,
                workers     = 60,
                top         = 10,
                logCallback = { line ->
                    val phase = line.removePrefix("[android] ").trim()
                    listener?.onProgress(0, 0, 0, phase)
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "speedtest failed: ${e.message}")
            listener?.onError("测速失败：${e.message}")
            return 0
        }

        if (entries.isEmpty()) {
            Log.w(TAG, "no entries found")
            listener?.onError("未找到可用频道")
            return 0
        }

        M3uParser.buildAndWrite(context, entries)
        val count = entries.map { it.name }.toSet().size
        Log.i(TAG, "done, $count channels")
        listener?.onFinished(count)
        return count
    }
}
