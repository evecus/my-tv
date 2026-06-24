package com.lizongying.mytv.speedtest

import android.content.Context
import android.util.Log
import org.json.JSONArray
import java.io.File

/**
 * 调用打包进 APK 的 Rust 测速二进制。
 *
 * jniLibs 中只有当前 APK 对应架构的 libiptv_speedtest.so，
 * 安装后系统解压到 nativeLibraryDir，固定文件名无需架构判断。
 */
object NativeSpeedtestRunner {

    private const val TAG = "NativeSpeedtestRunner"
    private const val SO_NAME  = "libiptv_speedtest.so"
    private const val BIN_NAME = "iptv_speedtest"

    /**
     * 将 so 从 nativeLibraryDir 复制到 filesDir 并赋予执行权限。
     * 已存在且大小相同时跳过（应对 APK 升级场景用大小判断）。
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
     * 运行 Rust 二进制，输出 JSON 到临时文件，解析后返回频道列表。
     *
     * @param context      Context
     * @param workers      并发数
     * @param top          每类型保留前 N 源
     * @param extraUrls    额外订阅 URL
     * @param logCallback  实时 stderr 日志回调（IO 线程）
     */
    fun run(
        context: Context,
        workers: Int = 60,
        top: Int = 10,
        extraUrls: List<String> = emptyList(),
        logCallback: ((String) -> Unit)? = null,
    ): List<IptvEntry> {
        val binPath = prepare(context)
        val outputFile = File(context.cacheDir, "iptv_speedtest_result.json")
        outputFile.delete()

        val cmd = mutableListOf(
            binPath,
            "android",
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

        // 持续 drain stdout，防止 Rust 进程的 print!/println! 写满管道缓冲区
        // （~64 KB）后在 write() 处永久阻塞，导致测速卡死、流量归零。
        // Rust 侧进度条已改用 eprint!，此处仅做兜底。
        val stdoutThread = Thread {
            try {
                process.inputStream.copyTo(java.io.OutputStream.nullOutputStream())
            } catch (_: Exception) {}
        }
        stdoutThread.isDaemon = true
        stdoutThread.start()

        val exitCode = process.waitFor()
        stderrThread.join(2000)
        stdoutThread.join(500)

        check(exitCode == 0) { "binary exited with code $exitCode" }

        check(outputFile.exists() && outputFile.length() > 0) {
            "output file empty or missing: ${outputFile.absolutePath}"
        }

        val arr = JSONArray(outputFile.readText())
        val entries = mutableListOf<IptvEntry>()
        for (i in 0 until arr.length()) {
            val obj   = arr.getJSONObject(i)
            val name  = obj.optString("name")
            val url   = obj.optString("url")
            val speed = obj.optDouble("speed", -1.0)
            val group = obj.optString("group", ChannelHelper.baseGroup(name))
            if (name.isNotEmpty() && url.isNotEmpty() && speed > 0) {
                entries += IptvEntry(
                    name        = name,
                    url         = url,
                    group       = group,
                    logo        = ChannelHelper.buildLogoUrl(name),
                    speed       = speed,
                    sourceIndex = 0,
                )
            }
        }
        Log.i(TAG, "parsed ${entries.size} entries")
        return entries
    }
}
