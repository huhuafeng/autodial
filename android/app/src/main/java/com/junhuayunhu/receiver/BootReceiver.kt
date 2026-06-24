package com.junhuayunhu.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.junhuayunhu.service.MainService
import com.junhuayunhu.utils.FileLogger

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val logger = FileLogger(context)
            logger.i("BootRcvr", "BOOT_COMPLETED received, starting service")
            context.startForegroundService(Intent(context, MainService::class.java))
        }
    }
}
