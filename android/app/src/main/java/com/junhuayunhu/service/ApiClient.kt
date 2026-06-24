package com.junhuayunhu.service

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.junhuayunhu.model.*
import okhttp3.*
import java.io.IOException
import java.util.concurrent.TimeUnit

class ApiClient(private val baseUrl: String) {

    private val gson = Gson()
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    fun getStats(onResult: (StatsResult?) -> Unit) {
        get("/api/stats") { body ->
            onResult(body?.let { gson.fromJson(it, StatsResult::class.java) })
        }
    }

    fun getRecords(page: Int = 1, limit: Int = 20, onResult: (RecordListResult?) -> Unit) {
        get("/api/records?page=$page&limit=$limit") { body ->
            onResult(body?.let { gson.fromJson(it, RecordListResult::class.java) })
        }
    }

    fun syncRecords(records: List<CallRecord>, onResult: (SyncResult?) -> Unit) {
        val json = gson.toJson(records)
        val body = RequestBody.create(MediaType.parse("application/json"), json)
        val request = Request.Builder().url("$baseUrl/api/records/sync").post(body).build()
        client.newCall(request).enqueue(callback { resp ->
            val result = resp?.let { gson.fromJson(it, SyncResult::class.java) }
            onResult(result)
        })
    }

    fun pullRecords(since: Long, onResult: (PullResult?) -> Unit) {
        get("/api/records/pull?since=$since") { body ->
            onResult(body?.let { gson.fromJson(it, PullResult::class.java) })
        }
    }

    private fun get(path: String, onResult: (String?) -> Unit) {
        val request = Request.Builder().url("$baseUrl$path").get().build()
        client.newCall(request).enqueue(callback { onResult(it) })
    }

    private fun callback(onResponse: (String?) -> Unit): Callback {
        return object : Callback {
            override fun onResponse(call: Call, response: Response) {
                onResponse(response.body?.string())
            }

            override fun onFailure(call: Call, e: IOException) {
                onResponse(null)
            }
        }
    }
}
