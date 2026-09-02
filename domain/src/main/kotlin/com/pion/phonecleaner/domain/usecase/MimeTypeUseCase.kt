package com.pion.phonecleaner.domain.usecase

import com.pion.phonecleaner.domain.model.file.ScannedFile

/**
 * The MIME type to hand an external viewer
 * (`docs/screens/14-file-tools-and-app-manager.md` §2.4).
 *
 * **The column first, the extension second.** The competitor guesses from the extension although its
 * own query already selected `mime_type`; `ScannedFile.mimeType` carries that column, and it is null
 * only for a row that came from a walk rather than a media collection.
 *
 * No dependencies, so it is a `factory` like every other use case and holds nothing.
 */
class MimeTypeUseCase {

    operator fun invoke(file: ScannedFile): String? =
        file.mimeType ?: byExtension(file.name.substringAfterLast('.', "").lowercase())

    /**
     * UNKNOWN — no source states a fallback table. `android.webkit.MimeTypeMap` is the platform's
     * answer and cannot be reached from `:domain`, so this covers the extensions the six file tools
     * actually produce and returns null — *"let the system decide"* — for everything else. A wrong
     * MIME type opens the wrong app; a null one opens a chooser.
     */
    private fun byExtension(extension: String): String? = when (extension) {
        "jpg", "jpeg" -> "image/jpeg"
        "png" -> "image/png"
        "gif" -> "image/gif"
        "webp" -> "image/webp"
        "mp4" -> "video/mp4"
        "3gp" -> "video/3gpp"
        "mkv" -> "video/x-matroska"
        "mp3" -> "audio/mpeg"
        "m4a" -> "audio/mp4"
        "ogg", "opus" -> "audio/ogg"
        "wav" -> "audio/x-wav"
        "pdf" -> "application/pdf"
        "apk" -> "application/vnd.android.package-archive"
        "zip" -> "application/zip"
        "txt" -> "text/plain"
        else -> null
    }
}
