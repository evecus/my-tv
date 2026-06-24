package com.lizongying.mytv.speedtest

import android.content.Context
import java.io.File

object M3uParser {

    // ── 解析订阅文件（m3u 或 txt 两种格式）───────────────────────

    fun parse(content: String): List<IptvChannel> {
        val trimmed = content.trimStart()
        return if (trimmed.startsWith("#EXTM3U")) parseM3u(trimmed)
        else parseTxt(trimmed)
    }

    private fun parseM3u(content: String): List<IptvChannel> {
        val channels = mutableListOf<IptvChannel>()
        var pending = ""
        for (line in content.lines()) {
            val l = line.trim()
            when {
                l.startsWith("#EXTINF") -> {
                    val idx = l.lastIndexOf(',')
                    if (idx >= 0) pending = l.substring(idx + 1).trim()
                }
                l.isNotEmpty() && !l.startsWith('#') && pending.isNotEmpty() -> {
                    channels += IptvChannel(name = pending, url = l)
                    pending = ""
                }
            }
        }
        return channels
    }

    private fun parseTxt(content: String): List<IptvChannel> {
        val channels = mutableListOf<IptvChannel>()
        for (line in content.lines()) {
            val l = line.trim()
            if (l.isEmpty() || l.startsWith('#')) continue
            val parts = l.split(',', limit = 2)
            if (parts.size == 2) {
                val name = parts[0].trim()
                val url  = parts[1].trim()
                if (name.isNotEmpty() && url.isNotEmpty() && !url.contains("#genre#")) {
                    channels += IptvChannel(name = name, url = url)
                }
            }
        }
        return channels
    }

    // ── 构建单条 m3u8 条目字符串 ──────────────────────────────────

    fun buildEntry(name: String, url: String, speed: Double): String {
        val group = ChannelHelper.baseGroup(name)
        val logo  = ChannelHelper.buildLogoUrl(name)
        return "#EXTINF:-1 tvg-name=\"$name\" tvg-logo=\"$logo\" group-title=\"$group\",$name\n$url"
    }

    // ── 聚合所有条目 → 去重排序 → 写入本地 m3u8 文件 ─────────────

    private val GROUPS = listOf("央视频道", "卫视频道", "其他频道")
    const val OUTPUT_FILENAME = "iptv_sources.m3u8"

    fun buildAndWrite(context: Context, entries: List<IptvEntry>) {
        // 按频道名聚合
        val byName = mutableMapOf<String, MutableList<IptvEntry>>()
        for (e in entries) byName.getOrPut(e.name) { mutableListOf() }.add(e)

        // 每个频道内部：去重 + 按速度降序
        for (list in byName.values) {
            val seen = mutableSetOf<String>()
            list.retainAll { seen.add(it.url) }
            list.sortWith(compareByDescending<IptvEntry> { it.speed }.thenBy { it.sourceIndex })
        }

        // 频道名排序
        val allNames = byName.keys.sortedWith(Comparator { a, b ->
            val (a0, a1, a2) = ChannelHelper.sortKey(a)
            val (b0, b1, b2) = ChannelHelper.sortKey(b)
            val c0 = a0.compareTo(b0)
            if (c0 != 0) return@Comparator c0
            val c1 = a1.compareTo(b1)
            if (c1 != 0) return@Comparator c1
            a2.compareTo(b2)
        })

        // 输出 m3u8
        val sb = StringBuilder()
        sb.appendLine("#EXTM3U")
        for (grp in GROUPS) {
            for (name in allNames) {
                if (ChannelHelper.baseGroup(name) != grp) continue
                val list = byName[name] ?: continue
                for (e in list) sb.appendLine(e.m3u8Line)
            }
        }

        val file = File(context.filesDir, OUTPUT_FILENAME)
        file.writeText(sb.toString())
    }

    // ── 读取本地 m3u8 → TV 数据结构 ──────────────────────────────

    data class ParsedChannel(
        val name: String,
        val url: String,
        val group: String,
        val logo: String,
    )

    fun readLocal(context: Context): Map<String, List<ParsedChannel>> {
        val file = File(context.filesDir, OUTPUT_FILENAME)
        if (!file.exists()) return emptyMap()

        val result = linkedMapOf<String, MutableList<ParsedChannel>>()
        var currentName = ""
        var currentGroup = ""
        var currentLogo = ""

        for (line in file.readLines()) {
            val l = line.trim()
            when {
                l.startsWith("#EXTINF") -> {
                    currentName  = extractAttr(l, "tvg-name") ?: run {
                        val idx = l.lastIndexOf(',')
                        if (idx >= 0) l.substring(idx + 1).trim() else ""
                    }
                    currentGroup = extractAttr(l, "group-title") ?: ChannelHelper.baseGroup(currentName)
                    currentLogo  = extractAttr(l, "tvg-logo") ?: ""
                }
                l.isNotEmpty() && !l.startsWith('#') && currentName.isNotEmpty() -> {
                    val ch = ParsedChannel(currentName, l, currentGroup, currentLogo)
                    result.getOrPut(currentGroup) { mutableListOf() }.add(ch)
                    currentName = ""
                }
            }
        }
        return result
    }

    fun hasLocalSource(context: Context): Boolean =
        File(context.filesDir, OUTPUT_FILENAME).let { it.exists() && it.length() > 0 }

    private fun extractAttr(line: String, attr: String): String? {
        val key = "$attr=\""
        val start = line.indexOf(key) + key.length
        if (start < key.length) return null
        val end = line.indexOf('"', start)
        if (end < 0) return null
        return line.substring(start, end)
    }
}

// 扩展属性：IptvEntry → m3u8 单行
private val IptvEntry.m3u8Line: String
    get() = M3uParser.buildEntry(name, url, speed) // buildEntry 返回两行，已含 \n
