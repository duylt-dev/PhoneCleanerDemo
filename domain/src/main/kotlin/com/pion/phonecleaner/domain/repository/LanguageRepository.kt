package com.pion.phonecleaner.domain.repository

import com.pion.phonecleaner.core.common.result.AppResult
import com.pion.phonecleaner.domain.model.settings.AppLanguage
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.flow.Flow

/**
 * The in-app language picker's one port
 * (`docs/screens/20-settings-language-and-push.md` §0, §7.2).
 *
 * It retires five competitor mechanisms at once: `od.i0` (a locale getter whose `Context` parameter
 * is never used), `od.p0.k()`/`.g()` (the same two preference keys read with a *different* fallback
 * policy), `od.d0` (the `commit()`-everything wrapper), `n7.e` (MultiLanguages, `language_setting.xml`)
 * and blankj `LanguageUtils` (`Utils.xml`). The competitor's locale therefore lives in four places
 * that agree only because one method writes them all in one breath (§2.4 delta 2). **One reader, one
 * fallback, here.**
 *
 * ### What this port deliberately does NOT do
 *
 * It does not apply a locale. Applying one is `AppCompatDelegate.setApplicationLocales`, a platform
 * call the Route makes when it collects `LanguageEffect.ApplyLocale` — the ViewModel may not name
 * `Locale`, `Configuration`, `Resources` or `AppCompatDelegate` (§2.2, MVI §8). And it never
 * restarts the process: the competitor calls `AppUtils.relaunchApp(killProcess = true)` twice per
 * save, which destroys the back stack, drops every in-flight coroutine and is not guaranteed to land
 * on Android 12+ (§2.4 delta 1).
 */
interface LanguageRepository {

    /**
     * The rows the picker offers, in render order. A pure constant list — **no IO**, which is why it
     * does not suspend and does not return [AppResult].
     *
     * The competitor's equivalent, `ae.t`, is a `Lazy` singleton holding 17 **mutable** row objects
     * whose `isChoosed` field the screen writes and `ae.t.J()` resets on every entry
     * (`ae/t.java:236-243`). Immutable here; the selection is a `String?` on the screen's state.
     */
    fun supportedLanguages(): ImmutableList<AppLanguage>

    /**
     * The applied language, or `null` for "follow the system".
     *
     * `null` is a first-class answer, not a failure: `n7.b.a(ctx)` — the competitor's own "clear the
     * override" call — exists at `n7/b.java:17-20` and nothing in the app invokes it, so its picker
     * is a one-way door (§2.4 delta 3).
     */
    fun currentLanguage(): Flow<AppLanguage?>

    /**
     * Persists [tag] (`null` clears the override). Suspends, and is awaited before the screen raises
     * `ApplyLocale`: the competitor's `od.d0.l` uses `commit()` — a synchronous fsync on the main
     * thread, twice — immediately before killing the process (§2.4 delta 7).
     */
    suspend fun setLanguage(tag: String?): AppResult<Unit>
}
