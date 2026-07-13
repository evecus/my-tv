package com.github.mytv.speedtest

import android.content.Context
import java.io.File

/**
 * 读取 Rust 端写出的本地 m3u8 文件，转换为播放侧数据结构。
 * 测速、整理、去重、排序全部由 Rust 完成，此处只做解析。
 */
object M3uParser {

    const val OUTPUT_FILENAME = "iptv_sources.m3u8"

    data class ParsedChannel(
        val name: String,
        val url: String,
        val group: String,
        val logo: String,
    )

    /** 读取本地 m3u8，按分组返回频道列表（保持 Rust 写入的顺序） */
    fun readLocal(context: Context): Map<String, List<ParsedChannel>> {
        val file = File(context.filesDir, OUTPUT_FILENAME)
        if (!file.exists()) return emptyMap()

        val result = linkedMapOf<String, MutableList<ParsedChannel>>()
        var currentName  = ""
        var currentGroup = ""
        var currentLogo  = ""

        for (line in file.readLines()) {
            val l = line.trim()
            when {
                l.startsWith("#EXTINF") -> {
                    currentName  = extractAttr(l, "tvg-name") ?: run {
                        val idx = l.lastIndexOf(',')
                        if (idx >= 0) l.substring(idx + 1).trim() else ""
                    }
                    currentGroup = extractAttr(l, "group-title") ?: "其他频道"
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
        val key   = "$attr=\""
        val start = line.indexOf(key) + key.length
        if (start < key.length) return null
        val end = line.indexOf('"', start)
        if (end < 0) return null
        return line.substring(start, end)
    }
}
