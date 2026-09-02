package com.pion.phonecleaner.domain.model.junk

/**
 * What a junk item *is*, one level below its category.
 *
 * The competitor's `xc.p` taxonomy, all 29 values in source order
 * (`docs/reverse-engineering/12-junk-cleaning.md` §7.2). There it reaches no pixel: 336 hand-written
 * sub-rules carry a value each, `xc.b` exposes no getter for them, and `getFullName()` has no caller
 * in the APK. Here it is the second level of the review tree — the difference between one 1.2 GB row
 * labelled `WhatsApp` and a browsable per-content-type list (`docs/screens/12-junk-cleaning.md`
 * §4.4 Delta R5).
 *
 * **UNKNOWN — the 29 display strings.** Looked for, and not found: a string table in
 * `docs/screens/12-junk-cleaning.md` (§8.4 item 5 states outright that they have no source in our
 * app, because the competitor never called `getFullName()` and so never localised them), and any
 * `strings.xml` entry under a `values` directory in this repository. No display name is invented
 * here; a screen that needs one adds a real string resource, keyed off the constant, in the change
 * that needs it.
 */
enum class JunkContentType {
    Unknown,
    Backup,
    SentVideo,
    ReceivedAudio,
    ExportedData,
    DownloadedData,
    OfflineData,
    OfflineMaps,
    OfflineMedia,
    SentAudio,
    VoiceNotes,
    Thumbnails,
    ReceivedDocs,
    SentDocs,
    Advertisement,
    Dictionary,
    Audio,
    Documents,
    ReceivedImages,
    SentImages,
    OfflineGameData,
    OfflineBooks,
    History,
    Wallpapers,
    AnimatedGifs,
    Stickers,
    Images,
    Video,
    Cache,
}
