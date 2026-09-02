package com.pion.phonecleaner.feature.settings.testing

import com.pion.phonecleaner.core.common.error.AppError
import com.pion.phonecleaner.core.common.result.AppResult
import com.pion.phonecleaner.domain.model.permission.AppPermission
import com.pion.phonecleaner.domain.model.push.PushMessage
import com.pion.phonecleaner.domain.model.settings.AppLanguage
import com.pion.phonecleaner.domain.model.settings.LegalDocument
import com.pion.phonecleaner.domain.repository.AppInfoProvider
import com.pion.phonecleaner.domain.repository.LanguageRepository
import com.pion.phonecleaner.domain.repository.LegalDocumentUrls
import com.pion.phonecleaner.domain.repository.PermissionRepository
import com.pion.phonecleaner.domain.repository.PushRepository
import com.pion.phonecleaner.domain.repository.ResidentWidgetSettingsRepository
import com.pion.phonecleaner.domain.model.feature.FeatureId
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toImmutableSet
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Hand-written fakes, no mocking library (`LLM.md` §9). Every one of them is small enough to read in
 * one screen, and each carries the switchable failure mode its suite needs.
 */
internal class FakeLanguageRepository(
    var roster: ImmutableList<AppLanguage> = persistentListOf(
        AppLanguage("en-US"),
        AppLanguage("ja-JP"),
    ),
    var writeResult: AppResult<Unit> = AppResult.Success(Unit),
    var throwOnWrite: Boolean = false,
) : LanguageRepository {

    val applied = MutableStateFlow<AppLanguage?>(null)
    val writes = mutableListOf<String?>()

    override fun supportedLanguages(): ImmutableList<AppLanguage> = roster

    override fun currentLanguage(): Flow<AppLanguage?> = applied

    override suspend fun setLanguage(tag: String?): AppResult<Unit> {
        writes += tag
        if (throwOnWrite) error("language write blew up")
        return writeResult
    }
}

internal class FakeAppInfoProvider(
    override val appName: String = "Phone Cleaner",
    override val versionName: String = "1.2.3",
    override val versionCode: Long = 42L,
    override val isDebugBuild: Boolean = false,
) : AppInfoProvider

internal class FakeLegalDocumentUrls(
    var urls: Map<LegalDocument, String> = emptyMap(),
) : LegalDocumentUrls {
    override fun of(document: LegalDocument): String = urls[document].orEmpty()
}

internal class FakeResidentWidgetSettings(
    var writeResult: AppResult<Unit> = AppResult.Success(Unit),
) : ResidentWidgetSettingsRepository {

    val enabled = MutableStateFlow(false)
    val writes = mutableListOf<Boolean>()

    override fun isEnabled(): Flow<Boolean> = enabled

    override suspend fun setEnabled(enabled: Boolean): AppResult<Unit> {
        writes += enabled
        // The real store re-emits and the screen renders THAT, never an optimistic copy — so a fake
        // whose write fails must leave the flow alone.
        if (writeResult is AppResult.Success) this.enabled.value = enabled
        return writeResult
    }
}

/**
 * [granted] is mutable so a test can grant between two `ScreenResumed` intents — the round trip the
 * competitor's Permission Centre gets wrong.
 */
internal class FakePermissionRepository(
    var granted: Set<AppPermission> = emptySet(),
) : PermissionRepository {

    val emissions = MutableStateFlow<ImmutableSet<AppPermission>>(persistentSetOf())

    override fun observe(): Flow<ImmutableSet<AppPermission>> = emissions

    override fun isGranted(permission: AppPermission): Boolean = permission in granted

    override fun missingFor(feature: FeatureId): ImmutableSet<AppPermission> = persistentSetOf()

    /** Grants [permissions] and pushes the same answer down [observe], as the real port would. */
    fun grant(vararg permissions: AppPermission) {
        granted = granted + permissions
        emissions.value = granted.toImmutableSet()
    }
}

internal class FakePushRepository(
    var result: AppResult<Unit> = AppResult.Success(Unit),
    var throwOnHandle: Boolean = false,
) : PushRepository {

    val handled = mutableListOf<PushMessage>()

    override suspend fun handle(message: PushMessage): AppResult<Unit> {
        handled += message
        if (throwOnHandle) error("push handler blew up")
        return result
    }
}

/** The failure every `onError` branch is asserted against. */
internal val StorageFailure: AppResult<Unit> = AppResult.Failure(AppError.Storage(cause = "disk"))
