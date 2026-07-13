package com.github.mytv

import android.content.Context
import android.content.SharedPreferences

object SP {
    private const val SP_FILE_NAME = "MainActivity"
    private const val KEY_CHANNEL_REVERSAL = "channel_reversal"
    const val KEY_TIME = "time"
    private const val KEY_BOOT_STARTUP = "boot_startup"
    private const val KEY_POSITION = "position"
    private const val KEY_GUID = "guid"
    const val KEY_AUTO_SPEEDTEST = "auto_speedtest"
    private const val KEY_LAST_SPEEDTEST = "last_speedtest"
    const val KEY_GRID = "grid"
    const val KEY_CHANNEL_NUM = "channel_num"

    // 播放器内核：exo / ijk
    const val KEY_PLAYER_TYPE = "player_type"

    // ijk 解码方式：auto / hard / soft
    const val KEY_IJK_DECODER = "ijk_decoder"

    // exo 解码方式：default / software
    const val KEY_EXO_DECODER = "exo_decoder"

    private lateinit var sp: SharedPreferences
    private var listener: OnSharedPreferenceChangeListener? = null

    fun init(context: Context) {
        sp = context.getSharedPreferences(SP_FILE_NAME, Context.MODE_PRIVATE)
    }

    fun setOnSharedPreferenceChangeListener(listener: OnSharedPreferenceChangeListener) {
        this.listener = listener
    }

    var channelReversal: Boolean
        get() = sp.getBoolean(KEY_CHANNEL_REVERSAL, false)
        set(value) = sp.edit().putBoolean(KEY_CHANNEL_REVERSAL, value).apply()

    var channelNum: Boolean
        get() = sp.getBoolean(KEY_CHANNEL_NUM, false)
        set(value) = sp.edit().putBoolean(KEY_CHANNEL_NUM, value).apply()

    var time: Boolean
        get() = sp.getBoolean(KEY_TIME, false)
        set(value) { sp.edit().putBoolean(KEY_TIME, value).apply() }

    var bootStartup: Boolean
        get() = sp.getBoolean(KEY_BOOT_STARTUP, false)
        set(value) = sp.edit().putBoolean(KEY_BOOT_STARTUP, value).apply()

    var grid: Boolean
        get() = sp.getBoolean(KEY_GRID, false)
        set(value) { sp.edit().putBoolean(KEY_GRID, value).apply() }

    var itemPosition: Int
        get() = sp.getInt(KEY_POSITION, 0)
        set(value) = sp.edit().putInt(KEY_POSITION, value).apply()

    var guid: String
        get() = sp.getString(KEY_GUID, "") ?: ""
        set(value) = sp.edit().putString(KEY_GUID, value).apply()

    var autoSpeedtest: Boolean
        get() = sp.getBoolean(KEY_AUTO_SPEEDTEST, false)
        set(value) = sp.edit().putBoolean(KEY_AUTO_SPEEDTEST, value).apply()

    var lastSpeedtest: Long
        get() = sp.getLong(KEY_LAST_SPEEDTEST, 0L)
        set(value) = sp.edit().putLong(KEY_LAST_SPEEDTEST, value).apply()

    /** 播放器内核："exo" 或 "ijk"，默认 "exo" */
    var playerType: String
        get() = sp.getString(KEY_PLAYER_TYPE, "exo") ?: "exo"
        set(value) {
            if (value != this.playerType) {
                sp.edit().putString(KEY_PLAYER_TYPE, value).apply()
                listener?.onSharedPreferenceChanged(KEY_PLAYER_TYPE)
            }
        }

    /** ijk 解码方式："auto" / "hard" / "soft"，默认 "auto" */
    var ijkDecoder: String
        get() = sp.getString(KEY_IJK_DECODER, "auto") ?: "auto"
        set(value) {
            if (value != this.ijkDecoder) {
                sp.edit().putString(KEY_IJK_DECODER, value).apply()
                listener?.onSharedPreferenceChanged(KEY_IJK_DECODER)
            }
        }

    /** exo 解码方式："default" / "software"，默认 "default" */
    var exoDecoder: String
        get() = sp.getString(KEY_EXO_DECODER, "default") ?: "default"
        set(value) {
            if (value != this.exoDecoder) {
                sp.edit().putString(KEY_EXO_DECODER, value).apply()
                listener?.onSharedPreferenceChanged(KEY_EXO_DECODER)
            }
        }
}
