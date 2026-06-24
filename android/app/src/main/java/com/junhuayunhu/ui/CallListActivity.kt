package com.junhuayunhu.ui

import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.junhuayunhu.R
import com.junhuayunhu.model.CallRecord
import com.junhuayunhu.service.ApiClient
import com.junhuayunhu.utils.ConfigManager
import java.text.SimpleDateFormat
import java.util.*

class CallListActivity : AppCompatActivity() {

    private lateinit var api: ApiClient
    private val records = mutableListOf<CallRecord>()
    private var page = 1
    private var loading = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_call_list)

        val wsUrl = ConfigManager(this).wsUrl
        val baseUrl = wsUrl.replace("ws://", "http://").replace("wss://", "https://")
        api = ApiClient(baseUrl)

        val rv = findViewById<RecyclerView>(R.id.rv_records)
        rv.layoutManager = LinearLayoutManager(this)
        rv.adapter = RecordAdapter(records)

        findViewById<Button>(R.id.btn_load_more).setOnClickListener { loadPage() }
        loadPage()
    }

    private fun loadPage() {
        if (loading) return; loading = true
        api.getRecords(page) { result ->
            runOnUiThread {
                if (result != null) {
                    records.addAll(result.list)
                    findViewById<RecyclerView>(R.id.rv_records).adapter?.notifyDataSetChanged()
                    page++
                    findViewById<TextView>(R.id.tv_page_info).text = "已加载 ${records.size}/${result.total} 条"
                }
                loading = false
            }
        }
    }
}

class RecordAdapter(private val data: List<CallRecord>) :
    RecyclerView.Adapter<RecordAdapter.VH>() {

    private val fmt = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())

    class VH(val root: android.view.View) : RecyclerView.ViewHolder(root)

    override fun onCreateViewHolder(p: android.view.ViewGroup, t: Int): VH {
        val v = android.view.LayoutInflater.from(p.context)
            .inflate(android.R.layout.simple_list_item_2, p, false)
        return VH(v)
    }

    override fun onBindViewHolder(h: VH, i: Int) {
        val r = data[i]
        h.root.findViewById<android.widget.TextView>(android.R.id.text1).text = r.phone
        h.root.findViewById<android.widget.TextView>(android.R.id.text2).text =
            "${fmt.format(Date(r.timestamp))}  ·  ${r.duration}秒  ·  ${statusCn(r.status)}"
    }

    override fun getItemCount() = data.size

    private fun statusCn(s: String) = when (s) {
        "answered" -> "已接通"
        "ended" -> "未接通"
        "calling" -> "呼叫中"
        else -> s
    }
}
