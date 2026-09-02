package com.pion.phonecleaner.domain.model.photo

/**
 * What a sample of the selection actually re-encoded to
 * (`docs/screens/13-photo-and-media.md` §3.1, §3.2).
 *
 * PLACEMENT — §3.1 writes this data class inside `PhotoCompressorContract.kt`. It cannot live there:
 * `EstimateCompressionUseCase` returns it and use cases are `:domain` (`LLM.md` §4), while a contract
 * file holds State, Intent and Effect and nothing else (MVI §2). It is a `:domain` model, so it goes
 * where every other `:domain` model goes.
 *
 * It exists at all because the competitor's intro panel is the literal string
 * *"Before 807KB / After 484KB(-40%)"* plus *"up to about 40%"*, and its engine's own scale rule
 * produces neither figure (§3.5).
 */
data class CompressionEstimate(
    val beforeBytes: Long,
    val afterBytes: Long,
    /** How many photos were actually re-encoded to produce the two figures above. */
    val sampledCount: Int,
) {
    val savedBytes: Long get() = (beforeBytes - afterBytes).coerceAtLeast(0L)
}
