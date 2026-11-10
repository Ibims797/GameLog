package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.config.AppConfig

/**
 * Lokale Room-Datenbank für den Story Games Tracker.
 * Startet komplett LEER ohne vordefinierte Spiele.
 */
@Database(
    entities = [LocalGame::class],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun gameDao(): GameDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE local_games ADD COLUMN currentPriceCents INTEGER NOT NULL DEFAULT -1")
                db.execSQL("ALTER TABLE local_games ADD COLUMN originalPriceCents INTEGER NOT NULL DEFAULT -1")
                db.execSQL("ALTER TABLE local_games ADD COLUMN discountPercent INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE local_games ADD COLUMN formattedPrice TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE local_games ADD COLUMN lowestHistoricalPrice TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE local_games ADD COLUMN targetPriceAlertEur REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE local_games ADD COLUMN isPriceAlertTriggered INTEGER NOT NULL DEFAULT 0")
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    AppConfig.LOCAL_DATABASE_NAME
                )
                    .addMigrations(MIGRATION_2_3)
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
