package com.example.snoringdetection.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 鼾声事件实体，对应 Room 数据库中的 snoring_events 表。
 *
 * @param id          自增主键
 * @param timestampMs 事件检测到的 Unix 时间戳（毫秒）
 * @param durationMs  本次鼾声片段持续时长（毫秒，近似估计）
 * @param peakDb      该片段内的峰值分贝值
 */
@Entity(tableName = "snoring_events")
data class SnoringEvent(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val timestampMs: Long,
    val durationMs: Long,
    val peakDb: Float,
    val audioFilePath: String? = null
)
