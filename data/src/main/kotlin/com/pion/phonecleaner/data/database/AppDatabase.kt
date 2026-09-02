package com.pion.phonecleaner.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.pion.phonecleaner.data.database.entity.HiddenNotificationEntity
import com.pion.phonecleaner.data.database.entity.ThreatCacheEntity

/**
 * **Exactly two tables, and a third needs a stated reason** (`LLM.md` §4, §6.4;
 * `docs/system-architecture.md` §7.3). The split is not a preference:
 *
 * | Store | Holds |
 * |---|---|
 * | Room — here | `hidden_notifications`, `threat_cache` — *lists* that grow, are queried, and are written from a non-UI thread |
 * | `DataStore<Preferences>` | every key-value latch, timestamp, setting and counter (`data/datastore/`) |
 * | an in-memory `single` | a scan result — that is a session, not persistent state, and every route that reads one owes a "session lost" branch |
 * | `SavedStateHandle` | route arguments and in-progress selection — the only thing that survives process death for a screen |
 *
 * Put a scan result in here and it stops being a session; put a latch in here and it stops being
 * observable in one line.
 *
 * The competitor has no Room, no SQLite and no `ContentProvider` at all
 * (`docs/screens/21-shared-models-and-ui.md:624`), which is why both tables replace "nothing" —
 * they replace a Gson blob in a `SharedPreferences` string and an `Intent` extra.
 */
@Database(
    entities = [HiddenNotificationEntity::class, ThreatCacheEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun hiddenNotificationDao(): HiddenNotificationDao

    abstract fun threatCacheDao(): ThreatCacheDao

    companion object {
        /** kebab-case per `LLM.md` §5's rule for non-source artefacts. */
        const val NAME: String = "phone-cleaner.db"

        /**
         * No `fallbackToDestructiveMigration()`. `hidden_notifications` is user-visible data the user
         * chose to keep, not a cache, and a destructive fallback discards it silently on the next
         * version bump. Without it a bump with no migration fails loudly when the database opens,
         * which is the failure that gets fixed.
         *
         * `exportSchema = false` because there is nothing to migrate *from* at version 1; turn it on,
         * with a `room.schemaLocation` KSP argument, in the change that writes the first migration.
         */
        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, NAME).build()
    }
}
