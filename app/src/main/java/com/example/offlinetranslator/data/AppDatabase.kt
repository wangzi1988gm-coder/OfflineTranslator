package com.example.offlinetranslator.data

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

/**
 * 翻译历史记录（Room）。
 */
@Entity(tableName = "history")
data class HistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "from_lang") val fromLang: String,
    @ColumnInfo(name = "to_lang") val toLang: String,
    @ColumnInfo(name = "source") val source: String,
    @ColumnInfo(name = "translation") val translation: String,
    @ColumnInfo(name = "engine") val engine: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "is_favorite") val isFavorite: Boolean = false,
)

@Dao
interface HistoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: HistoryEntity): Long

    @Query("SELECT * FROM history ORDER BY created_at DESC LIMIT 200")
    fun observe(): Flow<List<HistoryEntity>>

    @Query("SELECT * FROM history WHERE is_favorite = 1 ORDER BY created_at DESC LIMIT 200")
    fun observeFavorites(): Flow<List<HistoryEntity>>

    @Query("DELETE FROM history WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("UPDATE history SET is_favorite = :fav WHERE id = :id")
    suspend fun setFavorite(id: Long, fav: Boolean)

    @Query("DELETE FROM history")
    suspend fun clearAll()
}

@Database(entities = [HistoryEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun historyDao(): HistoryDao
}
