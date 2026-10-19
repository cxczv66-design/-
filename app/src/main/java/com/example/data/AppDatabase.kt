package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.model.FirewallLogEntry
import com.example.model.SandboxedApp
import com.example.model.SandboxSession

@Database(
    entities = [SandboxedApp::class, FirewallLogEntry::class, SandboxSession::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun sandboxedAppDao(): SandboxedAppDao
    abstract fun firewallLogDao(): FirewallLogDao
    abstract fun sandboxSessionDao(): SandboxSessionDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "isolatebox_database"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
