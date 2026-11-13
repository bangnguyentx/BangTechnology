package com.bangtech.optimize

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.*
import androidx.core.app.NotificationCompat

class RealtimeRamService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private var targetThresholdMb = 4800L
    private val channelId = "bang_realtime_ram_channel"

    private val updateRunnable = object : Runnable {
        override fun run() {
            updateNotification()
            handler.postDelayed(this, 3000L)
        }
    }

    override fun onCreate() {
        super.onCreate()
        val channel = NotificationChannel(
            channelId,
            "BangOptimize Realtime RAM",
            NotificationManager.IMPORTANCE_LOW
        ).apply { setShowBadge(false) }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        startForeground(1001, buildNotification(0, 0))
        handler.post(updateRunnable)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.getLongExtra("threshold_mb", -1L)?.let {
            if (it > 0) targetThresholdMb = it
        }
        if (intent?.action == "ACTION_CLEAN_NOW") {
            MainActivity.runShizukuGlobal("am kill-all && pm trim-caches 999G")
            updateNotification()
        }
        return START_STICKY
    }

    private fun updateNotification() {
        val am = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        am.getMemoryInfo(memInfo)
        val totalMb = memInfo.totalMem / (1024 * 1024)
        val usedMb = (memInfo.totalMem - memInfo.availMem) / (1024 * 1024)

        if (usedMb >= targetThresholdMb) {
            MainActivity.runShizukuGlobal("am kill-all && pm trim-caches 999G")
        }
        getSystemService(NotificationManager::class.java)
            .notify(1001, buildNotification(usedMb, totalMb))
    }

    private fun buildNotification(usedMb: Long, totalMb: Long): Notification {
        val openIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        val cleanIntent = PendingIntent.getService(
            this, 1,
            Intent(this, RealtimeRamService::class.java).apply { action = "ACTION_CLEAN_NOW" },
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .setContentTitle("BangOptimize • Realtime RAM • vừa xong")
            .setContentText("RAM: $usedMb MB / $totalMb MB (Mốc: $targetThresholdMb MB)")
            .setContentIntent(openIntent)
            .addAction(android.R.drawable.ic_menu_delete, "⚡ Dọn Ngay", cleanIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
    }

    override fun onDestroy() {
        handler.removeCallbacks(updateRunnable)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
