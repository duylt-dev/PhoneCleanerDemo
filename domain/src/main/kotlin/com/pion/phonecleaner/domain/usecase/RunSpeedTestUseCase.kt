package com.pion.phonecleaner.domain.usecase

import com.pion.phonecleaner.domain.model.network.SpeedTestProgress
import com.pion.phonecleaner.domain.repository.SpeedTestRepository
import kotlinx.coroutines.flow.Flow

/**
 * Starts one connection measurement (`docs/screens/19-network-and-speed-test.md` §2.2).
 *
 * Not `suspend`: it returns the cold flow the caller collects, so the measurement starts when it is
 * collected and stops when that collection is cancelled.
 */
class RunSpeedTestUseCase(
    private val speedTest: SpeedTestRepository,
) {
    operator fun invoke(): Flow<SpeedTestProgress> = speedTest.measure()
}
