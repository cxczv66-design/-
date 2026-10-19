package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.model.FirewallLogEntry
import com.example.model.SandboxSession
import kotlinx.coroutines.flow.Flow

@Dao
interface FirewallLogDao {

    @Query("SELECT * FROM firewall_logs ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentLogs(limit: Int = 100): Flow<List<FirewallLogEntry>>

    @Query("SELECT * FROM firewall_logs WHERE packageName = :packageName ORDER BY timestamp DESC LIMIT :limit")
    fun getLogsForPackage(packageName: String, limit: Int = 50): Flow<List<FirewallLogEntry>>

    @Query("SELECT COUNT(*) FROM firewall_logs WHERE packageName = :packageName")
    suspend fun getBlockedCountForPackage(packageName: String): Long

    @Query("SELECT COUNT(*) FROM firewall_logs")
    fun getTotalBlockedCountFlow(): Flow<Long>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(entry: FirewallLogEntry)

    @Query("DELETE FROM firewall_logs")
    suspend fun clearLogs()
}

@Dao
interface SandboxSessionDao {

    @Query("SELECT * FROM sandbox_sessions ORDER BY startTime DESC LIMIT 50")
    fun getAllSessions(): Flow<List<SandboxSession>>

    @Query("SELECT * FROM sandbox_sessions WHERE status = 'RUNNING' ORDER BY startTime DESC LIMIT 1")
    fun getActiveSession(): Flow<SandboxSession?>

    @Query("SELECT * FROM sandbox_sessions WHERE status = 'RUNNING' ORDER BY startTime DESC LIMIT 1")
    suspend fun getActiveSessionSync(): SandboxSession?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: SandboxSession)

    @Query("UPDATE sandbox_sessions SET endTime = :endTime, status = 'STOPPED' WHERE sessionId = :sessionId")
    suspend fun endSession(sessionId: String, endTime: Long = System.currentTimeMillis())

    @Query("UPDATE sandbox_sessions SET blockedPacketsCount = blockedPacketsCount + :increment WHERE sessionId = :sessionId")
    suspend fun incrementSessionBlockedPackets(sessionId: String, increment: Int)
}
