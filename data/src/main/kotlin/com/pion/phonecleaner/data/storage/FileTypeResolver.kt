package com.pion.phonecleaner.data.storage

import android.webkit.MimeTypeMap
import com.pion.phonecleaner.domain.model.file.FileKind
import java.util.Locale

/**
 * One answer to "what kind of file is this", for every scanner in the app.
 *
 * It is the `FileTypeResolver` slice of `od.p0`, which `docs/system-architecture.md` §5.10 splits
 * five ways. The competitor's bean carries an `int filetype` whose two writers disagree about what
 * the numbers mean (`docs/reverse-engineering/14-file-tools-and-app-manager.md:213` against `:255`) —
 * one enum with one resolver is the whole fix.
 *
 * The MIME type wins when there is one, because a MediaStore row always has one and it is
 * authoritative. The extension is the fallback for a plain file on disk.
 */
internal object FileTypeResolver {

    fun of(name: String, mimeType: String?): FileKind {
        val mime = mimeType ?: mimeTypeOf(name)
        return when {
            mime == null -> kindFromExtension(extensionOf(name))
            mime.startsWith("image/") -> FileKind.Image
            mime.startsWith("video/") -> FileKind.Video
            mime.startsWith("audio/") -> FileKind.Audio
            mime == APK_MIME -> FileKind.Apk
            else -> kindFromExtension(extensionOf(name))
        }
    }

    fun mimeTypeOf(name: String): String? =
        MimeTypeMap.getSingleton().getMimeTypeFromExtension(extensionOf(name))

    private fun kindFromExtension(extension: String): FileKind =
        if (extension == "apk") FileKind.Apk else FileKind.Other

    private fun extensionOf(name: String): String =
        name.substringAfterLast('.', "").lowercase(Locale.ROOT)

    private const val APK_MIME = "application/vnd.android.package-archive"
}
