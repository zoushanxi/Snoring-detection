package com.example.snoringdetection.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * Room DAO：提供对 snoring_events 表的增删查操作。
 */
@Dao
interface SnoringEventDao {

    /** 插入一条新的鼾声事件（冲突时替换）。 */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(event: SnoringEvent)

    /**
     * 查询今日（以当地时区的 0 点为起点）的所有事件，按时间倒序。
     * @param dayStartMs 今日 0 点对应的 Unix 时间戳（毫秒）
     */
    @Query("SELECT * FROM snoring_events WHERE timestampMs >= :dayStartMs ORDER BY timestampMs DESC")
    fun getTodayEvents(dayStartMs: Long): Flow<List<SnoringEvent>>

    /**
     * 查询最近 [limit] 条事件，按时间倒序，用于 UI 列表展示。
     */
    @Query("SELECT * FROM snoring_events ORDER BY timestampMs DESC LIMIT :limit")
    fun getRecentEvents(limit: Int = 50): Flow<List<SnoringEvent>>

    /** 删除所有记录（开发/测试用）。 */
    @Query("DELETE FROM snoring_events")
    suspend fun deleteAll()
}
