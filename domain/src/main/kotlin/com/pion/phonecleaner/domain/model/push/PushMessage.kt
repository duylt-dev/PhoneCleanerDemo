package com.pion.phonecleaner.domain.model.push

import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentMapOf

/**
 * One received push message, as the platform hands it over.
 *
 * Every field is a direct reading of Firebase's `RemoteMessage` — `messageId`, `sentTime`, `data` —
 * so nothing here is a wire contract this project invented. **There is no backend and none is in
 * scope** (owner decision 1, `LLM.md` §1), so nothing sends to it and what a given key *means* is
 * undefined; this type carries the payload without interpreting it.
 *
 * The competitor's payload is interpreted, and its interpretation is the defect worth recording:
 * `action_id` means *the module to open* — except when `param_from == 1`, when it means the tapped
 * **view** id (`docs/reverse-engineering/20-settings-language-and-push.md:689`). One field with two
 * meanings selected by another field is not ported, and cannot be ported before a payload contract
 * exists.
 *
 * `FeatureId.fromLegacyIndex` is the *only* legitimate reader of a numeric id out of a payload, and
 * it is never used for navigation (`LLM.md` §7.2). Nothing here calls it.
 */
data class PushMessage(
    /** Firebase's `RemoteMessage.getMessageId()`; null when the sender set none. */
    val messageId: String?,
    /** Firebase's `RemoteMessage.getSentTime()`, epoch millis. `0` when unset. */
    val sentAtMillis: Long,
    /** Firebase's `RemoteMessage.getData()`, verbatim and uninterpreted. */
    val data: ImmutableMap<String, String> = persistentMapOf(),
)
