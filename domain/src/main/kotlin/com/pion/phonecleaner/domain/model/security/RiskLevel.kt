package com.pion.phonecleaner.domain.model.security

/**
 * How much attention one finding is asking for.
 *
 * **The competitor's whole risk model, in one place.** It scatters the same two cut-points over an
 * adapter and a filter — a `>= 6` list filter and a `< 8` badge switch — so a row can be filtered in
 * by one rule and drawn by the other (`docs/screens/15-antivirus.md` §0.1). Here the cut-points exist
 * exactly once, in [of], and `RiskBadge` is one composable over this enum.
 *
 * **The names are ours, the numbers are ported.** 6 and 8 are the competitor's, verified; the two
 * words it puts on them are not carried into our copy — no string this app renders makes a
 * malware-detection claim in our own voice (`LLM.md` §5, `docs/screens/15-antivirus.md`, wording
 * note). `Elevated` / `High` describe *a finding to review*, which is what we can honestly say.
 *
 * `docs/screens/15-antivirus.md` open item 5 records that these two identifiers are that appendix's
 * own; the cut-points are not.
 */
enum class RiskLevel {
    Clean,
    Elevated,
    High,
    ;

    companion object {
        /** Scores seen in practice are 0, 1, 6 and 8 (`docs/reverse-engineering/15-antivirus.md` §4.4). */
        fun of(score: Int): RiskLevel = when {
            score >= HIGH_SCORE -> High
            score >= ELEVATED_SCORE -> Elevated
            else -> Clean
        }

        /** The competitor's `>= 6` list filter — what counts as a finding at all. */
        const val ELEVATED_SCORE: Int = 6

        /** The competitor's `< 8` badge switch, read the other way round. */
        const val HIGH_SCORE: Int = 8
    }
}
