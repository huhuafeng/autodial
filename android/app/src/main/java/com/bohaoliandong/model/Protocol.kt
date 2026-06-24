package com.bohaoliandong.model

data class WsMessage(
    val type: String,
    val role: String? = null,
    val phone: String? = null,
    val callSession: String? = null,
    val status: String? = null,
    val fileName: String? = null,
    val message: String? = null
)
