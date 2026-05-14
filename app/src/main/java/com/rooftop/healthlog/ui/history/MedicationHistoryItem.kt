package com.rooftop.healthlog.ui.history

/**
 * 修改点3：历史服药记录按"时间点"聚合，而不是按单个药品拆分。
 * 这里保留摘要与详情两份文案，分别用于列表和导出/分享。
 */
data class MedicationHistoryItem(
    val scheduledTime: Long,
    val actualTime: Long?,
    val status: String,
    val medicationNames: List<String>,
    val medicationDetails: List<String>
)
