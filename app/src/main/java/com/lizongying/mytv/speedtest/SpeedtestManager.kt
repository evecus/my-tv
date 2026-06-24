package com.lizongying.mytv.speedtest

import android.content.Context
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.withPermit
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.InputStream
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * IPTV 测速管理器
 *
 * 对应 Rust 项目：task.rs + speedtest.rs + subscribe.rs
 *
 * 两路输入源（完全自动，无需用户配置）：
 *   1. https://iptvs.pes.im  —— 全国局域网 IPTV 网关列表（txiptv/jsmpeg/zhgxtv/hsmdtv）
 *   2. DEFAULT_SUB_URL       —— 公开 m3u 订阅文件
 */
object SpeedtestManager {

    private const val TAG = "SpeedtestManager"

    // ── 配置常量（对应 config.rs）────────────────────────────────
    private const val API_URL         = "https://iptvs.pes.im"
    private const val DEFAULT_SUB_URL =
        "http://gh-proxy.com/raw.githubusercontent.com/suxuang/myIPTV/main/ipv4.m3u"

    private const val SPEED_LOW_MBPS  = 0.5          // MB/s 最低可用门槛
    private const val HOST_TIMEOUT_MS = 15_000L       // 单 host 总超时
    private const val SUB_TIMEOUT_MS  = 10_000L       // 订阅文件下载超时
    private const val SPEED_TEST_MS   = 8_000L        // 测速最长持续时间
    private const val MAX_SPEED_BYTES = 10 * 1024 * 1024L  // 测速最多下载 10MB
    private const val WORKERS         = 10            // 并发数
    private const val TOP_N           = 5             // 每种类型最多保留几个源

    // ── 运行状态 ──────────────────────────────────────────────────
    private val isRunning = AtomicBoolean(false)
    fun isRunning() = isRunning.get()

    // ── 进度回调 ──────────────────────────────────────────────────
    interface ProgressListener {
        fun onProgress(completed: Int, total: Int, valid: Int, phase: String)
        fun onFinished(channelCount: Int)
        fun onError(message: String)
    }

    // ── 主入口 ────────────────────────────────────────────────────

    /**
     * 在 IO 协程中调用，阻塞直到完成。
     * @return 写入的频道总数，-1 表示已在运行
     */
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

    private suspend fun doRun(context: Context, listener: ProgressListener?): Int {
        Log.i(TAG, "── speedtest start ──")
        val allEntries = mutableListOf<IptvEntry>()
        var sourceIdx = 0

        // ── Step 1：下载订阅文件（并行）──────────────────────────
        listener?.onProgress(0, 0, 0, "正在下载订阅文件…")
        val subContent = downloadText(DEFAULT_SUB_URL, SUB_TIMEOUT_MS)

        // ── Step 2：请求 API，获取网关列表 ───────────────────────
        listener?.onProgress(0, 0, 0, "正在获取 IPTV 网关列表…")
        val apiItems = fetchApiData()
        Log.i(TAG, "api hosts: ${apiItems.size}")

        // ── Step 3：并发测速所有网关 ──────────────────────────────
        if (apiItems.isNotEmpty()) {
            listener?.onProgress(0, apiItems.size, 0, "正在测速 API 网关…")
            val rawResults = runApiSpeedTests(apiItems, listener)
            val topSources = selectTopSources(rawResults, TOP_N)
            Log.i(TAG, "selected ${topSources.size} api sources")

            for (src in topSources) {
                val channels = fetchChannelsForSource(src)
                val entries = buildEntriesFromChannels(channels, sourceIdx, src.speed)
                allEntries += entries
                sourceIdx++
            }
        }

        // ── Step 4：测速订阅源 ────────────────────────────────────
        if (subContent != null) {
            val channels = M3uParser.parse(subContent)
            Log.i(TAG, "subscribe channels: ${channels.size}")
            if (channels.isNotEmpty()) {
                listener?.onProgress(0, 0, 0, "正在测速订阅源（${channels.size} 个频道）…")
                val hostSpeeds = testSubscribeHosts(channels, listener)
                var added = 0
                for (ch in channels) {
                    val hk = hostKey(ch.url)
                    val spd = hostSpeeds[hk] ?: -1.0
                    if (spd < SPEED_LOW_MBPS) continue
                    val name = ChannelHelper.cleanChannelName(ch.name)
                    allEntries += IptvEntry(
                        name        = name,
                        url         = ch.url,
                        group       = ChannelHelper.baseGroup(name),
                        logo        = ChannelHelper.buildLogoUrl(name),
                        speed       = spd,
                        sourceIndex = sourceIdx,
                    )
                    added++
                }
                Log.i(TAG, "subscribe kept $added / ${channels.size}")
                sourceIdx++
            }
        }

        if (allEntries.isEmpty()) {
            Log.w(TAG, "no entries, keeping existing cache")
            listener?.onError("未找到可用频道，保留原有列表")
            return 0
        }

        // ── Step 5：写入本地 m3u8 ─────────────────────────────────
        M3uParser.buildAndWrite(context, allEntries)
        val count = allEntries.map { it.name }.toSet().size
        Log.i(TAG, "done, $count channels written")
        listener?.onFinished(count)
        return count
    }

