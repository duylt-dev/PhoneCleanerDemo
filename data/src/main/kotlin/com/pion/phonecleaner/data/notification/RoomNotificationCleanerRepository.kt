package com.pion.phonecleaner.data.notification

import com.pion.phonecleaner.core.common.concurrent.DispatcherProvider
import com.pion.phonecleaner.core.common.error.AppError
import com.pion.phonecleaner.core.common.log.AppLogger
import com.pion.phonecleaner.core.common.result.AppResult
import com.pion.phonecleaner.data.database.HiddenNotificationDao
import com.pion.phonecleaner.data.database.entity.HiddenNotificationEntity
import com.pion.phonecleaner.domain.model.notification.HiddenNotification
import com.pion.phonecleaner.domain.repository.NotificationCleanerRepository
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlin.time.Instant

/**
 * The store half of `od.i`, over Room (`docs/screens/17-notification-and-permissions.md` §5.1).
 *
 * DECLARED IN `notificationDataModule`. The **DAO** it injects is declared in `coreDataModule`, with
 * `AppDatabase` — Room holds exactly two tables in this app and `hidden_notifications` is one of them
 * (§3.4). This class never opens a database and never declares a second binding for one.
 *
 * ### The three things Room replaces, all in `od.i.B()`
 *
 * 1. A full Gson `fromJson` of up to 100 entries, an `add(0, …)`, a full `toJson` and a `commit()` —
 *    **on the system binder thread, once per posted notification** (§5.2). Here: one `upsert` on
 *    `dispatchers.io`.
 * 2. `LiveEventBus("flux_refresh_noti_list")`, a payload-free event whose only possible answer is to
 *    re-read the whole store from disk and reload every icon. Here: [observeHidden] is the
 *    notification.
 * 3. A cap guard written `size == 100`, so a list that ever exceeded 100 is never trimmed again. Here:
 *    [HiddenNotificationDao.trimTo], which is the `> MAX` form of the same rule.
 */
internal class RoomNotificationCleanerRepository(
    private val dao: HiddenNotificationDao,
    private val dispatchers: DispatcherProvider,
    private val log: AppLogger,
) : NotificationCleanerRepository {

    override fun observeHidden(): Flow<ImmutableList<HiddenNotification>> =
        dao.observeAll()
            .map { rows -> rows.map(HiddenNotificationEntity::toModel).toImmutableList() }
            .flowOn(dispatchers.io)

    override fun observeHiddenCount(): Flow<Int> =
        dao.observeCount().distinctUntilChanged().flowOn(dispatchers.io)

    override suspend fun insert(notification: HiddenNotification): AppResult<Unit> =
        io("insert") {
            dao.upsert(notification.toEntity())
            // Trim in the same call, so the cap cannot be skipped by a caller that forgets it.
            dao.trimTo(MAX_ROWS)
            Unit
        }

    override suspend fun dismiss(key: String): AppResult<Unit> = io("dismiss") {
        dao.deleteByKey(key)
        Unit
    }

    override suspend fun clearAll(): AppResult<Int> = io("clearAll") { dao.clearAll() }

    private suspend fun <T> io(what: String, block: suspend () -> T): AppResult<T> =
        withContext(dispatchers.io) {
            runCatching { block() }.fold(
                onSuccess = { AppResult.Success(it) },
                onFailure = {
                    log.e(it) { "Hidden notifications $what failed" }
                    AppResult.Failure(AppError.Storage(cause = it.message))
                },
            )
        }

    private companion object {
        /**
         * The competitor's cap, kept — `od.i.B()` guards on `size == 100`
         * (`docs/screens/17-notification-and-permissions.md` §3.5). What changes is the comparison,
         * not the number.
         */
        const val MAX_ROWS = 100
    }
}

/** Epoch millis on the column, an `Instant` on the model: a column is a number (`HiddenNotificationEntity`). */
private fun HiddenNotificationEntity.toModel() = HiddenNotification(
    key = key,
    packageName = packageName,
    title = title,
    body = body,
    postedAt = Instant.fromEpochMilliseconds(postedAtEpochMillis),
)

private fun HiddenNotification.toEntity() = HiddenNotificationEntity(
    key = key,
    packageName = packageName,
    title = title,
    body = body,
    postedAtEpochMillis = postedAt.toEpochMilliseconds(),
)
