package com.pion.phonecleaner.data.junk

import com.pion.phonecleaner.domain.model.junk.AppRule
import com.pion.phonecleaner.domain.model.junk.AppRuleRoot
import com.pion.phonecleaner.domain.model.junk.AppSubPath
import com.pion.phonecleaner.domain.model.junk.JunkContentType
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf

/**
 * Pass 2's table: apps that leave a **branded directory in public storage**, and what is inside it.
 *
 * A rule fires only when its package is **not installed** — junk here is the leftovers of an app the
 * user removed, which is the same inversion the competitor makes
 * (`docs/reverse-engineering/12-junk-cleaning.md` §6.3 finding 2). `RuleJunkScanner` supplies the
 * installed set from `InstalledAppsRepository`; a rule whose app is still on the device never becomes
 * a candidate, so this table can never propose deleting a live app's data.
 *
 * ## Why this list is eight apps and not two hundred
 *
 * The competitor's 208-app table is the largest single asset in its APK and it is **not carried**
 * (see [KotlinJunkRuleCatalog] — owner decision of 2026-09-06, branch B). Ours is written from what a
 * branded public directory demonstrably is, and it grows the same way: **one row per path someone has
 * actually seen on a device**, never a row guessed from a package name. The cost of the short list is
 * a smaller app-residual figure. The cost of a guessed list would be a row pointing at a directory
 * that belongs to something else, which is the one failure mode this pass must not have.
 *
 * A wrong path is cheap in the other direction: `residualCandidates` tests `File(path).isDirectory`
 * before a rule becomes a candidate, so a path that does not exist on a device costs one `stat` and
 * disappears.
 *
 * ## `scoped` is the honest half of this file
 *
 * A root under `Android/data` or `Android/obb` is **skipped**, deliberately and knowably, because on
 * API 30+ it is unreadable even holding `MANAGE_EXTERNAL_STORAGE`
 * (`gioi-han-android-phone-cleaner` §1). It is carried as a row with `scoped = true` rather than
 * deleted from the table, so the reason a modern app's leftovers are missing is written down instead
 * of being an unexplained absence. `Android/media/<pkg>` is **not** scoped — it is readable, and it is
 * where the messengers moved after Android 11.
 */
internal object JunkAppResidualRules {

    val RULES: ImmutableList<AppRule> = persistentListOf(
        whatsApp(),
        whatsAppBusiness(),
        telegram(),
        viber(),
        shareIt(),
        xender(),
        ucBrowser(),
        esFileExplorer(),
    )
}

/**
 * Two roots, because WhatsApp moved. Up to Android 10 it wrote `/WhatsApp` at the volume root; from
 * Android 11 it writes `/Android/media/com.whatsapp/WhatsApp`, which is readable — unlike
 * `Android/data`, which is why the app chose it. An uninstall leaves whichever one that device used.
 *
 * The sub-paths are what makes the review screen browsable per content type instead of one 1.2 GB row
 * labelled `WhatsApp` (`docs/screens/12-junk-cleaning.md` §4.4 Delta R5). `Databases` is the local
 * message backup — the single largest thing an uninstalled WhatsApp leaves behind.
 */
private fun whatsApp(): AppRule = AppRule(
    packageName = "com.whatsapp",
    label = "WhatsApp",
    aliases = persistentSetOf(),
    roots = persistentListOf(
        AppRuleRoot("WhatsApp", scoped = false, subPaths = whatsAppSubPaths()),
        AppRuleRoot(
            path = "Android/media/com.whatsapp/WhatsApp",
            scoped = false,
            subPaths = whatsAppSubPaths(),
        ),
    ),
)

private fun whatsAppBusiness(): AppRule = AppRule(
    packageName = "com.whatsapp.w4b",
    label = "WhatsApp Business",
    aliases = persistentSetOf(),
    roots = persistentListOf(
        AppRuleRoot("WhatsApp Business", scoped = false, subPaths = whatsAppSubPaths()),
        AppRuleRoot(
            path = "Android/media/com.whatsapp.w4b/WhatsApp Business",
            scoped = false,
            subPaths = whatsAppSubPaths(),
        ),
    ),
)

