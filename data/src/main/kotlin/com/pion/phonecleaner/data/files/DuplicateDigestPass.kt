package com.pion.phonecleaner.data.files

import com.pion.phonecleaner.core.common.result.AppResult
import com.pion.phonecleaner.domain.model.file.ScannedFile
import com.pion.phonecleaner.domain.repository.FileDigest
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlin.time.TimeMark

/**
 * One digest pass over a candidate list, bounded by a deadline and by parallelism — the half of
 * `docs/screens/14-file-tools-and-app-manager.md` §2.2 that `Md5DuplicateFinder` runs twice.
 *
 * It **computes digests and groups nothing**: which bucket a hex belongs in differs between the two
 * passes (the head pass must key on size as well, because a truncated copy shares its original's
 * first 64 KiB), and a helper that decided that would have to be told the answer anyway.
 *
 * `coroutineScope` is what makes the per-file digests **structural children** of the collecting job
 * (`LLM.md` §12): cancelling the scan cancels every read in flight, and one read that throws
 * cancels its siblings rather than leaving them running behind a discarded result.
 */
internal data class DigestPass(
    /** Every file whose digest succeeded, paired with its hex. Order follows [digestPass]'s input. */
    val digests: List<Pair<ScannedFile, String>>,
    /** Files processed, successes and failures alike. Carried so a second pass can keep counting. */
    val hashed: Int,
    /** The deadline expired before the list ran out. What completed is still returned. */
    val truncated: Boolean,
)

/**
 * [parallelism] files at a time. A wider fan-out does not go faster: this is one storage device, and
 * the competitor's single-threaded loop and a 200-way fan-out are the two ends the four in
 * §2.2 sits between.
 *
 * On expiry it **returns what completed** and says so. The competitor's `withTimeoutOrNull(4000)`
 * returns success with a half-built map, so a device that needed five seconds is told it has no
 * duplicates.
 */
internal suspend fun digestPass(
    digest: FileDigest,
    candidates: List<ScannedFile>,
    maxBytes: Long,
    parallelism: Int,
    deadline: TimeMark,
    hashedBefore: Int = 0,
    onProgress: suspend (hashed: Int) -> Unit = {},
): DigestPass {
    val digests = ArrayList<Pair<ScannedFile, String>>(candidates.size)
    var hashed = hashedBefore
    for (chunk in candidates.chunked(parallelism)) {
        if (deadline.hasPassedNow()) return DigestPass(digests, hashed, truncated = true)
        val hexes = coroutineScope {
            chunk.map { file -> async { (digest.digest(file, maxBytes) as? AppResult.Success)?.value } }
                .awaitAll()
        }
        for ((index, hex) in hexes.withIndex()) {
            hashed++
            if (hex != null) digests += chunk[index] to hex
        }
        onProgress(hashed)
    }
    return DigestPass(digests, hashed, truncated = false)
}
