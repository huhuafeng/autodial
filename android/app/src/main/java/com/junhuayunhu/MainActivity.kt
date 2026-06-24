package com.junhuayunhu

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.PowerManager
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.junhuayunhu.service.ApiClient
import com.junhuayunhu.service.MainService
import com.junhuayunhu.utils.ConfigManager

class MainActivity : AppCompatActivity() {

    private lateinit var config: ConfigManager

    private var requiredPermissions = listOf(
        Manifest.permission.CALL_PHONE,
        Manifest.permission.READ_PHONE_STATE,
        Manifest.permission.PROCESS_OUTGOING_CALLS,
        Manifest.permission.READ_CALL_LOG,
        Manifest.permission.RECORD_AUDIO,
        Manifest.permission.POST_NOTIFICATIONS,
    )

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val denied = result.filter { !it.value }.keys
        val critical = denied.intersect(setOf(
            Manifest.permission.CALL_PHONE,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.READ_PHONE_STATE,
        ))
        if (critical.isNotEmpty()) {
            Toast.makeText(this, "拨号和录音权限被拒绝，功能可能受限", Toast.LENGTH_LONG).show()
        }
        checkStoragePermission()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        config = ConfigManager(this)

        if (config.wsUrl.contains("127.0.0.1") || config.wsUrl.contains("localhost")) {
            startActivity(Intent(this, SettingsActivity::class.java))
            finish()
            return
        }

        if (!config.isLoggedIn()) {
            startActivity(Intent(this, com.junhuayunhu.ui.LoginActivity::class.java))
            finish()
            return
        }

        setContentView(R.layout.activity_main)

        findViewById<android.widget.ImageView>(R.id.btn_settings)?.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        findViewById<Button>(R.id.btn_stats)?.setOnClickListener {
            startActivity(Intent(this, com.junhuayunhu.ui.StatsActivity::class.java))
        }

        findViewById<Button>(R.id.btn_logout)?.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("退出登录")
                .setMessage("确定退出当前坐席？")
                .setPositiveButton("确定") { _, _ ->
                    config.clearLogin()
                    stopService(Intent(this, MainService::class.java))
                    startActivity(Intent(this, com.junhuayunhu.ui.LoginActivity::class.java))
                    finish()
                }
                .setNegativeButton("取消", null)
                .show()
        }

        // Agent info
        findViewById<TextView>(R.id.tv_agent_name)?.text = config.agentName.ifEmpty { config.agentId }

        loadQuickStats()
        checkPermissions()
    }

    override fun onResume() {
        super.onResume()
        if (::config.isInitialized && !config.wsUrl.contains("127.0.0.1")) {
            if (checkBasicPermissions()) {
                startService()
            }
        }
    }

    private fun loadQuickStats() {
        val baseUrl = config.wsUrl.replace("ws://", "http://").replace("wss://", "https://")
        val api = ApiClient(baseUrl)
        api.getStats { result ->
            runOnUiThread {
                if (result != null) {
                    findViewById<TextView>(R.id.tv_today_calls)?.text = "${result.today.dialout}"
                    val min = result.today.callLong / 60
                    val sec = result.today.callLong % 60
                    findViewById<TextView>(R.id.tv_today_duration)?.text =
                        if (min > 0) "${min}分${sec}秒" else "${sec}秒"
                }
            }
        }
    }

    private fun checkPermissions() {
        val needRequest = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }.toMutableList()

        if (needRequest.isEmpty()) {
            checkStoragePermission()
        } else if (needRequest.any { shouldShowRequestPermissionRationale(it) }) {
            AlertDialog.Builder(this)
                .setTitle("需要权限")
                .setMessage("自动拨号和录音需要以下权限：拨打电话、读取通话状态、录音、通知")
                .setPositiveButton("去授权") { _, _ -> permissionLauncher.launch(needRequest.toTypedArray()) }
                .setNegativeButton("稍后", null)
                .show()
        } else {
            permissionLauncher.launch(needRequest.toTypedArray())
        }
    }

    private fun checkStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                AlertDialog.Builder(this)
                    .setTitle("需要文件管理权限")
                    .setMessage("为了读取通话录音文件，请授予「所有文件访问权限」")
                    .setPositiveButton("去设置") { _, _ ->
                        val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                            data = android.net.Uri.parse("package:$packageName")
                        }
                        startActivity(intent)
                    }
                    .setNegativeButton("稍后", null)
                    .show()
            } else {
                checkBatteryOptimization()
            }
        } else {
            val sp = Manifest.permission.READ_EXTERNAL_STORAGE
            if (ContextCompat.checkSelfPermission(this, sp) != PackageManager.PERMISSION_GRANTED) {
                permissionLauncher.launch(arrayOf(sp))
            } else {
                checkBatteryOptimization()
            }
        }
    }

    private fun checkBatteryOptimization() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val pm = getSystemService(POWER_SERVICE) as PowerManager
            if (!pm.isIgnoringBatteryOptimizations(packageName)) {
                android.util.Log.w("AutoDial", "battery optimization not disabled")
            }
        }
        startService()
    }

    private fun checkBasicPermissions(): Boolean {
        return requiredPermissions.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun startService() {
        startForegroundService(Intent(this, MainService::class.java))
    }
}
