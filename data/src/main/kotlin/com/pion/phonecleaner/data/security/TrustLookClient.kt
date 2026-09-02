package com.pion.phonecleaner.data.security

import android.content.Context
import com.pion.phonecleaner.core.common.concurrent.DispatcherProvider
import com.pion.phonecleaner.core.common.log.AppLogger
import com.pion.phonecleaner.domain.model.security.ScanFailure
import com.pion.phonecleaner.domain.model.security.SecurityScanPhase
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn

/**
 * **The ONLY file in this app that may ever import `com.trustlook.**`** (`LLM.md` §4, the last row of
 * the "where a new file goes" table; `docs/screens/15-antivirus.md` §0.3). One file owning the
 * vendor's lifecycle is what makes a single `awaitClose { }` a complete cancellation story.
 *
 * ### It imports nothing from the vendor today, and still compiles
 *
 * **The SDK has no Maven coordinate anywhere in the corpus** — only decompiled classes, and
 * procurement has not resolved it (`docs/screens/15-antivirus.md` open item 1). No dependency is
 * added to any build file here, and no vendor class name is invented: the decompiled names are
 * obfuscated (`m8.a`, `m8.b`, `m8.l`) and the real ones are UNKNOWN. Until the AAR lands,
 * [TrustLookClientImpl] fails immediately with [ScanFailure.SdkUnavailable] and logs which dependency
 * is missing.
 *
 * ### What "adding the AAR is the only change needed" means, concretely
 *
 * The shape below is already the shape §0.3 specifies, and the work left is confined to the marked
 * block inside [TrustLookClientImpl.scan]:
 *
 *  1. build the vendor client with [context], region `INTL`, 30 000 ms connect and 30 000 ms socket
 *     timeouts (`docs/reverse-engineering/15-antivirus.md` §3.1);
 *  2. attach its listener and map the five callbacks onto [SecurityScanPhase] — *started* →
 *     [SecurityScanPhase.Preparing]; *progress(current, total, app)* →
 *     [SecurityScanPhase.Scanning]; *finished(list)* → [SecurityScanPhase.Finished]; *error(code,
 *     message)* → [SecurityScanPhase.Failed] through [scanFailureOf]; *cancelled* → `close()`;
 *  3. start the scan, passing [certSha1MaxMegabytes];
 *  4. put the vendor's `cancel()` in the existing `awaitClose`.
 *
 * The competitor needs **three** separate call sites to raise the same cancel flag and still leaks
 * the SDK's worker `Thread`; and it overrides the *cancelled* callback with an empty body, so a
 * cancelled scan produces no state at all and the flow would hang (§3.2). Closing on that callback is
 * the whole difference.
 *
 * **UNKNOWN, and must stay so**: the request shape and the batch size. The vendor's batch loop and
 * request builder are not decompiled (`docs/reverse-engineering/15-antivirus.md` §4.2, and
 * `docs/screens/15-antivirus.md` open item 7). Nothing in this design reads either — the client is a
 * black box behind five callbacks — and no future document may state them as known.
 */
interface TrustLookClient {

    /**
     * Cold. Collection starts the scan; cancelling the collector cancels the vendor client.
     *
     * [certSha1MaxMegabytes] is **not a batch size** — naming it is the point of this wrapper. The
     * competitor passes `100` to a method whose parameter it never names; that value is the maximum
     * APK size, in megabytes, for which the signing certificate's SHA-1 is computed, and a larger APK
     * is submitted with an empty `cs1` (`docs/reverse-engineering/15-antivirus.md` §3.3, §4.4).
     */
    fun scan(certSha1MaxMegabytes: Int = CERT_SHA1_CAP_MB): Flow<SecurityScanPhase>
}

/** The competitor's own value, lowered from the SDK default of 300 MB. Ported, not chosen. */
const val CERT_SHA1_CAP_MB: Int = 100

/**
 * Maps the vendor's nine error codes onto [ScanFailure] (`docs/screens/15-antivirus.md` §0.2).
 *
 * Written now, and unused until the AAR lands, because it is the one piece of the integration that is
 * fully specified and testable without the SDK.
 *
 * `code == 2` is genuinely ambiguous — the vendor raises it both for HTTP 406 and for "nothing at all
 * to scan" — so **the caller disambiguates it by asking whether anything was enumerated, never by
 * trusting the code**; that is what [nothingEnumerated] carries.
 */
internal fun scanFailureOf(code: Int, nothingEnumerated: Boolean): ScanFailure = when (code) {
    6 -> ScanFailure.NoNetwork
    7 -> ScanFailure.Timeout
    8 -> ScanFailure.BadApiKey
    4, 5, 9 -> ScanFailure.ServerError
    2 -> if (nothingEnumerated) ScanFailure.NothingToScan else ScanFailure.Unknown
    else -> ScanFailure.Unknown
}

/**
 * The one implementation. A Koin `single`, so the client is built **once per process**: the vendor's
 * constructor wipes its own preference file on every construction, destroying its verdict table and
 * any pending upload queue, and the competitor builds one per Activity entry
 * (`docs/screens/15-antivirus.md` §1.5). The wipe is SDK-internal and out of scope to change; how
 * often it happens is not.
 */
internal class TrustLookClientImpl(
    private val context: Context,
    private val dispatchers: DispatcherProvider,
    private val log: AppLogger,
) : TrustLookClient {

    override fun scan(certSha1MaxMegabytes: Int): Flow<SecurityScanPhase> = callbackFlow {
        // ── the vendor block ───────────────────────────────────────────────────────────────────
        // Steps 1-3 of the file KDoc go here, over `context` and `certSha1MaxMegabytes`. Nothing
        // else in the app changes when they land.
        log.d { "$MISSING_DEPENDENCY — cloud scan unavailable in this build" }
        trySend(SecurityScanPhase.Failed(ScanFailure.SdkUnavailable))
        close()
        // ───────────────────────────────────────────────────────────────────────────────────────
        awaitClose {
            // Step 4: the vendor client's cancel() goes here. It runs on collector cancellation, on
            // close() and on scope death — one cancellation story instead of the competitor's three.
        }
    }.flowOn(dispatchers.io)

    private companion object {
        /**
         * Named exactly, so a failing build says what is missing rather than "unknown error".
         * UNKNOWN — the Maven coordinate. Looked for, and not found: `gradle/libs.versions.toml`,
         * `docs/screens/15-antivirus.md` open item 1 (which states there is none in the corpus), and
         * `docs/reverse-engineering/15-antivirus.md` §4.2. Only decompiled classes exist.
         */
        const val MISSING_DEPENDENCY =
            "Trustlook SDK dependency is not on the classpath (no Maven coordinate resolved)"
    }
}
