package com.pion.phonecleaner.feature.settings.webview

import androidx.lifecycle.SavedStateHandle
import com.pion.phonecleaner.domain.model.settings.LegalDocument

/**
 * The `SavedStateHandle` key the route argument arrives under, and the one reader of it.
 *
 * **The route argument IS the initial state** (MVI §5): it is read once, in the ViewModel's
 * constructor, and never re-read from an `Intent` on recreation. That is what makes this screen
 * process-death-safe, which an Intent-extra-into-a-field never is
 * (`docs/screens/20-settings-language-and-push.md` §4.2).
 *
 * UNKNOWN — `:app/navigation/Routes.kt` is not this cluster's file and today declares only `Home`.
 * With Navigation-Compose type-safe destinations the key is the route class's **property name**, so
 * [DOCUMENT] must match the property name reported in `routesNeeded`:
 * `@Serializable data class WebView(val document: LegalDocument)`. Until that route lands the read
 * returns the default, [LegalDocument.PrivacyPolicy] — the more conservative of the two, because a
 * privacy policy is the document a user is more likely to be looking for and neither is reachable by
 * accident.
 */
internal object WebViewArgs {
    const val DOCUMENT = "document"
}

/**
 * Reads the argument without asserting how Navigation stored it: a type-safe enum argument may come
 * back as the enum itself or as its `name`, and `SavedStateHandle.get<T>` is an unchecked cast that
 * would fail at the use site rather than here.
 */
internal fun SavedStateHandle.legalDocument(): LegalDocument =
    when (val raw = get<Any>(WebViewArgs.DOCUMENT)) {
        is LegalDocument -> raw
        is String -> LegalDocument.entries.firstOrNull { it.name == raw }
        else -> null
    } ?: LegalDocument.PrivacyPolicy
