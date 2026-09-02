package com.pion.phonecleaner.domain.repository

import com.pion.phonecleaner.domain.model.network.SpeedTestProgress
import kotlinx.coroutines.flow.Flow

/**
 * A connection measurement (`docs/screens/19-network-and-speed-test.md` §2).
 *
 * **This interface is the whole point of the design.** The byte source a real measurement needs — a
 * host, a payload size, a ramp-up policy — is an open owner decision and procurement rather than
 * design (§2, §5 item 1). Writing the screens against this port keeps that decision contained: when
 * it is settled, one implementation class changes and nothing above it does; if the answer is "no
 * host", one implementation and two screens are deleted and the traffic half is untouched.
 *
 * The implementation chooses its own dispatcher through `DispatcherProvider` and bounds each leg of
 * the measurement itself. **The ViewModel never sees a socket.**
 *
 * A faithful port of the competitor is not an option: it opens no socket at all and reports a
 * device-wide `TrafficStats` delta divided by an advert's dwell time. That number cannot be described
 * to a user without making a claim the wording rules forbid.
 */
interface SpeedTestRepository {
    fun measure(): Flow<SpeedTestProgress>
}
