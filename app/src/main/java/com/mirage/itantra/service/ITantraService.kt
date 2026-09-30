package com.mirage.itantra.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.mirage.itantra.app.MainActivity
import dagger.hilt.android.AndroidEntryPoint

/**
 * Foreground service for maintaining P2P communication
 * when the application is in the background.
 *
 * This service is started when a peer connection is established
 * and stopped when the connection is terminated.
 *
 * Phase 5+ will wire this to the actual transport layer.
 */
@AndroidEntryPoint
class ITantraService : Service() {

    companion object {
        const val CHANNEL_ID = "itantra_communication"
        const val NOTIFICATION_ID = 1001
    }

    private var wakeLock: android.os.PowerManager.WakeLock? = null
    private var wifiLock: android.net.wifi.WifiManager.WifiLock? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        acquireLocks()
    }

    private fun acquireLocks() {
        try {
            val powerManager = getSystemService(android.content.Context.POWER_SERVICE) as android.os.PowerManager
            wakeLock = powerManager.newWakeLock(android.os.PowerManager.PARTIAL_WAKE_LOCK, "ITantra:BackgroundAudioLock")
            wakeLock?.acquire()

            val wifiManager = applicationContext.getSystemService(android.content.Context.WIFI_SERVICE) as android.net.wifi.WifiManager
            wifiLock = wifiManager.createWifiLock(android.net.wifi.WifiManager.WIFI_MODE_FULL_HIGH_PERF, "ITantra:WifiLock")
            wifiLock?.acquire()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = buildNotification()
        startForeground(NOTIFICATION_ID, notification)
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        releaseLocks()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        try {
            stopForeground(true)
            stopSelf()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        // Completely kill the process so singletons (like P2pTransport) are destroyed
        android.os.Process.killProcess(android.os.Process.myPid())
    }

    private fun releaseLocks() {
        try {
            if (wakeLock?.isHeld == true) wakeLock?.release()
            if (wifiLock?.isHeld == true) wifiLock?.release()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "iTantra Communication",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Active P2P communication link"
            setShowBadge(false)
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("iTantra Active")
            .setContentText("P2P communication link active")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }
}
