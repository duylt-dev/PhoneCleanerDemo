package com.pion.phonecleaner.feature.antivirus.scan

import com.pion.phonecleaner.domain.model.security.ScanConsentState
import com.pion.phonecleaner.domain.model.security.ScanCoverage
import org.junit.Test
import kotlin.test.assertEquals

/**
 * The whole entry funnel as a pure function, with no fakes and no dispatcher — which is the point of
 * it being one (`ScanGateDecision`'s KDoc). The competitor re-implements these three checks in two
 * different callers, so a deep link reaches neither.
 */
internal class ScanGateDecisionTest {

    @Test
    fun `network is the first gate, ahead of consent and storage`() {
        val decision = scanGateDecision(
            isOnline = false,
            consent = ScanConsentState.Unanswered,
            hasStorageAccess = false,
            hasAskedForStorage = false,
        )
        assertEquals(ScanGateDecision.Blocked(ScanGate.Network), decision)
    }

    @Test
    fun `an unanswered disclosure asks, a rejected one blocks`() {
        assertEquals(
            ScanGateDecision.AskConsent,
            scanGateDecision(true, ScanConsentState.Unanswered, true, false),
        )
        assertEquals(
            ScanGateDecision.Blocked(ScanGate.Consent),
            scanGateDecision(true, ScanConsentState.Rejected, true, false),
        )
    }

    @Test
    fun `the storage gate blocks once and then never again`() {
        assertEquals(
            ScanGateDecision.Blocked(ScanGate.StoragePermission),
            scanGateDecision(true, ScanConsentState.Granted, false, hasAskedForStorage = false),
        )
        assertEquals(
            ScanGateDecision.Run(ScanCoverage.InstalledAppsOnly),
            scanGateDecision(true, ScanConsentState.Granted, false, hasAskedForStorage = true),
        )
    }

    @Test
    fun `a granted storage read covers files as well as installed apps`() {
        assertEquals(
            ScanGateDecision.Run(ScanCoverage.InstalledAppsAndFiles),
            scanGateDecision(true, ScanConsentState.Granted, true, false),
        )
    }
}