private fun whatsAppSubPaths(): ImmutableList<AppSubPath> = persistentListOf(
    AppSubPath("Media/WhatsApp Images", JunkContentType.ReceivedImages),
    AppSubPath("Media/WhatsApp Images/Sent", JunkContentType.SentImages),
    AppSubPath("Media/WhatsApp Video", JunkContentType.Video),
    AppSubPath("Media/WhatsApp Video/Sent", JunkContentType.SentVideo),
    AppSubPath("Media/WhatsApp Audio", JunkContentType.ReceivedAudio),
    AppSubPath("Media/WhatsApp Voice Notes", JunkContentType.VoiceNotes),
    AppSubPath("Media/WhatsApp Documents", JunkContentType.ReceivedDocs),
    AppSubPath("Media/WhatsApp Stickers", JunkContentType.Stickers),
    AppSubPath("Media/WhatsApp Animated Gifs", JunkContentType.AnimatedGifs),
    AppSubPath("Databases", JunkContentType.Backup),
)

/** Same two-location story as WhatsApp, for the same Android 11 reason. */
private fun telegram(): AppRule = AppRule(
    packageName = "org.telegram.messenger",
    // Telegram ships the same app under several package names — the web variant and the beta channel
    // both write the SAME `/Telegram` directory, so a rule keyed on one package alone would offer to
    // delete a live install's downloads. This is what `AppRule.aliases` is for; the competitor stores
    // the field in fifteen places and has no getter for it (`xc.o.j()`).
    aliases = persistentSetOf("org.telegram.messenger.web", "org.telegram.messenger.beta"),
    label = "Telegram",
    roots = persistentListOf(
        AppRuleRoot("Telegram", scoped = false, subPaths = telegramSubPaths()),
        AppRuleRoot(
            path = "Android/media/org.telegram.messenger/Telegram",
            scoped = false,
            subPaths = telegramSubPaths(),
        ),
    ),
)

private fun telegramSubPaths(): ImmutableList<AppSubPath> = persistentListOf(
    AppSubPath("Telegram Images", JunkContentType.Images),
    AppSubPath("Telegram Video", JunkContentType.Video),
    AppSubPath("Telegram Audio", JunkContentType.Audio),
    AppSubPath("Telegram Documents", JunkContentType.Documents),
)

private fun viber(): AppRule = AppRule(
    packageName = "com.viber.voip",
    label = "Viber",
    aliases = persistentSetOf(),
    // Lower-case `viber` is the directory the app creates; the walk resolves it case-sensitively
    // because the underlying filesystem is, and `residualCandidates` only lower-cases for its
    // distinct-check.
    roots = persistentListOf(AppRuleRoot("viber", scoped = false, subPaths = persistentListOf())),
)

/**
 * The file-transfer apps. Both keep everything they have ever received in a branded root, and both
 * are the classic install-once-and-forget app — which is exactly the case pass 2 exists for.
 */
private fun shareIt(): AppRule = AppRule(
    packageName = "com.lenovo.anyshare.gps",
    label = "SHAREit",
    aliases = persistentSetOf("shareit.lite"),
    roots = persistentListOf(
        AppRuleRoot("SHAREit", scoped = false, subPaths = persistentListOf()),
        AppRuleRoot("QuickSend", scoped = false, subPaths = persistentListOf()),
    ),
)

private fun xender(): AppRule = AppRule(
    packageName = "cn.xender",
    label = "Xender",
    aliases = persistentSetOf(),
    roots = persistentListOf(AppRuleRoot("Xender", scoped = false, subPaths = persistentListOf())),
)

private fun ucBrowser(): AppRule = AppRule(
    packageName = "com.UCMobile.intl",
    label = "UC Browser",
    aliases = persistentSetOf("com.uc.browser.en"),
    roots = persistentListOf(
        AppRuleRoot("UCDownloads", scoped = false, subPaths = persistentListOf()),
        // Declared and skipped, on purpose: this is the row that shows what `scoped` costs. Every
        // modern app's real cache lives at a path shaped like this one, and none of them is readable.
        AppRuleRoot("Android/data/com.UCMobile.intl", scoped = true, subPaths = persistentListOf()),
    ),
)

private fun esFileExplorer(): AppRule = AppRule(
    packageName = "com.estrongs.android.pop",
    label = "ES File Explorer",
    aliases = persistentSetOf(),
    roots = persistentListOf(
        AppRuleRoot(".estrongs", scoped = false, subPaths = persistentListOf()),
    ),
)
