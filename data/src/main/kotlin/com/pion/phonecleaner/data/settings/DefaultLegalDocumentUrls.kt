package com.pion.phonecleaner.data.settings

import com.pion.phonecleaner.domain.model.settings.LegalDocument
import com.pion.phonecleaner.domain.repository.LegalDocumentUrls

/**
 * The one place the legal URLs live (`docs/screens/20-settings-language-and-push.md` §4.4 delta 8,
 * §8 open item 2).
 *
 * UNKNOWN — **this app has no legal URLs and no source in the corpus states one.** Looked for:
 * `docs/screens/20-settings-language-and-push.md` §4 and §8 (which name the type and its home and
 * give no values), `docs/system-architecture.md` §4.1's alias table (the type is not in it), and
 * `docs/reverse-engineering/20-settings-language-and-push.md:389-390`, which records two URLs — both
 * are the **competitor's own** `sites.google.com/view/antivirusflux-*` pages. Using either would ship
 * another company's terms as ours, which is worse than shipping nothing.
 *
 * So both entries are empty, and `WebViewScreen` renders "not available yet" instead of loading
 * anything. A blank is honest; a plausible-looking `https://…/privacy` that 404s looks checked.
 *
 * **What closing this looks like:** the owner supplies two URLs, they are written below, and nothing
 * else in the app changes — the enum argument, the route and the screen are all already in place.
 */
internal class DefaultLegalDocumentUrls : LegalDocumentUrls {

    override fun of(document: LegalDocument): String = when (document) {
        LegalDocument.TermsOfService -> TERMS_URL
        LegalDocument.PrivacyPolicy -> PRIVACY_URL
    }

    private companion object {
        /** UNKNOWN — see the class KDoc. Empty, never a placeholder that looks like a real page. */
        const val TERMS_URL = ""
        const val PRIVACY_URL = ""
    }
}