    // ── 获取 API 网关列表（对应 fetch_api_data）──────────────────

    private fun fetchApiData(): List<JSONObject> {
        val client = makeClient(10_000L)
        repeat(3) { attempt ->
            try {
                val body = client.newCall(Request.Builder().url(API_URL).build())
                    .execute().use { it.body?.string() } ?: return@repeat
                val root = JSONObject(body)
                val arr = root.optJSONArray("results") ?: return@repeat
                return (0 until arr.length()).map { arr.getJSONObject(it) }
            } catch (e: Exception) {
                Log.w(TAG, "api fetch attempt ${attempt + 1} failed: ${e.message}")
                Thread.sleep(3000)
            }
        }
        return emptyList()
    }

    // ── 并发测速所有 API 网关（对应 run_api_speed_tests）────────

    private suspend fun runApiSpeedTests(
        items: List<JSONObject>,
        listener: ProgressListener?,
    ): List<ApiSourceResult> = coroutineScope {
        val total     = items.size
        val completed = AtomicInteger(0)
        val valid     = AtomicInteger(0)
        val semaphore = kotlinx.coroutines.sync.Semaphore(WORKERS)

        items.map { item ->
            async(Dispatchers.IO) {
                semaphore.withPermit {
                    val host = item.optString("host").trim()
                    val mt   = item.optString("matchType").trim()
                    if (host.isEmpty()) return@withPermit null

                    val (speed, _) = testApiHostSpeed(host, mt, fetchChannels = false)
                    val c = completed.incrementAndGet()
                    val v = if (speed >= SPEED_LOW_MBPS) valid.incrementAndGet()
                            else valid.get()
                    listener?.onProgress(c, total, v, "测速 API 网关")
                    if (speed >= SPEED_LOW_MBPS)
                        ApiSourceResult(host = host, matchType = mt, speed = speed)
                    else null
                }
            }
        }.awaitAll().filterNotNull()
    }

    // ── 为已选源补抓频道列表 ──────────────────────────────────────

    private suspend fun fetchChannelsForSource(src: ApiSourceResult): List<IptvChannel> {
        val (_, channels) = testApiHostSpeed(src.host, src.matchType, fetchChannels = true)
        return channels
    }

    // ── 测单个 API Host（对应 test_api_host_speed）───────────────

    private suspend fun testApiHostSpeed(
        host: String,
        matchType: String,
        fetchChannels: Boolean,
    ): Pair<Double, List<IptvChannel>> = withContext(Dispatchers.IO) {
        val deadline = System.currentTimeMillis() + HOST_TIMEOUT_MS
        when (matchType) {
            "txiptv"  -> testTxiptv(host, deadline, fetchChannels)
            "jsmpeg"  -> testJsmpeg(host, deadline, fetchChannels)
            "zhgxtv"  -> testZhgxtv(host, deadline, fetchChannels)
            "hsmdtv"  -> Pair(testHsmdtv(host, deadline), emptyList())
            else      -> Pair(-1.0, emptyList())
        }
    }

