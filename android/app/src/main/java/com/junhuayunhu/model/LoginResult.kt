package com.junhuayunhu.model

data class LoginResult(
    val success: Boolean,
    val agentId: String?,
    val name: String?,
    val token: String?,
    val error: String?
)
