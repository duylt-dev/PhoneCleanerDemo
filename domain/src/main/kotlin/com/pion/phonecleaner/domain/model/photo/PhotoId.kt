package com.pion.phonecleaner.domain.model.photo

/**
 * The `MediaStore` `_id` of one image row, **captured at scan time**
 * (`docs/screens/13-photo-and-media.md` §0.2).
 *
 * A value class rather than a bare `Long` because every screen in this cluster carries a selection of
 * these and a `Set<Long>` beside a `List<Long>` of anything else is a mistake the compiler cannot see.
 *
 * It is the identity of a row and therefore the `key` of every `LazyVerticalGrid` lane in this module
 * (`LLM.md` §8). The competitor keys nothing: `java/ud/c.java:89,136` stashes the adapter *position*
 * in the row's `View` tag and casts it back.
 */
@JvmInline
value class PhotoId(val value: Long)