    // ── txiptv ────────────────────────────────────────────────────

    private fun testTxiptv(
        host: String, deadline: Long, fetchChannels: Boolean,
    ): Pair<Double, List<IptvChannel>> {
        val remaining = (deadline - System.currentTimeMillis()).coerceAtLeast(1)
        val url = "http://$host/iptv/live/1000.json?key=txiptv"
        val body = getJson(url, remaining.coerceAtMost(2000)) ?: return Pair(-1.0, emptyList())
        val dataArr = body.optJSONArray("data") ?: return Pair(-1.0, emptyList())

        val channels = mutableListOf<IptvChannel>()
        var firstUrl = ""
        for (i in 0 until dataArr.length()) {
            val d    = dataArr.getJSONObject(i)
            val name = d.optString("name")
            val u    = d.optString("url")
            if (name.isEmpty() || u.isEmpty() || u.contains(',')) continue
            val full = when {
                u.contains("http") -> u
                u.startsWith('/') -> "http://$host$u"
                else              -> "http://$host/$u"
            }
            if (fetchChannels) channels += IptvChannel(name, full)
            if (firstUrl.isEmpty()) firstUrl = full
        }
        if (firstUrl.isEmpty()) return Pair(-1.0, channels)
        val spd = testStreamUrl(firstUrl, deadline)
        return Pair(spd, channels)
    }

    // ── jsmpeg ────────────────────────────────────────────────────

    private fun testJsmpeg(
        host: String, deadline: Long, fetchChannels: Boolean,
    ): Pair<Double, List<IptvChannel>> {
        val remaining = (deadline - System.currentTimeMillis()).coerceAtLeast(1)
        val url = "http://$host/streamer/list"
        val bodyStr = getText(url, remaining.coerceAtMost(2000)) ?: return Pair(-1.0, emptyList())

        // response 是 JSON array
        val arr = try {
            org.json.JSONArray(bodyStr)
        } catch (e: Exception) { return Pair(-1.0, emptyList()) }

        val channels = mutableListOf<IptvChannel>()
        var firstUrl = ""
        for (i in 0 until arr.length()) {
            val d    = arr.getJSONObject(i)
            val name = d.optString("name").trim()
            val key  = d.optString("key").trim()
            if (name.isEmpty() || key.isEmpty()) continue
            val full = "http://$host/hls/$key/index.m3u8"
            if (fetchChannels) channels += IptvChannel(name, full)
            if (firstUrl.isEmpty()) firstUrl = full
        }
        if (firstUrl.isEmpty()) return Pair(-1.0, channels)
        val spd = testStreamUrl(firstUrl, deadline)
        return Pair(spd, channels)
    }

    // ── zhgxtv ───────────────────────────────────────────────────

    private fun testZhgxtv(
        host: String, deadline: Long, fetchChannels: Boolean,
    ): Pair<Double, List<IptvChannel>> {
        val remaining = (deadline - System.currentTimeMillis()).coerceAtLeast(1)
        val url = "http://$host/ZHGXTV/Public/json/live_interface.txt"
        val bodyStr = getText(url, remaining.coerceAtMost(5000)) ?: return Pair(-1.0, emptyList())

        val channels = mutableListOf<IptvChannel>()
        var firstUrl = ""
        for (line in bodyStr.lines()) {
            val l = line.trim()
            if (!l.contains(',')) continue
            val parts = l.split(',', limit = 2)
            if (parts.size < 2) continue
            val name    = parts[0].trim()
            val urlPart = parts[1].trim()
            val full = when {
                urlPart.startsWith("http") -> {
                    try {
                        val p = java.net.URL(urlPart)
                        "http://$host${p.path}${if (p.query != null) "?${p.query}" else ""}"
                    } catch (e: Exception) { continue }
                }
                urlPart.startsWith('/') -> "http://$host$urlPart"
                else                   -> "http://$host/$urlPart"
            }
            if (fetchChannels) channels += IptvChannel(name, full)
            if (firstUrl.isEmpty()) firstUrl = full
        }
        if (firstUrl.isEmpty()) return Pair(-1.0, channels)
        val spd = testStreamUrl(firstUrl, deadline)
        return Pair(spd, channels)
    }

