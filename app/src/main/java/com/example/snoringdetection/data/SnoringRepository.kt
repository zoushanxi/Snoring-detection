package com.example.snoringdetection.data

import kotlinx.coroutines.flow.Flow
import java.util.Calendar

/**
 * 数据仓库层，封装对 [SnoringEventDao] 的访问，屏蔽数据库细节。
 *
 * @param dao Room DAO 实例
 */
class SnoringRepository(private val dao: SnoringEventDao) {

    /**
     * 插入一条鼾声事件。
     */
    suspend fun insertEvent(event: SnoringEvent) = dao.insert(event)

    /**
     * 获取今日的鼾声事件列表（响应式 Flow）。
     */
    fun getTodayEvents(): Flow<List<SnoringEvent>> {
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return dao.getTodayEvents(calendar.timeInMillis)
    }

    /**
     * 获取最近 [limit] 条鼾声事件（响应式 Flow）。
     */
    fun getRecentEvents(limit: Int = 50): Flow<List<SnoringEvent>> =
        dao.getRecentEvents(limit)

    /**
     * 清空所有记录（开发/测试用）。
     */
    suspend fun deleteAll() = dao.deleteAll()
}
