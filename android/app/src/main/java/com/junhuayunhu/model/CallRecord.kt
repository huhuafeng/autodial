package com.junhuayunhu.model

data class CallRecord(
    val id: Long = 0,
    val phone: String,
    val callSession: String,
    val duration: Int,
    val status: String,
    val recordingUrl: String?,
    val timestamp: Long
)
