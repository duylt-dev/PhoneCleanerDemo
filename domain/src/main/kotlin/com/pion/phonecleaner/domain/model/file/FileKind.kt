package com.pion.phonecleaner.domain.model.file

/**
 * What a scanned file is, for the row icon and for the media-collection it came from.
 *
 * The competitor carries an `int filetype` whose meaning **differs between two screens** that both
 * populate the same row bean: big files uses `0 = walk, 1 = image, 2 = video, 3 = audio`
 * (`docs/reverse-engineering/14-file-tools-and-app-manager.md:255`) while the same file's row icon
 * switches on `1 = apk, 2 = generic, 4 = image`
 * (`docs/reverse-engineering/14-file-tools-and-app-manager.md:213`). Two disagreeing meanings for one
 * field is exactly the class of drift an enum removes.
 *
 * UNKNOWN — no section of `docs/system-architecture.md` §4 and no appendix in `docs/screens/`
 * adjudicates the constant set; only the type NAME is fixed (`LLM.md` §3.3). These five are the
 * distinctions the corpus actually makes at a call site: the three `MediaStoreRepository`
 * collections, the `.apk` visitor `xc.y` (`deobfuscation-reference.md:515`, and the `.apk` arm of the
 * delete strategy table, `docs/screens/12-junk-cleaning.md:724`), and everything else. A finer split
 * — Document versus Archive — is asserted by no source and is not invented here.
 */
enum class FileKind {
    Image,
    Video,
    Audio,
    Apk,
    Other,
}
