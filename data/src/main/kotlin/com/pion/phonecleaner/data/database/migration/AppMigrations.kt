package com.pion.phonecleaner.data.database.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Every `AppDatabase` migration, in one place so `AppDatabase.build()` names one import.
 *
 * **No `fallbackToDestructiveMigration()`, ever** — `AppDatabase`'s own KDoc says why, and a missing
 * migration here fails loudly when the database opens rather than silently discarding a table.
 *
 * Room 2.8.4: `Migration.migrate` takes a [SupportSQLiteDatabase] here — this module never calls
 * `RoomDatabase.Builder.setDriver`, so Room runs the classic framework-SQLite bridge and the
 * `SQLiteConnection` (KMP driver) overload is not the one in play. Both are `execSQL` with the same
 * string either way.
 */
internal object AppMigrations {

    /**
     * `trash_entries` — the third table. The `CREATE TABLE` below is copied verbatim from
     * `data/schemas/…/2.json`'s `createSql` (`TABLE_NAME` resolved to the literal name), per
     * `LLM.md` §4: a hand-written statement that differs by one `NOT NULL` fails `MigrationTestHelper`
     * with a diff nobody enjoys reading. The index matches `TrashEntryEntity`'s `@Index`.
     */
    val MIGRATION_1_2: Migration = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `trash_entries` (" +
                    "`id` TEXT NOT NULL, " +
                    "`original_path` TEXT NOT NULL, " +
                    "`trashed_path` TEXT NOT NULL, " +
                    "`display_name` TEXT NOT NULL, " +
                    "`size_bytes` INTEGER NOT NULL, " +
                    "`file_count` INTEGER NOT NULL, " +
                    "`is_directory` INTEGER NOT NULL, " +
                    "`mime_type` TEXT, " +
                    "`source_feature` TEXT NOT NULL, " +
                    "`origin_content_uri` TEXT, " +
                    "`media_row_cleared` INTEGER NOT NULL, " +
                    "`state` TEXT NOT NULL, " +
                    "`trashed_at` INTEGER NOT NULL, " +
                    "`expires_at` INTEGER NOT NULL, " +
                    "PRIMARY KEY(`id`))",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_trash_entries_state_expires_at` " +
                    "ON `trash_entries` (`state`, `expires_at`)",
            )
        }
    }

    val MIGRATION_2_3: Migration = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `trash_entries` ADD COLUMN `entry_type` TEXT NOT NULL DEFAULT 'ORIGINAL'")
            db.execSQL("ALTER TABLE `trash_entries` ADD COLUMN `batch_id` TEXT")
            db.execSQL("ALTER TABLE `trash_entries` ADD COLUMN `metadata_json` TEXT")
        }
    }
}