    // ── hsmdtv ────────────────────────────────────────────────────

    private fun testHsmdtv(host: String, deadline: Long): Double {
        val url = "http://$host/newlive/live/hls/1/live.m3u8"
        return testStreamUrl(url, deadline)
    }

    // ── 选 Top N 源（对应 select_top_sources）────────────────────

    private fun selectTopSources(
        results: List<ApiSourceResult>,
        topN: Int,
    ): List<ApiSourceResult> {
        val sorted = results.sortedByDescending { it.speed }
        val selected = mutableListOf<ApiSourceResult>()
        val seenHosts = mutableSetOf<String>()

        // 每种类型至少保留一个
        for (mt in listOf("txiptv", "hsmdtv", "zhgxtv", "jsmpeg")) {
            sorted.firstOrNull { it.matchType == mt && it.host !in seenHosts }
                ?.let { seenHosts += it.host; selected += it }
        }
        // 补充至 topN
        for (r in sorted) {
            if (selected.size >= topN) break
            if (r.host !in seenHosts) { seenHosts += r.host; selected += r }
        }
        return selected.sortedByDescending { it.speed }
    }

    // ── 构建 Entry 列表 ───────────────────────────────────────────

    private fun buildEntriesFromChannels(
        channels: List<IptvChannel>,
        sourceIndex: Int,
        speed: Double,
    ): List<IptvEntry> = channels.map { ch ->
        val name = ChannelHelper.cleanChannelName(ch.name)
        IptvEntry(
            name        = name,
            url         = ch.url,
            group       = ChannelHelper.baseGroup(name),
            logo        = ChannelHelper.buildLogoUrl(name),
            speed       = speed,
            sourceIndex = sourceIndex,
        )
    }

    // ── 订阅源按 Host 测速（对应 test_subscribe_hosts）───────────

    private suspend fun testSubscribeHosts(
        channels: List<IptvChannel>,
        listener: ProgressListener?,
    ): Map<String, Double> = coroutineScope {
        // 每个 host 只测一条代表 URL
        val hostToSample = mutableMapOf<String, IptvChannel>()
        for (ch in channels) {
            hostToSample.putIfAbsent(hostKey(ch.url), ch)
        }
        val total     = hostToSample.size
        val completed = AtomicInteger(0)
        val valid     = AtomicInteger(0)
        val semaphore = kotlinx.coroutines.sync.Semaphore(WORKERS)

        val results = hostToSample.map { (hk, ch) ->
            async(Dispatchers.IO) {
                semaphore.withPermit {
                    val spd = testOneSubscribeUrl(ch.url)
                    val c = completed.incrementAndGet()
                    val v = if (spd >= SPEED_LOW_MBPS) valid.incrementAndGet() else valid.get()
                    listener?.onProgress(c, total, v, "测速订阅源")
                    hk to if (spd >= SPEED_LOW_MBPS) spd else -1.0
                }
            }
        }.awaitAll()

        results.toMap()
    }

    private fun testOneSubscribeUrl(rawUrl: String): Double {
        val deadline = System.currentTimeMillis() + HOST_TIMEOUT_MS
        val lower = rawUrl.lowercase()
        return if (lower.contains(".m3u8") || lower.contains("/hls/") || lower.contains("/live/"))
            testStreamUrl(rawUrl, deadline)
        else
            measureSpeed(rawUrl, deadline)
    }

