package com.pion.phonecleaner.feature.settings.language

import androidx.annotation.StringRes
import com.pion.phonecleaner.feature.settings.R

/**
 * `AppLanguage.tag -> @StringRes` — the endonym mapping.
 *
 * ### Why it is here and not on the model
 *
 * `docs/screens/20-settings-language-and-push.md` §0 puts `@StringRes val displayNameRes: Int` on
 * `AppLanguage`. That annotation is `androidx.annotation`, i.e. Android, and `:domain` is compiled
 * without it (`LLM.md` §2). **The rule bends by moving the type, never by weakening the rule** —
 * exactly as `FeatureCatalog` keeps `FeatureId` while `FeatureDescriptor` in `:core:ui/catalog` holds
 * the resource ids. `AppLanguage` in `:domain` therefore carries the tag alone, and the resource id
 * lives beside the only screen that renders it.
 *
 * The digest's note on `AppLanguage` proposes `:core:ui` as that home. It is here instead, because
 * `LLM.md` §4 promotes a composable or a table to `:core:ui` only once **two clusters** need it, and
 * exactly one screen in the app renders a language name. Promoting it is a file move on the day a
 * second one does.
 *
 * ### Why the endonyms are string resources at all
 *
 * They must never be translated: an endonym is a language's name *in that language*, and its only
 * job is to be readable by someone who cannot read the current UI language. Every entry in
 * `strings.xml` is `translatable="false"`. Resolving them at **render** time is also the fix for the
 * competitor's worst localisation defect — `ae.i2` resolves its names to `String`s from `Resources`
 * at object initialisation (`java/ae/i2.java:315-415`), so in a 17-locale app every name stays stale
 * until the process restarts (`LLM.md` §2).
 *
 * A tag with no row here returns `null` and the screen falls back to the tag itself: a language that
 * is offered but unlabelled must still be selectable, and an honest "pt-PT" beats a blank row.
 */
@StringRes
internal fun endonymRes(tag: String): Int? = ENDONYMS[tag]

private val ENDONYMS: Map<String, Int> = mapOf(
    "en-US" to R.string.settings_language_en_us,
    "zh-Hans-CN" to R.string.settings_language_zh_hans_cn,
    "pt-PT" to R.string.settings_language_pt_pt,
    "es-ES" to R.string.settings_language_es_es,
    "fr-FR" to R.string.settings_language_fr_fr,
    "de-DE" to R.string.settings_language_de_de,
    "th-TH" to R.string.settings_language_th_th,
    "id-ID" to R.string.settings_language_id_id,
    "ms-MY" to R.string.settings_language_ms_my,
    "vi-VN" to R.string.settings_language_vi_vn,
    "ja-JP" to R.string.settings_language_ja_jp,
    "it-IT" to R.string.settings_language_it_it,
    "ko-KR" to R.string.settings_language_ko_kr,
    "nl-NL" to R.string.settings_language_nl_nl,
    "zh-Hant-TW" to R.string.settings_language_zh_hant_tw,
    "tr-TR" to R.string.settings_language_tr_tr,
    "ru-RU" to R.string.settings_language_ru_ru,
)

/** The identity of the "follow the system" row, which has no [AppLanguage] and therefore no tag. */
internal const val SYSTEM_DEFAULT_KEY: String = "language.system-default"
