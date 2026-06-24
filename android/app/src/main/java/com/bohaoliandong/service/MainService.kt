package com.bohaoliandong.service

import android.app.Notification
import android.app.Service
import android.content.Intent
import android.os.IBinder
import com.bohaoliandong.App
import com.bohaoliandong.R

class MainService : Service() {

    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIFICATION_ID, buildNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
    }

    private fun buildNotification(): Notification {
        val pendingIntent = packageManager.getLaunchIntentForPackage(packageName)?.let {
            android.app.PendingIntent.getActivity(this, 0, it, android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE)
        }
        return Notification.Builder(this, App.CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.service_running))
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    companion object {
        const val NOTIFICATION_ID = 1
    }
}
