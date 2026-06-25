package com.lizongying.mytv.speedtest

import android.content.Context
import android.util.Log
import java.io.File

/**
 * 调用打包进 APK 的 Rust 测速二进制。
 *
 * Rust 端完成全部测速、整理、去重、排序工作，直接输出 m3u8 文件。
 * Kotlin 侧只负责启动进程、转发进度日志、等待结束。
 */
object NativeSpeedtestRunner {

    private const val TAG = "NativeSpeedtestRunner"
    private const val SO_NAME  = "libiptv_speedtest.so"
    private const val BIN_NAME = "iptv_speedtest"

    /**
     * 将 so 从 nativeLibraryDir 复制到 filesDir 并赋予执行权限。
     * 已存在且大小相同时跳过。
     * @return 可执行文件绝对路径
     */
    fun prepare(context: Context): String {
        val src  = File(context.applicationInfo.nativeLibraryDir, SO_NAME)
        val dest = File(context.filesDir, BIN_NAME)

        if (!src.exists()) {
            error("native binary not found: ${src.absolutePath}")
        }

        if (!dest.exists() || dest.length() != src.length()) {
            Log.i(TAG, "copying binary → ${dest.absolutePath}")
            src.copyTo(dest, overwrite = true)
        }

        if (!dest.canExecute()) {
            dest.setExecutable(true, true)
        }

        return dest.absolutePath
    }

    /**
     * 运行 Rust 二进制，等待其直接写出 m3u8 文件。
     *
     * @param context      Context
     * @param workers      并发数
     * @param top          每类型保留前 N 源
     * @param extraUrls    额外订阅 URL
     * @param logCallback  实时 stderr 日志回调（IO 线程）
     * @return 输出的 m3u8 File（可直接作为播放列表使用）
     */
    fun run(
        context: Context,
        workers: Int = 60,
        top: Int = 10,
        extraUrls: List<String> = emptyList(),
        logCallback: ((String) -> Unit)? = null,
    ): File {
        val binPath    = prepare(context)
        val outputFile = File(context.filesDir, M3uParser.OUTPUT_FILENAME)

        val cmd = mutableListOf(
            binPath,
            "--workers", workers.toString(),
            "--top",     top.toString(),
            "--output",  outputFile.absolutePath,
        )
        for (url in extraUrls) {
            cmd += listOf("--url", url)
        }

        Log.i(TAG, "exec: ${cmd.joinToString(" ")}")

        val process = ProcessBuilder(cmd)
            .redirectErrorStream(false)
            .start()

        // 实时读 stderr 作为进度日志
        val stderrThread = Thread {
            try {
                process.errorStream.bufferedReader().forEachLine { line ->
                    Log.d(TAG, line)
                    logCallback?.invoke(line)
                }
            } catch (_: Exception) {}
        }
        stderrThread.isDaemon = true
        stderrThread.start()

        // drain stdout 防止管道缓冲区满导致 Rust 进程阻塞
        val stdoutThread = Thread {
            try {
                val buf = ByteArray(8192)
                val ins = process.inputStream
                while (ins.read(buf) != -1) { /* drain */ }
            } catch (_: Exception) {}
        }
        stdoutThread.isDaemon = true
        stdoutThread.start()

        val exitCode = process.waitFor()
        stderrThread.join(2000)
        stdoutThread.join(500)

        check(exitCode == 0) { "binary exited with code $exitCode" }

        check(outputFile.exists() && outputFile.length() > 0) {
            "m3u8 output empty or missing: ${outputFile.absolutePath}"
        }

        Log.i(TAG, "m3u8 ready: ${outputFile.absolutePath} (${outputFile.length()} bytes)")
        return outputFile
    }
}
