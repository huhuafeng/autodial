package com.bohaoliandong.call

import android.content.Context
import android.os.Environment
import java.io.File

class RecordingMonitor(private val context: Context) {

    var recordingPath: String = DEFAULT_RECORDING_PATH
    var onRecordingFound: ((filePath: String) -> Unit)? = null

    fun waitForRecording(timeoutMs: Long = 30000, onResult: (String?) -> Unit) {
        val deadline = System.currentTimeMillis() + timeoutMs
        Thread {
            var found: String? = null
            while (System.currentTimeMillis() < deadline && found == null) {
                val dir = File(recordingPath)
                if (dir.exists()) {
                    val files = dir.listFiles()
                        ?.filter { it.name.endsWith(".mp3") || it.name.endsWith(".amr") }
                        ?.sortedByDescending { it.lastModified() }
                    if (files != null && files.isNotEmpty()) {
                        found = files[0].absolutePath
                    }
                }
                if (found == null) {
                    Thread.sleep(1000)
                }
            }
            if (found != null) {
                onRecordingFound?.invoke(found)
            }
            onResult(found)
        }.start()
    }

    companion object {
        const val DEFAULT_RECORDING_PATH =
            "/storage/emulated/0/MIUI/sound_recorder/call_rec"
    }
}
