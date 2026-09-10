package com.pion.phonecleaner.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.pion.phonecleaner.data.database.entity.HiddenNotificationEntity
import com.pion.phonecleaner.data.database.entity.ThreatCacheEntity
import com.pion.phonecleaner.data.database.entity.TrashEntryEntity
import com.pion.phonecleaner.data.database.migration.AppMigrations

/**
 * **Exactly three tables, and a third needs a stated reason** (`LLM.md` §4, §6.4;
 * `docs/system-architecture.md` §7.3). The split is not a preference:
 *
 * | Store | Holds |
 * |---|---|
 * | Room — here | `hidden_notifications`, `threat_cache`, `trash_entries` — *lists* that grow, are queried, and are written from a non-UI thread |
 * | `DataStore<Preferences>` | every key-value latch, timestamp, setting and counter (`data/datastore/`) |
 * | an in-memory `single` | a scan result — that is a session, not persistent state, and every route that reads one owes a "session lost" branch |
 * | `SavedStateHandle` | route arguments and in-progress selection — the only thing that survives process death for a screen |
 *
 * Put a scan result in here and it stops being a session; put a latch in here and it stops being
 * observable in one line.
 *
 * The competitor has no Room, no SQLite and no `ContentProvider` at all
 * (`docs/screens/21-shared-models-and-ui.md:624`), which is why the first two tables replace
 * "nothing" — they replace a Gson blob in a `SharedPreferences` string and an `Intent` extra.
 *
 * **`trash_entries` is the third table, and here is the reason `LLM.md` §4 asks for.** It is a list
 * that grows, is queried by two predicates the UI cannot answer for itself (`state = 'TRASHED'`
 * ordered by age, and `expires_at <= now`), is written off the UI thread by a background worker, and
 * each row points at a real file the user can still get back. DataStore answers none of that: an
 * expiry sweep over a preferences blob is a full read, a full parse and a full rewrite for every
 * purge, which is the `od.i.B()` defect `hidden_notifications` was created to delete. And it is not a
 * session — a session dies with the process, and this must outlive a reboot.
 */
@Database(
    entities = [HiddenNotificationEntity::class, ThreatCacheEntity::class, TrashEntryEntity::class],
    version = 2,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun hiddenNotificationDao(): HiddenNotificationDao

    abstract fun threatCacheDao(): ThreatCacheDao

    abstract fun trashEntryDao(): TrashEntryDao

    companion object {
        /** kebab-case per `LLM.md` §5's rule for non-source artefacts. */
        const val NAME: String = "phone-cleaner.db"

        /**
         * No `fallbackToDestructiveMigration()`, on this database or `trash_entries` specifically.
         * `hidden_notifications` is user-visible data the user chose to keep, not a cache, and a
         * destructive fallback discards it silently on the next version bump — the same is true of
         * `trash_entries`, more strongly: a destructive fallback there silently destroys files the
         * user asked this feature to keep. Without it a bump with no migration fails loudly when the
         * database opens, which is the failure that gets fixed, never the data that gets lost.
         *
         * `exportSchema = true`, with the `room.schemaLocation` KSP argument (`data/build.gradle.kts`)
         * — turned on in the change that wrote the first migration, `AppMigrations.MIGRATION_1_2`.
         */
        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, NAME)
                .addMigrations(AppMigrations.MIGRATION_1_2)
                .build()
    }
}
