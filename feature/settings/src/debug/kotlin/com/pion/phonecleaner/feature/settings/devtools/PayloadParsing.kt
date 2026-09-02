package com.pion.phonecleaner.feature.settings.devtools

import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.toImmutableMap

/**
 * Parses the bench's text field into the `data` map of a push message.
 *
 * **This is not a wire contract.** Firebase's `RemoteMessage.getData()` is a `Map<String, String>`,
 * and that is the whole of what this produces; how a real sender would populate it is undefined
 * because there is no backend and none is in scope (owner decision 1). Nothing here interprets a key.
 *
 * One `key=value` per line, first `=` splits, blank lines and blank keys dropped. The competitor's
 * bench instead matches on a **raw notify id typed into a text field**, and negative ids exist
 * (`docs/screens/20-settings-language-and-push.md` §6.4 delta 3); a map of strings cannot be typo'd
 * into a different feature.
 */
internal fun parsePayload(input: String): ImmutableMap<String, String> = input
    .lineSequence()
    .mapNotNull { line ->
        val separator = line.indexOf('=')
        if (separator <= 0) return@mapNotNull null
        val key = line.substring(0, separator).trim()
        if (key.isEmpty()) null else key to line.substring(separator + 1).trim()
    }
    .toMap()
    .toImmutableMap()
