package com.github.mytv.models

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class TVViewModel(private var tv: TV) : ViewModel() {

    private var rowPosition: Int = 0
    private var itemPosition: Int = 0

    var retryTimes = 0
    var retryMaxTimes = 8

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

    val sourceCount: Int get() = tv.videoUrl.size
    val currentSourceIndex: Int get() = tv.sourceIndex

    fun switchSource(delta: Int) {
        val total = sourceCount
        if (total <= 1) return
        val newIndex = tv.sourceIndex + delta
        when {
            newIndex < 0 -> {
                _sourceChanged.value = Pair(0, total)
                return
            }
            newIndex >= total -> {
                _sourceChanged.value = Pair(-1, total)
                return
            }
            else -> {
                tv.sourceIndex = newIndex
                _videoIndex.value = tv.sourceIndex
                _videoUrl.value = tv.videoUrl
                _sourceChanged.value = Pair(tv.sourceIndex + 1, total)
                changed("source")
            }
        }
    }

    fun addVideoUrl(url: String) {
        tv.videoUrl += listOf(url)
        _videoUrl.value = tv.videoUrl
        _videoIndex.value = tv.videoUrl.lastIndex
    }

    fun changed(from: String) {
        retryTimes = 0
        _change.value = from
    }

    fun allReady() {
        _ready.value = true
    }

    init {
        _videoUrl.value = tv.videoUrl
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
        val idx = _videoIndex.value ?: 0
        return urls.getOrElse(idx) { urls.firstOrNull() ?: "" }
    }

    companion object {
        private const val TAG = "TVViewModel"
    }
}
