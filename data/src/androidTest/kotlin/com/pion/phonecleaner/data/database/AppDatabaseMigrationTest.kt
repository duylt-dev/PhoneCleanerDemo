package com.pion.phonecleaner.data.database

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pion.phonecleaner.data.database.migration.AppMigrations
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Device-based tests for Room database migrations.
 *
 * Verifies that:
 * 1. Migration 1 → 2 adds the `trash_entries` table correctly
 * 2. Existing data in `hidden_notifications` and `threat_cache` is preserved
 * 3. The migrated schema matches the exported schema
 * 4. Migration 2 -> 3 adds ZIP-batch trash metadata
 *
 * Requires `MigrationTestHelper` which needs a real SQLite database on a device.
 */
@RunWith(AndroidJUnit4::class)
class AppDatabaseMigrationTest {

    private val testDb = "test-database"

    @get:Rule
    val migrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    @Test
    fun migration_1_to_2_preserves_hidden_notifications() {
        // Create an old v1 database
        val db1 = migrationTestHelper.createDatabase(testDb, 1)

        // Insert sample data into hidden_notifications (schema v1 still has it)
        db1.execSQL(
            """
            INSERT INTO hidden_notifications (key, package_name, title, body, posted_at)
            VALUES (?, ?, ?, ?, ?)
            """.trimIndent(),
            arrayOf<Any>("key1", "com.example", "Title", "Body", System.currentTimeMillis()),
        )

        db1.close()

        // Perform the migration
        val db2 = migrationTestHelper.runMigrationsAndValidate(
            testDb,
            2,
            true,
            AppMigrations.MIGRATION_1_2,
        )

        // Verify hidden_notifications row is still there
        val cursor = db2.query("SELECT COUNT(*) FROM hidden_notifications WHERE key = 'key1'")
        cursor.moveToFirst()
        assert(cursor.getInt(0) == 1) { "hidden_notifications data was lost" }
        cursor.close()

        db2.close()
    }

    @Test
    fun migration_1_to_2_creates_trash_entries_table() {
        // Create an old v1 database
        val db1 = migrationTestHelper.createDatabase(testDb, 1)
        db1.close()

        // Perform the migration
        val db2 = migrationTestHelper.runMigrationsAndValidate(
            testDb,
            2,
            true,
            AppMigrations.MIGRATION_1_2,
        )

        // Verify trash_entries table exists and is empty
        val cursor = db2.query("SELECT COUNT(*) FROM trash_entries")
        cursor.moveToFirst()
        assert(cursor.getInt(0) == 0) { "trash_entries should be empty after migration" }
        cursor.close()

        db2.close()
    }

    @Test
    fun migration_1_to_2_schema_matches_exported() {
        // Create and migrate through all versions
        val db1 = migrationTestHelper.createDatabase(testDb, 1)
        db1.close()

        val db2 = migrationTestHelper.runMigrationsAndValidate(
            testDb,
            2,
            true,
            AppMigrations.MIGRATION_1_2,
        )

        // Verify key columns exist in trash_entries
        val cursor = db2.query("PRAGMA table_info(trash_entries)")
        val columns = mutableSetOf<String>()
        while (cursor.moveToNext()) {
            // PRAGMA table_info columns: cid, name, type, notnull, dflt_value, pk
            // Column name is at position 1
            val columnName: String = cursor.getString(1)
            columns.add(columnName)
        }
        cursor.close()

        val expectedColumns = setOf(
            "id", "original_path", "trashed_path", "display_name", "size_bytes",
            "file_count", "is_directory", "mime_type", "source_feature", "origin_content_uri",
            "media_row_cleared", "state", "trashed_at", "expires_at",
        )
        assert(columns.containsAll(expectedColumns)) {
            "Schema mismatch. Expected: $expectedColumns, Got: $columns"
        }

        db2.close()
    }

    @Test
    fun migration_2_to_3_adds_zip_metadata_columns() {
        val db2 = migrationTestHelper.createDatabase(testDb, 2)
        db2.execSQL(
            """
            INSERT INTO trash_entries (
                id, original_path, trashed_path, display_name, size_bytes, file_count,
                is_directory, mime_type, source_feature, origin_content_uri, media_row_cleared,
                state, trashed_at, expires_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
            arrayOf<Any>(
                "row", "/old.jpg", "/trash/row-old.jpg", "old.jpg", 12L, 1, 0,
                "image/jpeg", "Trash", "", 1, "TRASHED", 1L, 2L,
            ),
        )
        db2.close()

        val db3 = migrationTestHelper.runMigrationsAndValidate(
            testDb,
            3,
            true,
            AppMigrations.MIGRATION_2_3,
        )

        val cursor = db3.query("SELECT entry_type, batch_id, metadata_json FROM trash_entries WHERE id = 'row'")
        cursor.moveToFirst()
        assert(cursor.getString(0) == "ORIGINAL") { "Old trash rows must remain original entries" }
        assert(cursor.isNull(1)) { "Existing rows should not gain a batch id" }
        assert(cursor.isNull(2)) { "Existing rows should not gain metadata" }
        cursor.close()
        db3.close()
    }
}
