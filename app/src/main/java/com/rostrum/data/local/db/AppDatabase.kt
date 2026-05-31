package com.rostrum.data.local.db

import androidx.room.*

/**
 * Room数据库 - 解决"数据库存储层薄弱"问题
 */
@Database(
    entities = [
        BookmarkEntity::class,
        FileHistoryEntity::class,
        SettingEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun bookmarkDao(): BookmarkDao
    abstract fun fileHistoryDao(): FileHistoryDao
    abstract fun settingDao(): SettingDao
}

@Entity(tableName = "bookmarks")
data class BookmarkEntity(
    @PrimaryKey val id: String,
    val name: String,
    val path: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "file_history")
data class FileHistoryEntity(
    @PrimaryKey val id: String,
    val path: String,
    val operation: String,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "settings")
data class SettingEntity(
    @PrimaryKey val key: String,
    val value: String
)

@Dao
interface BookmarkDao {
    @Query("SELECT * FROM bookmarks ORDER BY createdAt DESC")
    suspend fun getAll(): List<BookmarkEntity>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(bookmark: BookmarkEntity)
    
    @Delete
    suspend fun delete(bookmark: BookmarkEntity)
}

@Dao
interface FileHistoryDao {
    @Query("SELECT * FROM file_history ORDER BY timestamp DESC LIMIT 100")
    suspend fun getRecent(): List<FileHistoryEntity>
    
    @Insert
    suspend fun insert(history: FileHistoryEntity)
}

@Dao
interface SettingDao {
    @Query("SELECT * FROM settings WHERE `key` = :key")
    suspend fun get(key: String): SettingEntity?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun set(setting: SettingEntity)
}
