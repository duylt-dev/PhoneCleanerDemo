package com.pion.phonecleaner.domain.model.security

/**
 * Why a scan stopped without a result.
 *
 * The vendor SDK raises nine error codes and the competitor maps **all nine onto one sentence** in a
 * dialog whose only button calls `finish()` (`docs/reverse-engineering/15-antivirus.md` §3.2, defect
 * 10). The collapse below is the one stated in `docs/screens/15-antivirus.md` §0.2:
 *
 * | SDK code | arm |
 * |---|---|
 * | `6` `UnknownHostException`, or the pre-flight connectivity check | [NoNetwork] |
 * | `7` `SocketTimeoutException`, or our own budget | [Timeout] |
 * | `8` HTTP 403 | [BadApiKey] |
 * | `9` HTTP 502/504 · `5` `IOException` · `4` `JSONException` | [ServerError] |
 * | `2` raised because nothing was enumerated | [NothingToScan] |
 * | `0`, and `2` from the exception mapper for HTTP 406 | [Unknown] |
 *
 * Code `2` is genuinely ambiguous in the competitor. **The repository disambiguates it by asking the
 * enumerator whether it produced zero candidates, never by trusting the code** (§0.2).
 */
enum class ScanFailure {
    NoNetwork,
    Timeout,
    BadApiKey,
    ServerError,
    NothingToScan,

    /**
     * The scan cannot run in this build because the vendor SDK is not on the classpath.
     *
     * DELIBERATE ADDITION to the six arms of `docs/screens/15-antivirus.md` §0.2, which assumes the
     * dependency ships. It does not yet: **the SDK has no Maven coordinate anywhere in the corpus**
     * — only decompiled classes — and that appendix's own open item 1 says so. The alternative was
     * [Unknown], which renders as "something went wrong" beside a retry button that can never
     * succeed. This arm is the honest branch, and `data/security/TrustLookClient.kt` is the only
     * place that raises it; it disappears from the UI the moment the AAR is resolved.
     */
    SdkUnavailable,

    Unknown,
    ;

    /** Retrying [SdkUnavailable] cannot succeed — a missing dependency is not a transient failure. */
    val isRetryable: Boolean get() = this != SdkUnavailable
}
