package com.github.mytv.speedtest

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

    /**
     * 直接定位 nativeLibraryDir 下系统已解压好的二进制，不做任何复制。
     *
     * Android 10 (API 29) 起引入了 W^X 限制：只有 APK 安装时系统解压到
     * nativeLibraryDir 的文件才带有 SELinux 执行权限（apk_data_file 上下
     * 文，可执行）。任何 App 自己复制到 filesDir / cacheDir 等私有目录的
     * 文件，哪怕 chmod 设置了 +x，exec() 时依然会被 SELinux 拦截
     * （EACCES / Permission denied）。低版本 Android 没有这个限制，所以
     * 同一份代码在低版本能跑、在 Android 10+ 上必然失败。
     *
     * 解决办法就是像 adb/fastboot 二进制常见做法一样：不复制，直接从
     * nativeLibraryDir 执行。前提是 build.gradle 里 jniLibs 用
     * useLegacyPackaging（不压缩）打包，否则系统不会把它解压出来。
     *
     * @return 可执行文件绝对路径
     */
    fun prepare(context: Context): String {
        val bin = File(context.applicationInfo.nativeLibraryDir, SO_NAME)

        if (!bin.exists()) {
            error("native binary not found: ${bin.absolutePath}")
        }

        // 理论上系统解压时已经带有执行权限，这里仅做兜底校验/重置，
        // 不涉及"复制到其它目录"这一步。
        if (!bin.canExecute()) {
            bin.setExecutable(true, true)
        }

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
                // nativeLibraryDir 本身只读，二进制内部如果用到 HOME/TMPDIR
                // （临时文件、缓存等），缺省值在 Android 上可能不存在或不可写，
                // 导致进程启动后内部出错退出。显式指到 filesDir/cacheDir——
                // 这两个目录本身可写，W^X 限制的是"执行"，不影响"读写"。
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
