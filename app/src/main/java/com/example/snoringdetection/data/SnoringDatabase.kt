package com.example.snoringdetection.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Room 数据库单例。
 *
 * 版本号升级时需要提供迁移策略（Migration），或在开发阶段使用
 * [fallbackToDestructiveMigration] 直接重建（会丢失数据，仅限开发期使用）。
 */
@Database(entities = [SnoringEvent::class], version = 2, exportSchema = false)
abstract class SnoringDatabase : RoomDatabase() {

    abstract fun snoringEventDao(): SnoringEventDao

    companion object {
        @Volatile
        private var INSTANCE: SnoringDatabase? = null

        /**
         * 获取数据库单例。使用双重检查锁保证线程安全。
         */
        fun getInstance(context: Context): SnoringDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    SnoringDatabase::class.java,
                    "snoring_detection.db"
                )
                    // 当前仍处于开发迭代阶段，数据库 schema 变更采用 destructive migration。
                    // 后续稳定后需替换为显式 Migration，避免用户数据丢失。
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
