package com.pion.phonecleaner.feature.files.whatsapp.component

import androidx.annotation.StringRes
import com.pion.phonecleaner.domain.model.file.WhatsAppBucketId
import com.pion.phonecleaner.feature.files.R

/**
 * The six group names, in one `when` over the enum. The competitor labels them from a parallel array
 * indexed by position, so a reordered array silently mislabels every tile.
 */
@StringRes
internal fun WhatsAppBucketId.labelRes(): Int = when (this) {
    WhatsAppBucketId.Junk -> R.string.whatsapp_bucket_junk
    WhatsAppBucketId.Video -> R.string.whatsapp_bucket_video
    WhatsAppBucketId.Image -> R.string.whatsapp_bucket_image
    WhatsAppBucketId.Voice -> R.string.whatsapp_bucket_voice
    WhatsAppBucketId.Audio -> R.string.whatsapp_bucket_audio
    WhatsAppBucketId.Document -> R.string.whatsapp_bucket_document
}
