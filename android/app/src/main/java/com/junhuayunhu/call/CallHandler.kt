package com.junhuayunhu.call

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.telephony.PhoneStateListener
import android.telephony.TelephonyManager

class CallHandler(private val context: Context) {

    var onCallStateChanged: ((state: Int, phone: String?) -> Unit)? = null
    private var currentPhone: String? = null
    private var currentCallSession: String? = null
    private var callStartTime: Long = 0
    private var callAnswered = false
    private val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager

    private val listener = object : PhoneStateListener() {
        override fun onCallStateChanged(state: Int, incomingNumber: String?) {
            when (state) {
                TelephonyManager.CALL_STATE_OFFHOOK -> {
                    if (!callAnswered) {
                        callAnswered = true
                        callStartTime = System.currentTimeMillis()
                    }
                }
                TelephonyManager.CALL_STATE_IDLE -> {
                    callAnswered = false
                }
            }
            onCallStateChanged?.invoke(state, currentPhone)
        }
    }

    fun dial(phone: String, callSession: String) {
        currentPhone = phone
        currentCallSession = callSession
        callStartTime = 0
        callAnswered = false
        val intent = Intent(Intent.ACTION_CALL, Uri.parse("tel:$phone")).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        telephonyManager.listen(listener, PhoneStateListener.LISTEN_CALL_STATE)
    }

    fun getCallSession(): String? = currentCallSession

    fun getDuration(): Int {
        return if (callStartTime > 0) ((System.currentTimeMillis() - callStartTime) / 1000).toInt() else 0
    }

    fun cleanup() {
        telephonyManager.listen(listener, PhoneStateListener.LISTEN_NONE)
    }
}