    // ── 流测速核心（对应 test_stream_url + measure_speed）────────

    /**
     * 下载 m3u8 → 取第一个 ts 分片 → 测速
     */
    private fun testStreamUrl(m3u8Url: String, deadline: Long): Double {
        if (System.currentTimeMillis() > deadline) return -1.0
        val tsUrl = getTsUrl(m3u8Url, deadline) ?: return -1.0
        if (System.currentTimeMillis() > deadline) return -1.0
        return measureSpeed(tsUrl, deadline)
    }

    /**
     * 从 m3u8 内容里取第一个 ts 分片的绝对 URL
     */
    private fun getTsUrl(m3u8Url: String, deadline: Long): String? {
        val remaining = (deadline - System.currentTimeMillis()).coerceAtLeast(1)
        val body = getText(m3u8Url, remaining.coerceAtMost(5000)) ?: return null

        val base   = m3u8Url.substringBeforeLast('/')  + "/"
        val origin = try {
            val u = java.net.URL(m3u8Url)
            "${u.protocol}://${u.host}"
        } catch (e: Exception) { "" }

        for (line in body.lines()) {
            val l = line.trim()
            if (l.isEmpty() || l.startsWith('#')) continue
            return when {
                l.startsWith("http") -> l
                l.startsWith('/')    -> "$origin$l"
                else                 -> "$base$l"
            }
        }
        return null
    }

    /**
     * 下载 streamUrl 若干秒，返回 MB/s
     */
    private fun measureSpeed(streamUrl: String, deadline: Long): Double {
        val remaining = (deadline - System.currentTimeMillis()).coerceAtLeast(1)
        val client = makeClient(remaining.coerceAtMost(10_000L))
        val start  = System.currentTimeMillis()
        var size   = 0L

        try {
            val resp = client.newCall(Request.Builder().url(streamUrl).build()).execute()
            if (!resp.isSuccessful) return -1.0
            val stream: InputStream = resp.body?.byteStream() ?: return -1.0
            val buf = ByteArray(8192)
            stream.use { ins ->
                while (true) {
                    val now = System.currentTimeMillis()
                    if (now > deadline || (now - start) > SPEED_TEST_MS || size > MAX_SPEED_BYTES) break
                    val n = ins.read(buf)
                    if (n < 0) break
                    size += n
                }
            }
        } catch (e: Exception) {
            if (size == 0L) return -1.0
        }

        val dur = ((System.currentTimeMillis() - start).coerceAtLeast(1)) / 1000.0
        return size / 1024.0 / 1024.0 / dur
    }

    // ── HTTP 工具 ─────────────────────────────────────────────────

    private fun makeClient(timeoutMs: Long): OkHttpClient =
        OkHttpClient.Builder()
            .connectTimeout(timeoutMs, TimeUnit.MILLISECONDS)
            .readTimeout(timeoutMs, TimeUnit.MILLISECONDS)
            .build()

    private fun getText(url: String, timeoutMs: Long): String? =
        try {
            makeClient(timeoutMs)
                .newCall(Request.Builder().url(url).build())
                .execute()
                .use { resp -> if (resp.isSuccessful) resp.body?.string() else null }
        } catch (e: Exception) { null }

    private fun getJson(url: String, timeoutMs: Long): JSONObject? =
        getText(url, timeoutMs)?.let {
            try { JSONObject(it) } catch (e: Exception) { null }
        }

    private fun downloadText(url: String, timeoutMs: Long): String? {
        Log.i(TAG, "downloading $url")
        return getText(url, timeoutMs).also {
            if (it == null) Log.w(TAG, "failed to download $url")
            else Log.i(TAG, "downloaded ${it.length} bytes from $url")
        }
    }

    // ── Host key（去掉 path，只保留 scheme+host，用于去重）───────

    fun hostKey(rawUrl: String): String {
        return try {
            val u = java.net.URL(rawUrl)
            "${u.protocol}://${u.host}"
        } catch (e: Exception) { rawUrl }
    }
}
