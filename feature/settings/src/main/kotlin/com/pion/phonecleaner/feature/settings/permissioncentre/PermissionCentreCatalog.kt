package com.pion.phonecleaner.feature.settings.permissioncentre

import com.pion.phonecleaner.domain.model.permission.AppPermission
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

/**
 * Which permissions this screen manages, and how each one is asked for.
 *
 * ### UNKNOWN — the roster is not stated by any source
 *
 * `docs/screens/20-settings-language-and-push.md` §8 open item 6 says the screen "renders
 * `permissions.snapshot()`, and which four appear is `PermissionRepository`'s answer, not a
 * hard-coded list here". **The port has no such method.** `PermissionRepository` (see its KDoc, and
 * `data/permission/AndroidPermissionRepository.kt`) exposes `observe(): Flow<ImmutableSet<AppPermission>>`
 * — the *granted subset* of all eleven constants — plus `isGranted` and `missingFor(feature)`.
 * A granted subset cannot name the rows to draw, so the roster has to be stated somewhere; it is
 * stated here, once, with its exclusions.
 *
 * Looked for a roster in: that appendix §5 (four unnamed cards), `docs/system-architecture.md` §4.4
 * (which defines the enum and no per-screen subset), and `FeatureCatalog.requirements`, which
 * deliberately carries only three rows and two of those are behind pending owner decisions.
 *
 * ### What is deliberately NOT on the list, and why each exclusion is a rule and not an oversight
 *
 * | Excluded | Why |
 * |---|---|
 * | `AllFiles` | `MANAGE_EXTERNAL_STORAGE` is **never assumed grantable** (`docs/system-architecture.md` §8.1, owner decision). A card that asks for it makes the default `MediaStore` + SAF branch look like a fallback |
 * | `UsageStats` | The ask lives in **App Manager**, beside the *Last used* column that needs it, where the reason is on screen (`docs/screens/14` §5). A settings row asking for it out of context is the ask this catalogue does not make, and the running-apps half of decision 3 is still open |
 * | `Overlay` | the App Lock overlay is a Play-policy decision, not an engineering one (`LLM.md` §7.5). `SYSTEM_ALERT_WINDOW` is not declared in `:data`'s manifest for the same reason |
 * | `WhatsAppFolder` | a SAF tree grant, taken in the WhatsApp screen's own flow against a specific directory. There is nothing for a settings row to open |
 * | `WriteSettings` · `DoNotDisturb` · `IgnoreBatteryOptimizations` | no code in this app requests or reads them, and no feature declares them in `FeatureCatalog`. A card for a permission nothing uses is a grant asked for nothing |
 *
 * A card that is missing fails **visibly** — the feature that needs it reports
 * `AppError.PermissionDenied` from its repository. A card that is present and wrong asks a user to
 * grant something for no reason, which is the harder failure to notice.
 */
internal object PermissionCentreCatalog {

    /** In render order. */
    val managed: ImmutableList<AppPermission> = persistentListOf(
        AppPermission.Storage,
        AppPermission.Media,
        AppPermission.Notifications,
        AppPermission.NotificationListener,
    )

    /**
     * How the grant is taken.
     *
     * The distinction is not cosmetic: a [Kind.Runtime] permission is a system dialog that returns a
     * result, and a [Kind.SpecialAccess] one is a system **screen** that returns nothing — which is
     * why the second kind is always preceded by the rationale sheet's numbered steps and followed by
     * a `ScreenResumed` re-read (§5.4 deltas 1 and 4).
     */
    enum class Kind { Runtime, SpecialAccess }

    fun kindOf(permission: AppPermission): Kind = when (permission) {
        AppPermission.NotificationListener -> Kind.SpecialAccess
        else -> Kind.Runtime
    }

    /**
     * Whether tapping the card opens the rationale sheet first.
     *
     * `Notifications` goes straight to the system dialog, matching the competitor, which shows no
     * dialog there (§5.2). Everything else explains itself first — and for a special access that
     * explanation is the only place the numbered steps can appear, because drawing over the system
     * Settings app is deleted (§5.4 delta 4, `LLM.md` §7.5).
     */
    fun needsRationale(permission: AppPermission): Boolean =
        permission != AppPermission.Notifications
}
