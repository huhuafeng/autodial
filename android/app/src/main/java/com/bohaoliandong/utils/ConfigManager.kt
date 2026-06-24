package com.bohaoliandong.utils

import android.content.Context
import android.content.SharedPreferences
import com.bohaoliandong.BuildConfig

class ConfigManager(context: Context) {

    private val sp: SharedPreferences =
        context.getSharedPreferences("autodial_config", Context.MODE_PRIVATE)

    var wsUrl: String
        get() = sp.getString(KEY_WS_URL, BuildConfig.WS_URL) ?: BuildConfig.WS_URL
        set(v) = sp.edit().putString(KEY_WS_URL, v).apply()

    var uploadUrl: String
        get() = sp.getString(KEY_UPLOAD_URL, BuildConfig.UPLOAD_URL) ?: BuildConfig.UPLOAD_URL
        set(v) = sp.edit().putString(KEY_UPLOAD_URL, v).apply()

    var callRecPath: String
        get() = sp.getString(KEY_CALL_REC_PATH, DEFAULT_CALL_REC_PATH) ?: DEFAULT_CALL_REC_PATH
        set(v) = sp.edit().putString(KEY_CALL_REC_PATH, v).apply()

    var keywordTemplate: String
        get() = sp.getString(KEY_KEYWORD, DEFAULT_KEYWORD) ?: DEFAULT_KEYWORD
        set(v) = sp.edit().putString(KEY_KEYWORD, v).apply()

    var suffix: String
        get() = sp.getString(KEY_SUFFIX, DEFAULT_SUFFIX) ?: DEFAULT_SUFFIX
        set(v) = sp.edit().putString(KEY_SUFFIX, v).apply()

    companion object {
        private const val KEY_WS_URL = "ws_url"
        private const val KEY_UPLOAD_URL = "upload_url"
        private const val KEY_CALL_REC_PATH = "call_rec_path"
        private const val KEY_KEYWORD = "keyword"
        private const val KEY_SUFFIX = "suffix"

        const val DEFAULT_CALL_REC_PATH = "/storage/emulated/0/MIUI/sound_recorder/call_rec"
        const val DEFAULT_KEYWORD = "mobile_time"
        const val DEFAULT_SUFFIX = ".mp3"
    }
}
