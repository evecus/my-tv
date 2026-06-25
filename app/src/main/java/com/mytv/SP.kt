package com.github.mytv

import android.content.Context
import android.content.SharedPreferences

object SP {
    // Name of the sp file TODO Should use a meaningful name and do migrations
    private const val SP_FILE_NAME = "MainActivity"

    // If Change channel with up and down in reversed order or not
    private const val KEY_CHANNEL_REVERSAL = "channel_reversal"

    // If use channel num to select channel or not
    private const val KEY_CHANNEL_NUM = "channel_num"

    const val KEY_TIME = "time"

    // If start app on device boot or not
    private const val KEY_BOOT_STARTUP = "boot_startup"

    const val KEY_GRID = "grid"

    // Position in list of the selected channel item
    private const val KEY_POSITION = "position"

    // guid
    private const val KEY_GUID = "guid"

    // 是否启动后自动测速
    const val KEY_AUTO_SPEEDTEST = "auto_speedtest"

    // 播放器内核选择
    const val KEY_PLAYER_ENGINE = "player_engine"
    const val PLAYER_ENGINE_EXO = "exo"
    const val PLAYER_ENGINE_IJK = "ijk"

    // 上次测速时间（时间戳毫秒，0 表示从未测过）
    private const val KEY_LAST_SPEEDTEST = "last_speedtest"

    private lateinit var sp: SharedPreferences

    private var listener: OnSharedPreferenceChangeListener? = null

    /**
     * The method must be invoked as early as possible(At least before using the keys)
     */
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
        set(value) {
            if (value != this.time) {
                sp.edit().putBoolean(KEY_TIME, value).apply()
                listener?.onSharedPreferenceChanged(KEY_TIME)
            }
        }

    var bootStartup: Boolean
        get() = sp.getBoolean(KEY_BOOT_STARTUP, false)
        set(value) = sp.edit().putBoolean(KEY_BOOT_STARTUP, value).apply()

    var grid: Boolean
        get() = sp.getBoolean(KEY_GRID, false)
        set(value) {
            if (value != this.grid) {
                sp.edit().putBoolean(KEY_GRID, value).apply()
                listener?.onSharedPreferenceChanged(KEY_GRID)
            }
        }

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

    var playerEngine: String
        get() = sp.getString(KEY_PLAYER_ENGINE, PLAYER_ENGINE_EXO) ?: PLAYER_ENGINE_EXO
        set(value) {
            if (value != playerEngine) {
                sp.edit().putString(KEY_PLAYER_ENGINE, value).apply()
                listener?.onSharedPreferenceChanged(KEY_PLAYER_ENGINE)
            }
        }
}
