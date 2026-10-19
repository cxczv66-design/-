package com.example.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.model.SandboxedApp
import kotlinx.coroutines.flow.Flow

@Dao
interface SandboxedAppDao {

    @Query("SELECT * FROM sandboxed_apps ORDER BY addedAt DESC")
    fun getAllSandboxedApps(): Flow<List<SandboxedApp>>

    @Query("SELECT * FROM sandboxed_apps WHERE packageName = :packageName")
    suspend fun getAppByPackage(packageName: String): SandboxedApp?

    @Query("SELECT * FROM sandboxed_apps WHERE isEnabled = 1")
    suspend fun getActiveSandboxedApps(): List<SandboxedApp>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertApp(app: SandboxedApp)

    @Update
    suspend fun updateApp(app: SandboxedApp)

    @Delete
    suspend fun deleteApp(app: SandboxedApp)

    @Query("UPDATE sandboxed_apps SET lastLaunchedAt = :timestamp WHERE packageName = :packageName")
    suspend fun updateLastLaunched(packageName: String, timestamp: Long)

    @Query("UPDATE sandboxed_apps SET totalBlockedPackets = totalBlockedPackets + :count WHERE packageName = :packageName")
    suspend fun incrementBlockedPackets(packageName: String, count: Long)
}
