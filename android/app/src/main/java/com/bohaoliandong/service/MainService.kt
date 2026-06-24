package com.bohaoliandong.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.telephony.TelephonyManager
import com.bohaoliandong.App
import com.bohaoliandong.BuildConfig
import com.bohaoliandong.R
import com.bohaoliandong.call.CallHandler
import com.bohaoliandong.call.RecordingMonitor
import com.bohaoliandong.upload.UploadManager

class MainService : Service() {

    private lateinit var wsManager: WebSocketManager
    private lateinit var callHandler: CallHandler
    private lateinit var recordingMonitor: RecordingMonitor
    private lateinit var uploadManager: UploadManager
    private var wsConnected = false
    private var currentCallSession: String? = null

    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIFICATION_ID, buildNotification(getString(R.string.service_connecting)))

        wsManager = WebSocketManager(this)
        callHandler = CallHandler(this)
        recordingMonitor = RecordingMonitor(this)
        uploadManager = UploadManager(this)

        wsManager.onDialRequest = { phone, callSession ->
            currentCallSession = callSession
            sendWsStatus("calling", phone, callSession)
            callHandler.dial(phone, callSession)
        }

        wsManager.onConnectionStateChange = { connected ->
            wsConnected = connected
            val text = if (connected) getString(R.string.service_running)
                       else getString(R.string.service_disconnected)
            updateNotification(text)
        }

        callHandler.onCallStateChanged = { state, phone ->
            when (state) {
                TelephonyManager.CALL_STATE_OFFHOOK -> {
                    sendWsStatus("answered", phone)
                }
                TelephonyManager.CALL_STATE_IDLE -> {
                    val session = callHandler.getCallSession() ?: return@onCallStateChanged
                    sendWsStatus("ended", phone)
                    recordingMonitor.waitForRecording { path ->
                        if (path != null) {
                            uploadManager.upload(path, session) { ok, _ ->
                                if (ok) {
                                    wsManager.send(toJson(mapOf(
                                        "type" to "record_ready",
                                        "callSession" to session,
                                        "fileName" to path.substringAfterLast('/')
                                    )))
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        wsManager.connect(BuildConfig.WS_URL)
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        wsManager.disconnect()
        callHandler.cleanup()
        super.onDestroy()
    }

    private fun sendWsStatus(status: String, phone: String?, session: String? = null) {
        val msg = mutableMapOf("type" to "status", "status" to status)
        if (phone != null) msg["phone"] = phone
        if (session != null) msg["callSession"] = session
        wsManager.send(toJson(msg))
    }

    private fun toJson(map: Map<String, String?>): String {
        val sb = StringBuilder("{")
        map.entries.forEachIndexed { i, (k, v) ->
            if (i > 0) sb.append(",")
            sb.append("\"$k\":")
            sb.append(if (v != null) "\"${v.replace("\"", "\\\"")}\"" else "null")
        }
        sb.append("}")
        return sb.toString()
    }

    private fun buildNotification(text: String): Notification {
        val pi = packageManager.getLaunchIntentForPackage(packageName)?.let {
            PendingIntent.getActivity(this, 0, it, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        }
        return Notification.Builder(this, App.CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pi)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(text: String) {
        val nm = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
        nm.notify(NOTIFICATION_ID, buildNotification(text))
    }

    companion object {
        const val NOTIFICATION_ID = 1
    }
}
