package com.junhuayunhu.upload

import android.content.Context
import com.junhuayunhu.BuildConfig
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

class UploadManager(private val context: Context) {

    var uploadUrl: String = BuildConfig.UPLOAD_URL

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    fun upload(filePath: String, callSession: String, phone: String = "", onResult: (Boolean, String?) -> Unit) {
        val file = File(filePath)
        if (!file.exists()) {
            onResult(false, "file not found")
            return
        }

        val mime = "audio/mpeg".toMediaType()
        val fileBody = file.asRequestBody(mime)

        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("file", file.name, fileBody)
            .addFormDataPart("callSession", callSession)
            .addFormDataPart("phone", phone.ifEmpty { "no-phone" })
            .build()

        val request = Request.Builder()
            .url(uploadUrl)
            .post(body)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onResponse(call: Call, response: Response) {
                val ok = response.isSuccessful
                val msg = if (ok) response.body?.string() else response.message
                onResult(ok, msg)
            }

            override fun onFailure(call: Call, e: IOException) {
                onResult(false, e.message)
            }
        })
    }
}
