package com.pion.phonecleaner.data.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * `hidden_notifications` — one of the app's exactly two tables
 * (`docs/system-architecture.md` §7.3, `LLM.md` §6.4).
 *
 * It replaces `od.i.B()`, which performs a full Gson `fromJson` of up to 100 entries, an
 * `add(0, …)`, a full `toJson` and a `commit()` **on the system binder thread, once per posted
 * notification** (`docs/screens/17-notification-and-permissions.md`, §7.3 item 2). Its cap guard is
 * `size == 100`, not `>= 100`, so a list that ever exceeded 100 is never trimmed again —
 * `HiddenNotificationDao.trimTo` is the `> MAX` version of that guard.
 *
 * **[key] is non-null and is the primary key.** `od.i.C(key)` deletes by a *nullable* key, so one
 * null-key entry deletes every other null-key entry. The interceptor service mints a deterministic
 * fallback (`"$packageName#$postedAtMillis"`) when the platform gives it none
 * (`docs/screens/17-notification-and-permissions.md:32`).
 *
 * The columns are exactly the five of the domain model `HiddenNotification(key, packageName, title,
 * body, postedAt)` (same file, :31-37), stored flat: no icon, no `Drawable`, no resource `int`. A
 * `Drawable` on a model is the direct cause of the competitor's `equals` anomaly where two identical
 * rows compare unequal (`LLM.md` §2), and this row is read by a `LazyColumn` that has to diff.
 *
 * The entity ↔ model mapping is the notification cluster's (`data/notification/`); this table is
 * shared infrastructure and holds no domain import, so `:data` can build before that cluster exists.
 */
@Entity(tableName = "hidden_notifications")
data class HiddenNotificationEntity(
    @PrimaryKey
    @ColumnInfo(name = "key")
    val key: String,
    @ColumnInfo(name = "package_name")
    val packageName: String,
    @ColumnInfo(name = "title")
    val title: String,
    @ColumnInfo(name = "body")
    val body: String,
    /** Epoch milliseconds. `Instant` is a domain concern; a column is a number. */
    @ColumnInfo(name = "posted_at")
    val postedAtEpochMillis: Long,
)
