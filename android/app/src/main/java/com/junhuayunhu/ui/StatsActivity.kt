package com.junhuayunhu.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.junhuayunhu.R
import com.junhuayunhu.model.StatsDay
import com.junhuayunhu.service.ApiClient
import com.junhuayunhu.utils.ConfigManager
import java.text.SimpleDateFormat
import java.util.*

class StatsActivity : AppCompatActivity() {

    private lateinit var api: ApiClient
    private val labels = listOf("通话量", "通话时长", "平均时长")
    private val qualityLabels = listOf("接通量", "40秒+通话", "接通率")
    private var autoRefreshRunnable: Runnable? = null
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_stats)

        val wsUrl = ConfigManager(this).wsUrl
        val baseUrl = wsUrl.replace("ws://", "http://").replace("wss://", "https://")
        api = ApiClient(baseUrl)

        findViewById<TextView>(R.id.tv_date).text =
            SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

        findViewById<Button>(R.id.btn_refresh).setOnClickListener { loadStats() }
        findViewById<Button>(R.id.btn_call_list).setOnClickListener {
            startActivity(Intent(this, CallListActivity::class.java))
        }

        loadStats()
    }

    override fun onResume() {
        super.onResume()
        autoRefreshRunnable?.let { handler.removeCallbacks(it) }
        autoRefreshRunnable = Runnable { loadStats() }
        handler.postDelayed(autoRefreshRunnable!!, 30000)
    }

    override fun onPause() {
        super.onPause()
        autoRefreshRunnable?.let { handler.removeCallbacks(it) }
    }

    private fun loadStats() {
        findViewById<LinearLayout>(R.id.layout_loading).visibility = android.view.View.VISIBLE
        findViewById<LinearLayout>(R.id.layout_error).visibility = android.view.View.GONE
        findViewById<LinearLayout>(R.id.layout_stats).visibility = android.view.View.GONE

        api.getStats { result ->
            runOnUiThread {
                findViewById<LinearLayout>(R.id.layout_loading).visibility = android.view.View.GONE
                if (result != null) {
                    findViewById<LinearLayout>(R.id.layout_stats).visibility = android.view.View.VISIBLE
                    render(result.today, result.yesterday)
                } else {
                    findViewById<LinearLayout>(R.id.layout_error).visibility = android.view.View.VISIBLE
                }
            }
        }
    }

    private fun render(today: StatsDay, yesterday: StatsDay) {
        val todayVals = listOf(
            "${today.dialout}",
            formatDuration(today.callLong),
            formatDuration(today.avg)
        )
        val yestVals = listOf(
            "${yesterday.dialout}",
            formatDuration(yesterday.callLong),
            formatDuration(yesterday.avg)
        )
        bindRows(R.id.group_calls, labels, todayVals, yestVals, today, yesterday)

        val tVals = listOf(
            "${today.connect}",
            "${today.connect40}",
            "${today.connectRate}%"
        )
        val yVals = listOf(
            "${yesterday.connect}",
            "${yesterday.connect40}",
            "${yesterday.connectRate}%"
        )
        bindRows(R.id.group_quality, qualityLabels, tVals, yVals, today, yesterday)
    }

    private fun bindRows(groupId: Int, labels: List<String>, todayVals: List<String>,
                         yestVals: List<String>, today: StatsDay, yesterday: StatsDay) {
        val group = findViewById<LinearLayout>(groupId)
        group.removeAllViews()
        val inflater = LayoutInflater.from(this)
        labels.forEachIndexed { i, label ->
            val row = inflater.inflate(R.layout.item_stat_row, group, false)
            row.findViewById<TextView>(R.id.tv_label).text = label
            row.findViewById<TextView>(R.id.tv_today).text = todayVals[i]
            row.findViewById<TextView>(R.id.tv_yesterday).text = yestVals[i]

            val arrow = row.findViewById<TextView>(R.id.tv_arrow)
            val diff = getDiff(i, today, yesterday)
            arrow.text = when {
                diff > 0 -> "↑"
                diff < 0 -> "↓"
                else -> "—"
            }
            arrow.setTextColor(if (diff >= 0) 0xFF52C41A.toInt() else 0xFFFF4D4F.toInt())

            group.addView(row)
            if (i < labels.size - 1) {
                val div = android.view.View(this).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, 1).apply {
                        marginStart = dp(16)
                    }
                    setBackgroundColor(0xFFF0F0F0.toInt())
                }
                group.addView(div)
            }
        }
    }

    private fun getDiff(index: Int, today: StatsDay, yesterday: StatsDay): Int {
        val t = listOf(today.dialout, today.callLong, today.avg, today.connect, today.connect40, today.connectRate)
        val y = listOf(yesterday.dialout, yesterday.callLong, yesterday.avg, yesterday.connect, yesterday.connect40, yesterday.connectRate)
        return t[index] - y[index]
    }

    private fun formatDuration(seconds: Int): String {
        if (seconds < 60) return "${seconds}秒"
        val min = seconds / 60
        val sec = seconds % 60
        return if (sec > 0) "${min}分${sec}秒" else "${min}分"
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()
}
