package com.lizongying.mytv.models

import android.content.Context
import com.lizongying.mytv.speedtest.M3uParser

object TVList {

    /** group名 → TV列表，保持插入顺序 */
    var list: Map<String, List<TV>> = emptyMap()
        private set

    fun load(context: Context): Int {
        val parsed = M3uParser.readLocal(context)
        if (parsed.isEmpty()) {
            list = emptyMap()
            return 0
        }

        val newList = linkedMapOf<String, List<TV>>()
        var globalId = 0

        parsed.forEach { (group, channels) ->
            // 同名频道合并：name → List<url>（保持速度排序，已在 M3uParser.buildAndWrite 完成）
            val byName = linkedMapOf<String, MutableList<String>>()
            for (ch in channels) {
                byName.getOrPut(ch.name) { mutableListOf() }.add(ch.url)
            }

            val tvs = byName.map { (name, urls) ->
                val first = channels.first { it.name == name }
                TV(
                    id          = globalId++,
                    title       = name,
                    alias       = name,
                    videoUrl    = urls,          // 多个源
                    channel     = group,
                    logo        = first.logo,
                    pid         = "",
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
