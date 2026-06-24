package com.junhuayunhu.model

data class StatsDay(
    val dialout: Int,
    val callLong: Int,
    val avg: Int,
    val connect: Int,
    val connect40: Int,
    val connectRate: Int
)

data class StatsResult(
    val today: StatsDay,
    val yesterday: StatsDay
)

data class RecordListResult(
    val total: Int,
    val page: Int,
    val limit: Int,
    val list: List<CallRecord>
)

data class PullResult(
    val list: List<CallRecord>
)

data class SyncResult(
    val success: Boolean,
    val added: Int
)
