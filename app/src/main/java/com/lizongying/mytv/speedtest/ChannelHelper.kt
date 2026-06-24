package com.lizongying.mytv.speedtest

object ChannelHelper {

    // ── 分组 ──────────────────────────────────────────────────────

    fun baseGroup(name: String): String {
        val upper = name.uppercase()
        return when {
            upper.contains("CCTV") -> "央视频道"
            name.contains("卫视")  -> "卫视频道"
            else                   -> "其他频道"
        }
    }

    // ── Logo URL ──────────────────────────────────────────────────

    private const val LOGO_BASE =
        "https://ghfast.top/https://raw.githubusercontent.com/Jarrey/iptv_logo/main/tv/"

    fun buildLogoUrl(name: String): String {
        val encoded = name.toByteArray(Charsets.UTF_8)
            .joinToString("") { b ->
                val i = b.toInt() and 0xFF
                if (i in 0x41..0x5A || i in 0x61..0x7A || i in 0x30..0x39
                    || i == 0x2D || i == 0x5F || i == 0x2E || i == 0x7E
                ) {
                    i.toChar().toString()
                } else {
                    "%%%02X".format(i)
                }
            }
        return "$LOGO_BASE$encoded.png"
    }

    // ── 频道名清洗（port of clean_channel_name in channel.rs）─────

    private val reCctvNum   = Regex("""CCTV(\d+)台""", RegexOption.IGNORE_CASE)
    private val reCctvExtract = Regex("""CCTV(\d{1,2})(\+)?""", RegexOption.IGNORE_CASE)
    private val reWeishiExtract = Regex("""([\u4e00-\u9fff]+卫视)""")

    fun cleanChannelName(raw: String): String {
        var s = raw
            .replace("cctv", "CCTV")
            .replace("中央", "CCTV")
            .replace("央视", "CCTV")

        for (rep in listOf("高清", "超高", "HD", "标清", "频道", "-", " ", "(", ")")) {
            s = s.replace(rep, "")
        }
        s = s.replace("PLUS", "+").replace('＋', '+')

        // CCTV数字台 → CCTV数字
        s = reCctvNum.replace(s) { "CCTV${it.groupValues[1]}" }

        // 包含 CCTV+数字 → 标准化
        val cctvMatch = reCctvExtract.find(s)
        if (cctvMatch != null) {
            val num = cctvMatch.groupValues[1].toIntOrNull() ?: 0
            val isPlus = cctvMatch.groupValues[2] == "+"
            if (isPlus && num == 5) return "CCTV5+"
            if (num in 1..17) return "CCTV$num"
            return s.replace("CCTV", "", ignoreCase = true)
        }

        // 包含 XX卫视
        val weiMatch = reWeishiExtract.find(s)
        if (weiMatch != null) return weiMatch.groupValues[1]

        // 含 CCTV 但没合法编号
        if (s.uppercase().contains("CCTV")) {
            return s.replace("CCTV", "", ignoreCase = true)
        }

        return s
    }

    // ── 排序 key ──────────────────────────────────────────────────

    private val reCctvNum2 = Regex("""CCTV(\d+)""")

    private val weishiOrder = listOf(
        "湖南卫视", "东方卫视", "浙江卫视", "江苏卫视", "北京卫视",
        "山东卫视", "河南卫视", "广东卫视", "安徽卫视", "深圳卫视",
        "天津卫视", "江西卫视", "四川卫视", "湖北卫视", "重庆卫视",
        "黑龙江卫视", "辽宁卫视", "河北卫视", "吉林卫视", "山西卫视",
        "广西卫视", "云南卫视", "福建东南卫视", "贵州卫视", "陕西卫视",
        "甘肃卫视", "内蒙古卫视", "新疆卫视", "宁夏卫视", "青海卫视",
        "西藏卫视", "海南卫视", "兵团卫视",
    )

    // 返回 (category 0央视/1卫视/2其他, subOrder, name)
    fun sortKey(name: String): Triple<Int, Double, String> {
        val upper = name.uppercase()
        if (upper.contains("CCTV")) {
            if (upper.contains("5+")) return Triple(0, 5.5, "")
            val m = reCctvNum2.find(upper)
            if (m != null) {
                val num = m.groupValues[1].toDoubleOrNull() ?: 999.0
                return Triple(0, num, "")
            }
            return Triple(0, 999.0, "")
        }
        if (name.contains("卫视")) {
            val idx = weishiOrder.indexOfFirst { name.contains(it) }
            return if (idx >= 0) Triple(1, idx.toDouble(), name)
            else Triple(1, weishiOrder.size.toDouble(), name)
        }
        return Triple(2, 0.0, name)
    }
}
