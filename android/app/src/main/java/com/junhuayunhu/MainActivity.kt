package com.junhuayunhu

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
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
        "android.permission.FOREGROUND_SERVICE_DATA_SYNC"
    )

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val denied = result.filter { !it.value }.keys
        if (denied.isEmpty()) {
            checkStoragePermission()
        } else {
            Toast.makeText(this, "部分权限被拒绝，功能可能受限", Toast.LENGTH_LONG).show()
            checkStoragePermission()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        config = ConfigManager(this)

        if (config.wsUrl.contains("127.0.0.1") || config.wsUrl.contains("localhost")) {
            startActivity(Intent(this, SettingsActivity::class.java))
            finish()
            return
        }

        // 未登录 → 跳转登录页
        if (!config.isLoggedIn()) {
            startActivity(Intent(this, ui.LoginActivity::class.java))
            finish()
            return
        }

        setContentView(R.layout.activity_main)

        findViewById<android.widget.Button>(R.id.btn_settings)?.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        findViewById<android.widget.Button>(R.id.btn_stats)?.setOnClickListener {
            startActivity(Intent(this, com.junhuayunhu.ui.StatsActivity::class.java))
        }

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
                // 不阻塞服务启动，仅作提示
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

    private fun checkStoragePermitted(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun startService() {
        startForegroundService(Intent(this, MainService::class.java))
    }
}
