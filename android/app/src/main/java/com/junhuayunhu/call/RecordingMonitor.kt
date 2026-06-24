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
    private val uploadedSessions = mutableSetOf<String>()

    var onRecordingFound: ((filePath: String) -> Unit)? = null

    private fun markerFile(path: String): String = path + ".uploaded"

    private fun isAlreadyUploaded(path: String): Boolean = java.io.File(markerFile(path)).exists()

    private fun markUploaded(path: String) {
        try { java.io.File(markerFile(path)).createNewFile() } catch (_: Exception) {}
    }

    fun waitForRecording(
        phone: String,
        callSession: String,
        timeoutMs: Long = 30000,
        onResult: (String?) -> Unit
    ) {
        if (uploadedSessions.contains(callSession)) {
            logger.i("RecordMon", "skip duplicate session=$callSession")
            onResult(null)
            return
        }

        val path = config.callRecPath
        val keyword = config.keywordTemplate
        val suffix = config.suffix
        val deadline = System.currentTimeMillis() + timeoutMs

        logger.i("RecordMon", "waitForRecording phone=$phone session=$callSession path=$path")

        Thread {
            var found: String? = null
            while (System.currentTimeMillis() < deadline && found == null) {
                val dir = File(path)
                if (dir.exists()) {
                    found = searchByKeyword(dir, phone, keyword, suffix)
                        ?: searchByTime(dir, suffix)
                    if (found != null && isAlreadyUploaded(found)) {
                        logger.i("RecordMon", "skip already uploaded: $found")
                        found = null
                        Thread.sleep(1000)
                        continue
                    }
                    // verify file is stable (not still being written)
                    if (found != null && !isFileStable(found)) {
                        logger.i("RecordMon", "file not stable yet, wait...")
                        found = null
                        Thread.sleep(1000)
                    }
                }
                if (found == null) Thread.sleep(1000)
            }
            logger.i("RecordMon", if (found != null) "found: $found" else "not found after ${timeoutMs}ms")
            if (found != null) {
                uploadedSessions.add(callSession)
                markUploaded(found)
                onRecordingFound?.invoke(found)
            }
            onResult(found)
        }.start()
    }

    private fun isFileStable(filePath: String): Boolean {
        val file = File(filePath)
        if (!file.exists()) return false
        if (file.length() < 5120) return false // min 5KB
        val size1 = file.length()
        Thread.sleep(1500)
        val size2 = file.length()
        val diff = Math.abs(size2 - size1)
        val stable = diff.toFloat() / size1.toFloat() < 0.05f // < 5% change = stable
        logger.i("RecordMon", "stability check: ${file.name} size1=$size1 size2=$size2 diff=$diff stable=$stable")
        return stable
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
                return files.maxByOrNull { it.lastModified() }?.absolutePath
            }
        }
        return null
    }

    private fun searchByTime(dir: File, suffix: String): String? {
        val files = dir.listFiles { f -> f.name.endsWith(suffix, ignoreCase = true) }
            ?.sortedByDescending { it.lastModified() }
        if (files.isNullOrEmpty()) return null
        val candidate = files.first()
        val isRecent = System.currentTimeMillis() - candidate.lastModified() < 8000
        val isBigEnough = candidate.length() > 2048
        return if (isRecent && isBigEnough) candidate.absolutePath else null
    }

    private fun incrementTime(time: String): String {
        return try {
            val fmt = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
            val d = fmt.parse(time) ?: return time
            fmt.format(Date(d.time + 1000))
        } catch (_: Exception) { time }
    }
}
