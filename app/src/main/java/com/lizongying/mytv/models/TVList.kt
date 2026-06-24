package com.lizongying.mytv.models

import android.content.Context
import com.lizongying.mytv.speedtest.M3uParser

/**
 * 频道列表。
 * 原来的杨视频硬编码频道已移除，改为从本地 iptv_sources.m3u8 加载。
 * 调用 load(context) 后 list 才有数据。
 */
object TVList {

    /** group名 → TV列表，保持插入顺序（对应 m3u8 里 group-title 的顺序） */
    var list: Map<String, List<TV>> = emptyMap()
        private set

    /**
     * 从本地缓存 m3u8 加载频道列表。
     * 在 MainFragment.onActivityCreated 之前调用。
     * @return 加载到的频道总数
     */
    fun load(context: Context): Int {
        val parsed = M3uParser.readLocal(context)   // Map<group, List<ParsedChannel>>
        if (parsed.isEmpty()) {
            list = emptyMap()
            return 0
        }

        val newList = linkedMapOf<String, List<TV>>()
        var globalId = 0

        parsed.forEach { (group, channels) ->
            val tvs = channels.mapIndexed { _, ch ->
                TV(
                    id          = globalId++,
                    title       = ch.name,
                    alias       = ch.name,
                    videoUrl    = listOf(ch.url),  // 直接可播放，不需要动态 token
                    channel     = group,
                    logo        = ch.logo,
                    pid         = "",              // 无杨视频 pid
                    sid         = "",
                    programType = ProgramType.CUSTOM,
                    needToken   = false,
                    mustToken   = false,
                    volume      = 0.5F,
                )
            }
            if (tvs.isNotEmpty()) newList[group] = tvs
        }

        list = newList
        return globalId
    }

    fun isEmpty() = list.isEmpty()
}
