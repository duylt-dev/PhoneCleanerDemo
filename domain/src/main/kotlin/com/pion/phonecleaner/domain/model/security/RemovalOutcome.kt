package com.pion.phonecleaner.domain.model.security

/**
 * What asking to remove a finding actually did.
 *
 * The split exists because the two kinds of finding are removed by two different actors
 * (`docs/screens/15-antivirus.md` §2.2):
 *
 * - an **installed app** can only be removed by the system uninstall dialog, so the use case
 *   *validates* and the screen raises the Effect that launches it; nothing is gone yet, and the row
 *   stays greyed until a `PACKAGE_REMOVED` broadcast or the user's return resolves it;
 * - a **loose `.apk`** is deleted by us, through the one `FileDeleter` in the app.
 *
 * The competitor collapses both into one field and fires the system uninstall with `startActivity`
 * rather than `startActivityForResult`, so it never learns the outcome of either
 * (`docs/reverse-engineering/15-antivirus.md` §3.4).
 */
sealed interface RemovalOutcome {

    /** Hand [packageName] to the system uninstall dialog. Nothing has been removed yet. */
    data class UninstallRequired(val packageName: String) : RemovalOutcome

    /** The file is gone and the row has been forgotten. [freedBytes] is measured, never parsed. */
    data class Removed(val freedBytes: Long) : RemovalOutcome
}
