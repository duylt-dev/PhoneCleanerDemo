package com.pion.phonecleaner.core.mvi

/**
 * The phase every one of the nine file tools moves through. Shared so nine screens cannot each
 * invent a slightly different set — the competitor's equivalents disagree with one another.
 */
enum class ToolPhase {
    Idle,
    Scanning,

    /** The scan is done but [com.pion.phonecleaner.core.common.policy.MinimumDuration] has not elapsed. */
    Completing,
    Ready,
    Deleting,
}
