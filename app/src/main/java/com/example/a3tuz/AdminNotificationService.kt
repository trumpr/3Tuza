package com.example.a3tuz

import android.app.*
import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.a3tuz.api.SocketManager
import io.socket.client.Socket

class AdminNotificationService : Service() {

    private var mediaPlayer: MediaPlayer? = null
    private val CHANNEL_ID = "AdminNotifications"
    private val NOTIFICATION_ID = 101

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (action == "STOP_SOUND") {
            stopSound()
        } else {
            startForegroundService()
            setupSocketListener()
        }
        return START_STICKY
    }

    private fun startForegroundService() {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Admin Panel Aktivdir")
            .setContentText("Yeni sorğular üçün gözlənilir...")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()

        startForeground(NOTIFICATION_ID, notification)
    }

    private fun setupSocketListener() {
        val socket = SocketManager.getSocket()
        socket?.on("notification") { args ->
            val data = args[0] as? org.json.JSONObject
            val title = data?.optString("title") ?: "YENİ BİLDİRİŞ 🚨"
            val msg = data?.optString("message") ?: "Yeni sorğu gəldi."
            val type = data?.optString("type") ?: ""
            
            if (type == "new_user") {
                playShortSound(R.raw.yenigelen)
            } else if (type == "high_balance") {
                playShortSound(R.raw.bal15)
            } else if (title.contains("Sorğu", ignoreCase = true)) {
                playLoopingSound(R.raw.zeng)
            }

            showNewRequestNotification(title, msg)
        }
    }

    private fun playLoopingSound(resId: Int) {
        if (mediaPlayer == null) {
            mediaPlayer = MediaPlayer.create(this, resId)
            mediaPlayer?.isLooping = true
        }
        if (mediaPlayer?.isPlaying == false) {
            mediaPlayer?.start()
        }
    }

    private fun playShortSound(resId: Int) {
        try {
            val mp = MediaPlayer.create(this, resId)
            mp.setOnCompletionListener { it.release() }
            mp.start()
        } catch (e: Exception) { e.printStackTrace() }
    }

    private fun stopSound() {
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null
        
        // Səs dayandıqdan sonra normal bildirişə qayıt
        startForegroundService()
    }

    private fun showNewRequestNotification(title: String, messageText: String) {
        val stopIntent = Intent(this, AdminNotificationService::class.java).apply {
            action = "STOP_SOUND"
        }
        val stopPendingIntent = PendingIntent.getService(this, 0, stopIntent, PendingIntent.FLAG_IMMUTABLE)

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(messageText)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setFullScreenIntent(null, true)
            .addAction(R.drawable.ic_launcher_foreground, "SƏSİ KƏS", stopPendingIntent)
            .build()

        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, notification)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "Admin Bildirişləri",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Yeni sorğular gələndə səslə xəbərdar edir"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        mediaPlayer?.release()
        SocketManager.getSocket()?.off("notification")
        super.onDestroy()
    }
}
