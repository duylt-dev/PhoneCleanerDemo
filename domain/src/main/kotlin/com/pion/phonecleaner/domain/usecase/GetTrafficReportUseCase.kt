package com.pion.phonecleaner.domain.usecase

import com.pion.phonecleaner.core.common.result.AppResult
import com.pion.phonecleaner.domain.model.network.TrafficPeriod
import com.pion.phonecleaner.domain.model.network.TrafficReport
import com.pion.phonecleaner.domain.repository.NetworkTrafficRepository

/**
 * One query per period selection — the whole traffic screen.
 *
 * The competitor runs this query **twice** per visit: once on a 3 s loading Activity whose result it
 * discards, and again on the list (`docs/screens/19-network-and-speed-test.md` §1.5 D3). One screen,
 * one query.
 */
class GetTrafficReportUseCase(
    private val traffic: NetworkTrafficRepository,
) {
    suspend operator fun invoke(period: TrafficPeriod): AppResult<TrafficReport> =
        traffic.report(period)
}
