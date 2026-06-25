package com.lizongying.mytv.models

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.lizongying.mytv.api.FEPG
import com.lizongying.mytv.proto.Ysp.cn.yangshipin.omstv.common.proto.programModel.Program
import com.tencent.videolite.android.datamodel.cctvjce.TVProgram
import java.text.SimpleDateFormat
import java.util.TimeZone

class TVViewModel(private var tv: TV) : ViewModel() {

    private var rowPosition: Int = 0
    private var itemPosition: Int = 0

    var retryTimes = 0
    var retryMaxTimes = 8
    var authYSPRetryTimes = 0
    var authYSPRetryMaxTimes = 3
    var tokenYSPRetryTimes = 0
    var tokenYSPRetryMaxTimes = 0
    var tokenFHRetryTimes = 0
    var tokenFHRetryMaxTimes = 8

    var needGetToken = false

    private val _errInfo = MutableLiveData<String>()
    val errInfo: LiveData<String> get() = _errInfo

    private var _epg = MutableLiveData<MutableList<EPG>>()
    val epg: LiveData<MutableList<EPG>> get() = _epg

    private val _videoUrl = MutableLiveData<List<String>>()
    val videoUrl: LiveData<List<String>> get() = _videoUrl

    private val _videoIndex = MutableLiveData<Int>()
    val videoIndex: LiveData<Int> get() = _videoIndex

    private val _change = MutableLiveData<String>()
    val change: LiveData<String> get() = _change

    private val _ready = MutableLiveData<Boolean>()
    val ready: LiveData<Boolean> get() = _ready

    // 源切换通知：first=当前源编号(1-based)，边界提示时 first=0(已是第一个源) 或 -1(已是最后一个源); second=总源数(快照)
    private val _sourceChanged = MutableLiveData<Pair<Int, Int>>()
    val sourceChanged: LiveData<Pair<Int, Int>> get() = _sourceChanged

    var seq = 0

    /** 源的总数 */
    val sourceCount: Int get() = tv.videoUrl.size

    /** 当前源下标（0-based） */
    val currentSourceIndex: Int get() = tv.sourceIndex

    /**
     * 切换源：delta = +1 下一个，-1 上一个，循环。
     * 切换后触发播放并通知 InfoFragment 显示"源 x/n"。
     */
    /**
     * 切换源：delta = +1 下一个，-1 上一个。
     * 到达边界时不切换，通过 sourceChanged 发出边界信号：
     *   first = 0  → 已是第一个源（左键越界）
     *   first = -1 → 已是最后一个源（右键越界）
     * 正常切换时 first = 当前1-based编号，second = 总源数快照。
     */
    fun switchSource(delta: Int) {
        val total = sourceCount   // 快照，避免 addVideoUrl 竞态导致 total 变化
        if (total <= 1) return
        val newIndex = tv.sourceIndex + delta
        when {
            newIndex < 0 -> {
                // 已经是第一个源，不切换，发出边界提示
                _sourceChanged.value = Pair(0, total)
                return
            }
            newIndex >= total -> {
                // 已经是最后一个源，不切换，发出边界提示
                _sourceChanged.value = Pair(-1, total)
                return
            }
            else -> {
                tv.sourceIndex = newIndex
                _videoIndex.value = tv.sourceIndex
                _videoUrl.value   = tv.videoUrl
                _sourceChanged.value = Pair(tv.sourceIndex + 1, total)
                changed("source")
            }
        }
    }

    fun addVideoUrl(url: String) {
        if (_videoUrl.value?.isNotEmpty() == true) {
            if (_videoUrl.value!!.last().contains("cctv.cn")) {
                tv.videoUrl = tv.videoUrl.subList(0, tv.videoUrl.lastIndex) + listOf(url)
            } else {
                tv.videoUrl += listOf(url)
            }
        } else {
            tv.videoUrl += listOf(url)
        }
        _videoUrl.value  = tv.videoUrl
        _videoIndex.value = tv.videoUrl.lastIndex
    }

    fun changed(from: String) {
        retryTimes = 0
        authYSPRetryTimes = 0
        tokenYSPRetryTimes = 0
        tokenFHRetryTimes = 0
        _change.value = from
    }

    fun allReady() {
        _ready.value = true
    }

    init {
        _videoUrl.value  = tv.videoUrl
        _videoIndex.value = tv.sourceIndex
    }

    fun getRowPosition(): Int = rowPosition
    fun getItemPosition(): Int = itemPosition
    fun setRowPosition(position: Int) { rowPosition = position }
    fun setItemPosition(position: Int) { itemPosition = position }
    fun setErrInfo(info: String) { _errInfo.value = info }
    fun getTV(): TV = tv

    fun getVideoUrlCurrent(): String {
        val urls = _videoUrl.value ?: return ""
        val idx  = _videoIndex.value ?: 0
        return urls.getOrElse(idx) { urls.firstOrNull() ?: "" }
    }

    fun addYJceEPG(p: MutableList<TVProgram>) {
        _epg.value = p.map { EPG(it.name, it.start_time_stamp.toInt()) }.toMutableList()
    }

    fun addYEPG(p: MutableList<Program>) {
        _epg.value = p.map { EPG(it.name, it.st.toInt()) }.toMutableList()
    }

    private fun formatFTime(s: String): Int {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss")
        dateFormat.timeZone = TimeZone.getTimeZone("UTC")
        val date = dateFormat.parse(s.substring(0, 19))
        return if (date != null) (date.time / 1000).toInt() else 0
    }

    fun addFEPG(p: List<FEPG>) {
        _epg.value = p.map { EPG(it.title, formatFTime(it.event_time)) }.toMutableList()
    }

    companion object {
        private const val TAG = "TVViewModel"
    }
}
