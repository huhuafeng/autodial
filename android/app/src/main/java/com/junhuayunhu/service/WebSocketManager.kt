package com.junhuayunhu.service

import android.content.Context
import com.junhuayunhu.utils.FileLogger
import okhttp3.*
import java.util.concurrent.TimeUnit

class WebSocketManager(context: Context, private val logger: FileLogger) {

    private var webSocket: WebSocket? = null
    private var reconnectAttempt = 0
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private var reconnectRunnable: Runnable? = null

    var agentId: String = ""
    var token: String = ""
    var onDialRequest: ((phone: String, callSession: String) -> Unit)? = null
    var onConnectionStateChange: ((connected: Boolean) -> Unit)? = null
    var onAuthState: ((ok: Boolean, msg: String?) -> Unit)? = null

    private val client = OkHttpClient.Builder()
        .pingInterval(30, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    private val gson = com.google.gson.Gson()

    fun connect(url: String) {
        logger.i("WSMgr", "connecting to $url")
        val request = Request.Builder().url(url).build()
        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(ws: WebSocket, response: Response) {
                reconnectAttempt = 0
                logger.i("WSMgr", "connected")
                ws.send(gson.toJson(mapOf("type" to "register", "role" to "phone")))
                // send auth
                if (agentId.isNotEmpty() && token.isNotEmpty()) {
                    ws.send(gson.toJson(mapOf("type" to "auth", "agentId" to agentId, "token" to token)))
                    logger.i("WSMgr", "auth sent for $agentId")
                }
                onConnectionStateChange?.invoke(true)
            }

            override fun onMessage(ws: WebSocket, text: String) {
                logger.i("WSMgr", "recv: $text")
                try {
                    val msg = gson.fromJson(text, com.junhuayunhu.model.WsMessage::class.java)
                    when (msg.type) {
                        "auth_ok" -> onAuthState?.invoke(true, msg.agentId)
                        "auth_error" -> onAuthState?.invoke(false, msg.message)
                        "dial" -> onDialRequest?.invoke(msg.phone ?: "", msg.callSession ?: "")
                    }
                } catch (e: Exception) {
                    logger.e("WSMgr", "parse error: ${e.message}")
                }
            }

            override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                logger.e("WSMgr", "failure: ${t.message}")
                onConnectionStateChange?.invoke(false)
                scheduleReconnect(url)
            }

            override fun onClosed(ws: WebSocket, code: Int, reason: String) {
                logger.i("WSMgr", "closed: $code $reason")
                onConnectionStateChange?.invoke(false)
                scheduleReconnect(url)
            }
        })
    }

    fun send(json: String) {
        logger.i("WSMgr", "send: $json")
        webSocket?.send(json)
    }

    fun disconnect() {
        logger.i("WSMgr", "disconnect")
        handler.removeCallbacksAndMessages(null)
        webSocket?.close(1000, "bye")
        webSocket = null
    }

    private fun scheduleReconnect(url: String) {
        val delay = (1 shl minOf(reconnectAttempt, 5)) * 1000L
        reconnectAttempt++
        logger.i("WSMgr", "reconnect in ${delay}ms (attempt $reconnectAttempt)")
        reconnectRunnable = Runnable { connect(url) }
        handler.postDelayed(reconnectRunnable!!, delay)
    }
}
