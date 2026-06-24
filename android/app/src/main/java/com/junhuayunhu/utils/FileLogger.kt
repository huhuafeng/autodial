package com.junhuayunhu.utils

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class FileLogger(context: Context) {

    private val logDir = File(context.filesDir, "logs")
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())
    private val dayFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    init {
        if (!logDir.exists()) logDir.mkdirs()
        trimOldLogs()
    }

    private fun logFile(): File {
        val today = dayFormat.format(Date())
        return File(logDir, "autodial_$today.log")
    }

    fun i(tag: String, msg: String) = write("I", tag, msg)
    fun w(tag: String, msg: String) = write("W", tag, msg)
    fun e(tag: String, msg: String) = write("E", tag, msg)

    private fun write(level: String, tag: String, msg: String) {
        val line = "${dateFormat.format(Date())} $level/$tag: $msg"
        Log.i("AutoDial/$tag", msg)
        try {
            FileWriter(logFile(), true).use { it.write("$line\n") }
        } catch (_: Exception) {}
    }

    private fun trimOldLogs(maxFiles: Int = 7) {
        val files = logDir.listFiles { f -> f.name.startsWith("autodial_") }?.sortedDescending() ?: return
        if (files.size > maxFiles) {
            files.drop(maxFiles).forEach { it.delete() }
        }
    }
}
