package com.junhuayunhu.ui

import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.junhuayunhu.R
import com.junhuayunhu.model.StatsDay
import com.junhuayunhu.service.ApiClient
import com.junhuayunhu.utils.ConfigManager

class StatsActivity : AppCompatActivity() {

    private lateinit var api: ApiClient

    private val labels = listOf(
        "通话量" to "dialout",
        "通话时长" to "callLong",
        "平均时长" to "avg",
        "接通量" to "connect",
        "40秒+通话" to "connect40",
        "接通率" to "connectRate",
    )
    private val todayViews = mutableListOf<TextView>()
    private val yestViews = mutableListOf<TextView>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_stats)

        val wsUrl = ConfigManager(this).wsUrl
        val baseUrl = wsUrl.replace("ws://", "http://").replace("wss://", "https://")
        api = ApiClient(baseUrl)

        buildRows()

        findViewById<Button>(R.id.btn_refresh).setOnClickListener { loadStats() }
        findViewById<Button>(R.id.btn_call_list).setOnClickListener {
            startActivity(Intent(this, CallListActivity::class.java))
        }

        loadStats()
    }

    private fun buildRows() {
        val container = findViewById<LinearLayout>(R.id.stats_container)

        // header
        val hdr = inflateRow("指标", "今日", "昨日", true)
        container.addView(hdr, container.indexOfChild(findViewById(R.id.btn_refresh)) - 1)

        // separator
        val sep = android.view.View(this)
        sep.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 1).apply {
            setMargins(0, 0, 0, 0)
        }
        sep.setBackgroundColor(0xFFE8E8E8.toInt())
        container.addView(sep, container.indexOfChild(findViewById(R.id.btn_refresh)) - 1)

        labels.forEach { (label, _) ->
            val tvToday = TextView(this)
            val tvYest = TextView(this)
            val row = inflateRow(label, "", "", false, tvToday, tvYest)
            container.addView(row, container.indexOfChild(findViewById(R.id.btn_refresh)) - 1)
            todayViews.add(tvToday)
            yestViews.add(tvYest)
        }
    }

    private fun inflateRow(
        label: String, today: String, yesterday: String,
        isHeader: Boolean = false,
        tvToday: TextView? = null, tvYest: TextView? = null
    ): android.view.View {
        val row = LinearLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 56)
            gravity = Gravity.CENTER_VERTICAL
            setPadding(12, 0, 12, 0)
            if (!isHeader) setBackgroundColor(0xFFFFFFFF.toInt())

            val tvLbl = TextView(this@StatsActivity).apply {
                layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
                text = label
                textSize = if (isHeader) 12f else 14f
                setTextColor(if (isHeader) 0xFF909399.toInt() else 0xFF606266.toInt())
                if (isHeader) setTextColor(0xFF303133.toInt())
            }
            addView(tvLbl)

            val t = tvToday ?: TextView(this@StatsActivity)
            t.layoutParams = LinearLayout.LayoutParams(0, -2, 1f).apply {
                gravity = Gravity.CENTER
            }
            t.text = today
            t.textSize = if (isHeader) 12f else 16f
            t.gravity = Gravity.CENTER
            t.setTextColor(if (isHeader) 0xFF0090FF.toInt() else 0xFF303133.toInt())
            if (!isHeader) t.setTypeface(null, android.graphics.Typeface.BOLD)
            addView(t)

            val y = tvYest ?: TextView(this@StatsActivity)
            y.layoutParams = LinearLayout.LayoutParams(0, -2, 1f).apply {
                gravity = Gravity.CENTER
            }
            y.text = yesterday
            y.textSize = if (isHeader) 12f else 14f
            y.gravity = Gravity.CENTER
            y.setTextColor(if (isHeader) 0xFF909399.toInt() else 0xFF909399.toInt())
            addView(y)
        }
        return row
    }

    private fun loadStats() {
        api.getStats { result ->
            runOnUiThread {
                if (result != null) {
                    render(result.today, result.yesterday)
                } else {
                    findViewById<TextView>(R.id.tv_title).text = "📊 加载失败，下拉刷新"
                }
            }
        }
    }

    private fun render(today: StatsDay, yesterday: StatsDay) {
        val vals = listOf(
            "${today.dialout}" to "${yesterday.dialout}",
            "${today.callLong}秒" to "${yesterday.callLong}秒",
            "${today.avg}秒" to "${yesterday.avg}秒",
            "${today.connect}" to "${yesterday.connect}",
            "${today.connect40}" to "${yesterday.connect40}",
            "${today.connectRate}%" to "${yesterday.connectRate}%",
        )
        vals.forEachIndexed { i, (t, y) ->
            todayViews[i].text = t
            yestViews[i].text = y
        }
    }
}
