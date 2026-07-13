package com.github.mytv.models

import java.io.Serializable

data class TV(
    var id: Int = 0,
    var title: String,
    var alias: String = "",
    var videoUrl: List<String>,
    var sourceIndex: Int = 0,    // 当前正在播放第几个源（0-based）
    var channel: String = "",
    var logo: Any = "",
    var pid: String = "",
    var sid: String = "",
    var programType: ProgramType,
    var needToken: Boolean = false,
    var mustToken: Boolean = false,
    var volume: Float = 0.1F,
) : Serializable {

    override fun toString(): String {
        return "TV{id=$id, title='$title', sources=${videoUrl.size}, sourceIndex=$sourceIndex}"
    }
}
