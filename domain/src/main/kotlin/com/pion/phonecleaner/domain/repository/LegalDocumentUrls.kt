package com.pion.phonecleaner.domain.repository

import com.pion.phonecleaner.domain.model.settings.LegalDocument

/**
 * Where each legal document lives. **Configuration, not an enum property**
 * (`docs/screens/20-settings-language-and-push.md` §0, §4.4 delta 8).
 *
 * The competitor inlines its two URLs at four call sites and keeps two further unread copies in
 * `java/ic/c.java:60-61` and `java/md/r3.java:12-16` — three copies of two strings, of which only one
 * set is live (`docs/reverse-engineering/20-settings-language-and-push.md:724`). One reader, here.
 *
 * It is an interface and not a constant because §8 open item 2 leaves its home open: if the app ever
 * gains a remote-configuration surface the implementation moves and nothing else changes.
 *
 * **[of] may return an empty string**, and the WebView screen renders that as "not available" rather
 * than loading something. See `DefaultLegalDocumentUrls` in `:data` for why the values are not
 * settled.
 */
interface LegalDocumentUrls {

    /** The URL for [document], or an empty string when this build has none configured. */
    fun of(document: LegalDocument): String
}
