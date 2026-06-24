package com.junhuayunhu.ui

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import com.junhuayunhu.MainActivity
import com.junhuayunhu.R
import com.junhuayunhu.service.ApiClient
import com.junhuayunhu.utils.ConfigManager

class LoginActivity : AppCompatActivity() {

    private lateinit var config: ConfigManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, true)
        setContentView(R.layout.activity_login)

        config = ConfigManager(this)

        val etAgentId = findViewById<EditText>(R.id.et_agent_id)
        val etPassword = findViewById<EditText>(R.id.et_password)
        val tvError = findViewById<TextView>(R.id.tv_error)
        val btnLogin = findViewById<Button>(R.id.btn_login)

        // 如果有已保存的账号，自动填充
        etAgentId.setText(config.agentId)

        btnLogin.setOnClickListener {
            val agentId = etAgentId.text.toString().trim()
            val password = etPassword.text.toString().trim()
            if (agentId.isEmpty() || password.isEmpty()) {
                tvError.text = "请输入坐席ID和密码"
                tvError.visibility = android.view.View.VISIBLE
                return@setOnClickListener
            }

            tvError.visibility = android.view.View.GONE
            btnLogin.isEnabled = false
            btnLogin.text = "登录中..."

            val baseUrl = config.wsUrl.replace("ws://", "http://").replace("wss://", "https://")
            val api = ApiClient(baseUrl)
            api.login(agentId, password) { result ->
                runOnUiThread {
                    btnLogin.isEnabled = true
                    btnLogin.text = "登 录"
                    if (result != null && result.token != null) {
                        config.agentId = agentId
                        config.token = result.token
                        config.agentName = result.name ?: agentId
                        startActivity(Intent(this, MainActivity::class.java))
                        finish()
                    } else {
                        tvError.text = "登录失败，请检查账号密码"
                        tvError.visibility = android.view.View.VISIBLE
                    }
                }
            }
        }
    }
}
