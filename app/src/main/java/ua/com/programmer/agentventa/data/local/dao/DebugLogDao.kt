package ua.com.programmer.agentventa.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import ua.com.programmer.agentventa.data.local.entity.DebugLogEntry

@Dao
interface DebugLogDao {

    @Insert
    suspend fun insert(entry: DebugLogEntry): Long

    @Query("SELECT * FROM debug_log_entries WHERE sent = 0 ORDER BY id ASC LIMIT :limit")
    suspend fun pending(limit: Int): List<DebugLogEntry>

    @Query("SELECT COUNT(*) FROM debug_log_entries")
    suspend fun count(): Int

    @Query("SELECT COUNT(*) FROM debug_log_entries WHERE sent = 0")
    suspend fun pendingCount(): Int

    @Query("UPDATE debug_log_entries SET sent = 1 WHERE id IN (:ids)")
    suspend fun markSent(ids: List<Long>)

    @Query("UPDATE debug_log_entries SET attempts = attempts + 1 WHERE id IN (:ids)")
    suspend fun bumpAttempts(ids: List<Long>)

    // Drops the oldest [n] rows regardless of sent state. Used to bound the table
    // when the uploader cannot keep up (offline, server down, toggle disabled
    // mid-flight).
    @Query("DELETE FROM debug_log_entries WHERE id IN (SELECT id FROM debug_log_entries ORDER BY id ASC LIMIT :n)")
    suspend fun dropOldest(n: Int): Int

    @Query("DELETE FROM debug_log_entries WHERE sent = 1")
    suspend fun deleteSent(): Int
}
