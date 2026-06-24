package com.bohaoliandong.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.telephony.TelephonyManager
import com.bohaoliandong.App
import com.bohaoliandong.R
import com.bohaoliandong.call.CallHandler
import com.bohaoliandong.call.RecordingMonitor
import com.bohaoliandong.upload.UploadManager
import com.bohaoliandong.utils.ConfigManager
import com.bohaoliandong.utils.FileLogger

class MainService : Service() {

    private lateinit var config: ConfigManager
    private lateinit var logger: FileLogger
    private lateinit var wsManager: WebSocketManager
    private lateinit var callHandler: CallHandler
    private lateinit var recordingMonitor: RecordingMonitor
    private lateinit var uploadManager: UploadManager
    private var wsConnected = false

    override fun onCreate() {
        super.onCreate()
        config = ConfigManager(this)
        logger = FileLogger(this)
        startForeground(NOTIFICATION_ID, buildNotification(getString(R.string.service_connecting)))

        wsManager = WebSocketManager(this, logger)
        callHandler = CallHandler(this)
        recordingMonitor = RecordingMonitor(this)
        uploadManager = UploadManager(this)

        uploadManager.uploadUrl = config.uploadUrl

        wsManager.onDialRequest = { phone, callSession ->
            logger.i("MainSvc", "dial request phone=$phone session=$callSession")
            sendWsStatus("calling", phone, callSession)
            callHandler.dial(phone, callSession)
        }

        wsManager.onConnectionStateChange = { connected ->
            wsConnected = connected
            val text = if (connected) getString(R.string.service_running)
                       else getString(R.string.service_disconnected)
            updateNotification(text)
            logger.i("MainSvc", "WS ${if (connected) "connected" else "disconnected"}")
        }

        callHandler.onCallStateChanged = { state, phone ->
            logger.i("MainSvc", "call state changed: state=$state phone=$phone")
            when (state) {
                TelephonyManager.CALL_STATE_OFFHOOK -> {
                    sendWsStatus("answered", phone)
                }
                TelephonyManager.CALL_STATE_IDLE -> {
                    callHandler.getCallSession()?.let { session ->
                        sendWsStatus("ended", phone)
                        recordingMonitor.waitForRecording(phone ?: "") { path ->
                            logger.i("MainSvc", "recording result: $path")
                            if (path != null) {
                                uploadManager.upload(path, session) { ok, msg ->
                                    logger.i("MainSvc", "upload result: ok=$ok msg=$msg")
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
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val url = config.wsUrl
        logger.i("MainSvc", "starting with WS_URL=$url")
        wsManager.connect(url)
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        logger.i("MainSvc", "service destroyed")
        wsManager.disconnect()
        callHandler.cleanup()
        super.onDestroy()
    }

    private fun sendWsStatus(status: String, phone: String?, session: String? = null) {
        val msg = mutableMapOf("type" to "status", "status" to status)
        if (phone != null) msg["phone"] = phone
        if (session != null) msg["callSession"] = session
        val json = toJson(msg)
        logger.i("MainSvc", "send status: $json")
        wsManager.send(json)
    }

    private val gson = com.google.gson.Gson()
    private fun toJson(map: Map<String, String?>): String = gson.toJson(map)

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
