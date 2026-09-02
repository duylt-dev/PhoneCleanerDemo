package com.pion.phonecleaner.domain.model.launch

/**
 * How the process came to be in the foreground.
 *
 * `Intent.launchSource()` in `:app/navigation/` is the only mapping that reads an Intent
 * (LLM.md §3.9); everything downstream sees this enum instead.
 */
enum class LaunchSource {
    Launcher,
    Notification,
    ResidentWidget,
    AppResume,
}
