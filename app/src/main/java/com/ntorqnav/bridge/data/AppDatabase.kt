package com.ntorqnav.bridge.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "recorded_sessions")
data class RecordedSessionEntity(
    @PrimaryKey val sessionId: String,
    val description: String,
    val createdAt: Long,
    val packetCount: Int,
    val serializedJson: String
)

@Dao
interface SessionDao {
    @Query("SELECT * FROM recorded_sessions ORDER BY createdAt DESC")
    fun getAllSessions(): Flow<List<RecordedSessionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: RecordedSessionEntity)

    @Query("DELETE FROM recorded_sessions WHERE sessionId = :id")
    suspend fun deleteSession(id: String)
}

@Database(entities = [RecordedSessionEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun sessionDao(): SessionDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "ntorq_nav_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
