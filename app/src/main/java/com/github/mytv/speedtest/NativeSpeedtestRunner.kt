package com.github.mytv.speedtest

import android.content.Context
import android.util.Log
import java.io.File

/**
 * 调用打包进 APK 的 Rust 测速二进制。
 *
 * 修复说明（Android 10+ 兼容）：
 *   Android 10 引入 W^X 限制，App 私有目录（filesDir/cacheDir）中的文件
 *   即使 setExecutable(true) 也无法被 exec，会报 EACCES / ENOEXEC。
 *   低版本 Android 没有此限制，因此原来的"复制到 filesDir 再执行"只在低版本有效。
 *
 *   正确做法（参考 CableBee）：直接使用系统在安装时写入的 nativeLibraryDir 路径，
 *   该目录由系统管理，已具备可执行权限，无需复制也无需 setExecutable。
 */
object NativeSpeedtestRunner {

    private const val TAG     = "NativeSpeedtestRunner"
    private const val SO_NAME = "libiptv_speedtest.so"

    /**
     * 返回 nativeLibraryDir 中二进制的绝对路径。
     * 不做任何复制或权限修改——nativeLibraryDir 由系统安装时写入，已可执行。
     */
    fun prepare(context: Context): String {
        val bin = File(context.applicationInfo.nativeLibraryDir, SO_NAME)
        check(bin.exists()) { "native binary not found: ${bin.absolutePath}" }
        Log.i(TAG, "binary: ${bin.absolutePath} (${bin.length()} bytes)")
        return bin.absolutePath
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
            .apply {
                // 给二进制提供可写的 HOME/TMPDIR，避免因默认路径不可写而崩溃
                environment()["HOME"]   = context.filesDir.absolutePath
                environment()["TMPDIR"] = context.cacheDir.absolutePath
            }
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
