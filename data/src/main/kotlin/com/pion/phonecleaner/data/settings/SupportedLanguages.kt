package com.pion.phonecleaner.data.settings

import com.pion.phonecleaner.domain.model.settings.AppLanguage
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

/**
 * The rows the language picker offers, in render order.
 *
 * ### Where this list comes from
 *
 * It is the competitor's own seventeen, recorded verbatim at
 * `docs/reverse-engineering/20-settings-language-and-push.md:246-266` (built by `ae.t.K()`,
 * `java/ae/t.java:201`), **re-expressed as BCP-47 tags** exactly as
 * `docs/screens/20-settings-language-and-push.md` §2.4 delta 5 requires: "BCP-47 tags as identity,
 * endonyms as string resources, reviewed once before the list ships".
 *
 * Three rows change against that table, each for a reason the source itself states:
 *
 * | Recorded | Here | Why |
 * |---|---|---|
 * | `in` + `ID` | `id-ID` | `in` is the deprecated ISO code; the chapter names it as a data defect (`:270`) |
 * | `zh`+`CN` / `zh`+`TW` | `zh-Hans-CN` / `zh-Hant-TW` | `new Locale(lang, country)` cannot express a script subtag, which is the third defect the tag closes (`:632`) |
 * | 한국인 | 한국어 | "Korean person", not "Korean language" — named at `:271` |
 *
 * One further label is corrected without an explicit instruction and is flagged here rather than
 * left silent: the recorded Turkish endonym is **Türk** (the demonym). The language's endonym is
 * *Türkçe*, and shipping a known-wrong label because no line of the corpus says so would be
 * propagating the defect the delta exists to remove.
 *
 * The competitor's duplicated `index = 15` (two rows share it, `ae/t.java:229`, `:233`) has no port:
 * the tag is the identity, so a collision is not expressible.
 *
 * ### UNKNOWN — whether this app ships all seventeen
 *
 * `docs/screens/20-settings-language-and-push.md` §8 open item 4 states plainly that the roster is a
 * **product decision, not a design question**, and no source settles it. Looked for a shipped-locale
 * list in that appendix §2 and in `LLM.md` §10.4 (build types and source sets, which names no locale
 * set); neither carries one.
 *
 * **Two of the seventeen have a translation set: `en-US` (`values/`) and `vi-VN` (`values-vi/`, in
 * all thirteen modules that own copy).** Picking either changes the UI. The other fifteen rows still
 * render their endonym and then leave the UI in English, because a locale with no `values-<code>/`
 * falls back to the default resources — the row is honest about which language it selects, and wrong
 * about nothing, but it does not yet do anything.
 *
 * `vi-VN` resolves against `values-vi`: Android matches the language subtag when no
 * `values-vi-rVN` exists, so one directory per module covers the tag. Vietnamese has ONE CLDR plural
 * category, `other`, so every `<plurals>` in `values-vi` carries that body alone.
 *
 * The list is carried in full anyway, because the alternative is inventing a shorter one, and because
 * removing rows later is a one-line edit here while the plumbing is identical either way. **Shipping
 * this picker to users requires that decision first**, plus the remaining fifteen translation sets.
 *
 * ### VERIFIED DEFECT — the picker stores a choice that nothing applies
 *
 * Measured on `RF8Y60B9NCZ` (SM-A165F, Android 16) once `values-vi` existed and the failure became
 * observable for the first time: selecting a language and pressing Save **does** write the tag (the
 * settings row re-reads as "English"), but `adb shell cmd locale get-app-locales` stays `[]` and the
 * UI does not change — not on the spot, and not after a force-stop and relaunch either.
 *
 * `AppCompatDelegate.setApplicationLocales` (`LanguageRoute.kt:73`) is the only apply path, and it
 * reaches `LocaleManager` on API 33+ through a registered `AppCompatActivity` delegate or the
 * `AppLocalesMetadataHolderService` manifest entry. This app has **neither** — `MainActivity` is a
 * `ComponentActivity` (`app/.../MainActivity.kt:21`) and no manifest declares that service — so the
 * call is a silent no-op. `LanguageRoute`'s KDoc flags the host type as UNKNOWN; this is that open
 * item observed failing rather than suspected.
 *
 * Until it is closed, Vietnamese is reachable only by the device's own system language, which does
 * work: with no app override the app resolves `values-vi` from the system locale.
 */
internal object SupportedLanguages {

    val all: ImmutableList<AppLanguage> = persistentListOf(
        AppLanguage("en-US"),      // English
        AppLanguage("zh-Hans-CN"), // 简体中文
        AppLanguage("pt-PT"),      // português
        AppLanguage("es-ES"),      // Español
        AppLanguage("fr-FR"),      // Français
        AppLanguage("de-DE"),      // Deutsch
        AppLanguage("th-TH"),      // ไทย
        AppLanguage("id-ID"),      // Bahasa Indonesia — `in` corrected to `id`
        AppLanguage("ms-MY"),      // Melayu
        AppLanguage("vi-VN"),      // Tiếng Việt
        AppLanguage("ja-JP"),      // 日本語
        AppLanguage("it-IT"),      // Italiano
        AppLanguage("ko-KR"),      // 한국어 — corrected from 한국인
        AppLanguage("nl-NL"),      // Nederlands
        AppLanguage("zh-Hant-TW"), // 繁體中文
        AppLanguage("tr-TR"),      // Türkçe — corrected from Türk
        AppLanguage("ru-RU"),      // Русский
    )

    /** Null for an unknown or absent tag: an unrecognised stored value is "follow the system". */
    fun byTag(tag: String?): AppLanguage? = tag?.let { wanted -> all.firstOrNull { it.tag == wanted } }
}
