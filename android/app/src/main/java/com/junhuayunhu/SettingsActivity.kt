package com.junhuayunhu

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.telecom.TelecomManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.PowerManager
import android.provider.Settings
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import com.junhuayunhu.utils.ConfigManager

class SettingsActivity : AppCompatActivity() {

    private lateinit var config: ConfigManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, true)
        setContentView(R.layout.activity_settings)

        config = ConfigManager(this)

        findViewById<EditText>(R.id.et_ws_url).setText(config.wsUrl)
        findViewById<EditText>(R.id.et_upload_url).setText(config.uploadUrl)
        findViewById<EditText>(R.id.et_call_rec_path).setText(config.callRecPath)
        findViewById<EditText>(R.id.et_keyword).setText(config.keywordTemplate)
        findViewById<EditText>(R.id.et_suffix).setText(config.suffix)

        findViewById<Button>(R.id.btn_save).setOnClickListener { saveConfig() }
        findViewById<Button>(R.id.btn_fix_permissions).setOnClickListener { openPermissionSettings() }
        findViewById<Button>(R.id.btn_fix_battery).setOnClickListener { openBatterySettings() }
        findViewById<Button>(R.id.btn_fix_storage).setOnClickListener { openStorageSettings() }
        findViewById<Button>(R.id.btn_miui_autostart).setOnClickListener { openMiuiAutostart() }

        updatePermissionStatus()
    }

    private fun saveConfig() {
        val ws = findViewById<EditText>(R.id.et_ws_url).text.toString().trim()
        val upload = findViewById<EditText>(R.id.et_upload_url).text.toString().trim()
        val path = findViewById<EditText>(R.id.et_call_rec_path).text.toString().trim()
        val keyword = findViewById<EditText>(R.id.et_keyword).text.toString().trim()
        val suffix = findViewById<EditText>(R.id.et_suffix).text.toString().trim()

        if (ws.isEmpty()) { Toast.makeText(this, "WS 地址不能为空", Toast.LENGTH_SHORT).show(); return }
        if (upload.isEmpty()) { Toast.makeText(this, "上传地址不能为空", Toast.LENGTH_SHORT).show(); return }

        config.wsUrl = ws
        config.uploadUrl = upload
        config.callRecPath = path
        config.keywordTemplate = keyword
        config.suffix = suffix

        Toast.makeText(this, "已保存", Toast.LENGTH_SHORT).show()
        finish()
        startActivity(Intent(this, MainActivity::class.java))
    }

    private fun updatePermissionStatus() {
        fun check(perm: String): String = when {
            ContextCompat.checkSelfPermission(this, perm) == PackageManager.PERMISSION_GRANTED -> "✅ 已授权"
            else -> "❌ 未授权"
        }

        fun checkManageCalls(): String {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return "✅ 无需授权"
            val telecom = getSystemService(Context.TELECOM_SERVICE) as TelecomManager
            return if (telecom.isInCall) "⚠️ 请查看设置" else "❌ 未授权"
        }

        findViewById<TextView>(R.id.tv_perm_phone).text = "拨打电话：${check(Manifest.permission.CALL_PHONE)}"
        findViewById<TextView>(R.id.tv_perm_state).text = "通话状态：${check(Manifest.permission.READ_PHONE_STATE)}"
        findViewById<TextView>(R.id.tv_perm_audio).text = "录音：${check(Manifest.permission.RECORD_AUDIO)}"
        findViewById<TextView>(R.id.tv_perm_notification).text = "通知：${check(Manifest.permission.POST_NOTIFICATIONS)}"
        findViewById<TextView>(R.id.tv_perm_manage_calls).text = "后台拨号：${checkManageCalls()}"

        val storageOk = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
            Environment.isExternalStorageManager() else
            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        findViewById<TextView>(R.id.tv_perm_storage).text = "文件访问：${if (storageOk) "✅ 已授权" else "❌ 未授权"}"

        val pm = getSystemService(POWER_SERVICE) as PowerManager
        val battOk = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) pm.isIgnoringBatteryOptimizations(packageName) else true
        findViewById<TextView>(R.id.tv_perm_battery).text = "省电策略：${if (battOk) "✅ 已忽略" else "❌ 未设置"}"
    }

    private fun openPermissionSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:$packageName")
        }
        startActivity(intent)
    }

    private fun openBatterySettings() {
        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.parse("package:$packageName")
        }
        startActivity(intent)
    }

    private fun openStorageSettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                data = Uri.parse("package:$packageName")
            }
            startActivity(intent)
        } else {
            Toast.makeText(this, "请在「设置 → 应用管理 → 权限」中开启存储权限", Toast.LENGTH_LONG).show()
        }
    }

    private fun openMiuiAutostart() {
        val intent = try {
            // MIUI 12+ auto-start settings
            Intent().apply {
                action = "miui.intent.action.OP_AUTO_START"
                addCategory(Intent.CATEGORY_DEFAULT)
                putExtra("package_name", packageName)
                putExtra("uid", packageManager.getApplicationInfo(packageName, 0).uid)
            }
        } catch (_: Exception) {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:$packageName")
            }
        }
        try {
            startActivity(intent)
        } catch (_: Exception) {
            Toast.makeText(this, "请手动在「设置 → 应用管理 → 自启动」中开启", Toast.LENGTH_LONG).show()
        }
    }
}
