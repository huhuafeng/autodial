package com.junhuayunhu.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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
    private var total = 0
    private var loading = false
    private lateinit var adapter: RecordAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_call_list)

        val wsUrl = ConfigManager(this).wsUrl
        val baseUrl = wsUrl.replace("ws://", "http://").replace("wss://", "https://")
        api = ApiClient(baseUrl)

        adapter = RecordAdapter(records)
        findViewById<RecyclerView>(R.id.rv_records).apply {
            layoutManager = LinearLayoutManager(this@CallListActivity)
            adapter = this@CallListActivity.adapter
        }

        findViewById<Button>(R.id.btn_load_more).setOnClickListener { loadPage() }
        loadPage()
    }

    private fun loadPage() {
        if (loading) return; loading = true
        findViewById<Button>(R.id.btn_load_more).isEnabled = false
        api.getRecords(page) { result ->
            runOnUiThread {
                if (result != null) {
                    records.addAll(result.list)
                    total = result.total
                    adapter.notifyDataSetChanged()
                    page++
                    findViewById<TextView>(R.id.tv_page_info).text = "已加载 ${records.size}/${total} 条"
                    findViewById<TextView>(R.id.tv_empty).visibility =
                        if (records.isEmpty()) View.VISIBLE else View.GONE
                }
                loading = false
                findViewById<Button>(R.id.btn_load_more).isEnabled =
                    records.size < total
            }
        }
    }
}

class RecordAdapter(private val data: List<CallRecord>) :
    RecyclerView.Adapter<RecordAdapter.VH>() {

    private val fmt = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())

    class VH(val root: View) : RecyclerView.ViewHolder(root)

    override fun onCreateViewHolder(p: ViewGroup, t: Int): VH {
        val v = LayoutInflater.from(p.context)
            .inflate(R.layout.item_call_record, p, false)
        return VH(v)
    }

    override fun onBindViewHolder(h: VH, i: Int) {
        val r = data[i]
        h.root.findViewById<TextView>(R.id.tv_phone).text = r.phone

        val badge = h.root.findViewById<TextView>(R.id.tv_status_badge)
        badge.text = if (r.status == "answered") "已接通" else "未接通"
        badge.setBackgroundResource(
            if (r.status == "answered") android.graphics.drawable.GradientDrawable().apply {
                setColor(0xFF52C41A.toInt()); cornerRadius = 9f; shape = android.graphics.drawable.GradientDrawable.RECTANGLE
            } else android.graphics.drawable.GradientDrawable().apply {
                setColor(0xFFC0C4CC.toInt()); cornerRadius = 9f; shape = android.graphics.drawable.GradientDrawable.RECTANGLE
            }
        )

        h.root.findViewById<TextView>(R.id.tv_time).text = fmt.format(Date(r.timestamp))
        h.root.findViewById<TextView>(R.id.tv_duration).text = formatDur(r.duration)
    }

    override fun getItemCount() = data.size

    private fun formatDur(s: Int): String {
        if (s < 60) return "${s}秒"
        return "${s / 60}分${s % 60}秒"
    }
}
