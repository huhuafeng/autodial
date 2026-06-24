package com.bohaoliandong.service

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import okhttp3.*
import java.util.concurrent.TimeUnit

class WebSocketManager(private val context: Context) {

    private var webSocket: WebSocket? = null
    private var reconnectAttempt = 0
    private val handler = Handler(Looper.getMainLooper())
    private var reconnectRunnable: Runnable? = null
    private var pingRunnable: Runnable? = null

    var onDialRequest: ((phone: String, callSession: String) -> Unit)? = null
    var onConnectionStateChange: ((connected: Boolean) -> Unit)? = null

    private val client = OkHttpClient.Builder()
        .pingInterval(30, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    fun connect(url: String) {
        val request = Request.Builder().url(url).build()
        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(ws: WebSocket, response: Response) {
                reconnectAttempt = 0
                send(com.google.gson.Gson().toJson(mapOf("type" to "register", "role" to "phone")))
                onConnectionStateChange?.invoke(true)
                startPing()
            }

            override fun onMessage(ws: WebSocket, text: String) {
                try {
                    val msg = com.google.gson.Gson().fromJson(text, com.bohaoliandong.model.WsMessage::class.java)
                    if (msg.type == "dial") {
                        onDialRequest?.invoke(msg.phone ?: "", msg.callSession ?: "")
                    }
                } catch (_: Exception) {}
            }

            override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                onConnectionStateChange?.invoke(false)
                scheduleReconnect(url)
            }

            override fun onClosed(ws: WebSocket, code: Int, reason: String) {
                onConnectionStateChange?.invoke(false)
                scheduleReconnect(url)
            }
        })
    }

    fun send(json: String) {
        webSocket?.send(json)
    }

    fun disconnect() {
        stopPing()
        handler.removeCallbacksAndMessages(null)
        webSocket?.close(1000, "bye")
        webSocket = null
    }

    private fun scheduleReconnect(url: String) {
        val delay = (1 shl minOf(reconnectAttempt, 5)) * 1000L
        reconnectAttempt++
        reconnectRunnable = Runnable { connect(url) }
        handler.postDelayed(reconnectRunnable!!, delay)
    }

    private fun startPing() {
        stopPing()
        pingRunnable = Runnable { webSocket?.send(com.google.gson.Gson().toJson(mapOf("type" to "ping"))) }
        handler.postDelayed(pingRunnable!!, 25000)
    }

    private fun stopPing() {
        pingRunnable?.let { handler.removeCallbacks(it) }
        pingRunnable = null
    }
}
