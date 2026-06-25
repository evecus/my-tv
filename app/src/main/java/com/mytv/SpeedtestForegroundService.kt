package com.github.mytv

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder

/**
 * 前台 Service，在测速子进程运行期间持有前台优先级。
 *
 * Android 12+ 引入了 PhantomProcess 机制（一个 App 最多允许 32 个子进程），
 * 超出限制或系统判定资源紧张时，会随机杀死不在前台关联范围内的子进程
 * （典型就是 ProcessBuilder fork 出来的原生二进制）。即使执行路径和权限
 * 都正确，测速过程中进程也可能被系统直接终止，表现为莫名其妙的非正常
 * 退出码。运行一个前台 Service 能让 App 进入前台优先级状态，从而让
 * 子进程免于被 PhantomProcessKiller 终止。
 *
 * 使用方式：
 *   startService(Intent(context, SpeedtestForegroundService::class.java)
 *       .setAction(SpeedtestForegroundService.ACTION_START))
 *   // 测速完成后
 *   startService(Intent(context, SpeedtestForegroundService::class.java)
 *       .setAction(SpeedtestForegroundService.ACTION_STOP))
 */
class SpeedtestForegroundService : Service() {

    companion object {
        private const val CHANNEL_ID = "mytv_speedtest"
        private const val NOTIF_ID   = 9201
        const val ACTION_START = "start"
        const val ACTION_STOP  = "stop"
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startForeground(NOTIF_ID, buildNotification())
            ACTION_STOP  -> {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val mgr = getSystemService(NotificationManager::class.java)
            if (mgr.getNotificationChannel(CHANNEL_ID) == null) {
                mgr.createNotificationChannel(
                    NotificationChannel(
                        CHANNEL_ID,
                        "频道测速",
                        NotificationManager.IMPORTANCE_LOW
                    ).apply { description = "测速运行期间显示" }
                )
            }
        }
    }

    private fun buildNotification(): Notification =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_popup_sync)
                .setContentTitle("MyTV")
                .setContentText("正在测速，请稍候…")
                .setOngoing(true)
                .build()
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
                .setSmallIcon(android.R.drawable.ic_popup_sync)
                .setContentTitle("MyTV")
                .setContentText("正在测速，请稍候…")
                .setOngoing(true)
                .build()
        }
}
