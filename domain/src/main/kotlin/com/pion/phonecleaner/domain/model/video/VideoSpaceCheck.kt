package com.pion.phonecleaner.domain.model.video

/**
 * Whether the volume has room for a run (`phase-03-domain-video-compression.md` step 5).
 *
 * A refusal that says *how much* short, so the screen can state it. "Not enough space" with no
 * number is the kind of message that makes a user delete things at random.
 */
sealed interface VideoSpaceCheck {
    data object Sufficient : VideoSpaceCheck
    data class Short(val byBytes: Long) : VideoSpaceCheck
}
