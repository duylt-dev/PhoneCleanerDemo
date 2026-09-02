package com.pion.phonecleaner.domain.model.settings

import kotlinx.serialization.Serializable

/**
 * Which legal page the in-app `WebView` route shows. Read by three clusters — splash/onboarding, home
 * and settings — which is why it lives here and not in any one of them.
 *
 * **The URL is deliberately not on the enum**: it is configuration, resolved by `LegalDocumentUrls`
 * in `:data` (`docs/screens/20-settings-language-and-push.md:67-70`). The competitor's screen takes a
 * free-form `strUrl` extra although only two literal URLs ever reach it from four call sites — a
 * general-purpose URL loader is attack surface for no benefit, and an unknown value cannot be
 * expressed once the argument is this enum.
 */
@Serializable
enum class LegalDocument {
    TermsOfService,
    PrivacyPolicy,
}
