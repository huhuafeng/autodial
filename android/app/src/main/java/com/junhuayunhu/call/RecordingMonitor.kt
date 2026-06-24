package com.junhuayunhu.call

import android.content.Context
import com.junhuayunhu.utils.ConfigManager
import com.junhuayunhu.utils.FileLogger
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class RecordingMonitor(context: Context) {

    private val config = ConfigManager(context)
    private val logger = FileLogger(context)

    var onRecordingFound: ((filePath: String) -> Unit)? = null

    fun waitForRecording(
        phone: String,
        timeoutMs: Long = 30000,
        onResult: (String?) -> Unit
    ) {
        val path = config.callRecPath
        val keyword = config.keywordTemplate
        val suffix = config.suffix
        val deadline = System.currentTimeMillis() + timeoutMs

        logger.i("RecordMon", "waitForRecording phone=$phone path=$path keyword=$keyword suffix=$suffix")

        Thread {
            var found: String? = null
            while (System.currentTimeMillis() < deadline && found == null) {
                val dir = File(path)
                if (dir.exists()) {
                    found = searchByKeyword(dir, phone, keyword, suffix) ?: searchByTime(dir, suffix)
                }
                if (found == null) Thread.sleep(1000)
            }
            logger.i("RecordMon", if (found != null) "found: $found" else "not found after ${timeoutMs}ms")
            if (found != null) onRecordingFound?.invoke(found)
            onResult(found)
        }.start()
    }

    private fun searchByKeyword(dir: File, phone: String, template: String, suffix: String): String? {
        val dateStr = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val last4 = phone.takeLast(4)

        listOf(dateStr, incrementTime(dateStr)).forEach { time ->
            val keyword = template
                .replace("mobile2", last4)
                .replace("mobile", phone)
                .replace("time", time)

            val files = dir.listFiles { f ->
                f.name.contains(keyword, ignoreCase = true) && f.name.endsWith(suffix, ignoreCase = true)
            }
            if (files != null && files.isNotEmpty()) {
                return files.sortedByDescending { it.lastModified() }.first().absolutePath
            }
        }
        return null
    }

    private fun searchByTime(dir: File, suffix: String): String? {
        val files = dir.listFiles { f -> f.name.endsWith(suffix, ignoreCase = true) }
            ?.sortedByDescending { it.lastModified() }
        if (files.isNullOrEmpty()) return null
        // only return if file is < 5 seconds old
        return if (System.currentTimeMillis() - files.first().lastModified() < 5000) files.first().absolutePath
        else null
    }

    private fun incrementTime(time: String): String {
        return try {
            val fmt = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
            val d = fmt.parse(time) ?: return time
            fmt.format(Date(d.time + 1000))
        } catch (_: Exception) { time }
    }
}
