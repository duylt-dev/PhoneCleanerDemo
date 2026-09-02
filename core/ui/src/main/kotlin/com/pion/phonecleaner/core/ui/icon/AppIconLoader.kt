package com.pion.phonecleaner.core.ui.icon

import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import coil3.ImageLoader

/**
 * How an app icon reaches a row: **by package name, resolved at draw time.**
 *
 * Five competitor models hold a `Drawable`, and that one choice causes two separate defects — the one
 * `Parcelable` among them silently drops the icon in `writeToParcel` (`Selerover:110`), and two
 * identical rows compare unequal because the image loader handed back a different `Drawable` instance,
 * so no diff can see that nothing changed (LLM.md §2, `docs/screens/21` §1.2). **No model in this port
 * holds an icon.** Every one of the five already carries `packageName`, which is the icon's identity.
 *
 * It also replaces `cd.g`: two `LruCache`s (50 and 500 entries) and a private 2-thread pool. Coil owns
 * the memory cache, the request cancellation on recompose, and the placeholder slot.
 *
 * > **`koinInject()` this into the composable, never into a ViewModel** (system-architecture §4.7).
 * > A ViewModel holding an image loader is a ViewModel a screenshot test has to stub, and it puts an
 * > Android type behind the one boundary MVI §1 keeps clear.
 *
 * Declared exactly once, in `coreUiModule` (LLM.md §6.5). Four cluster reports each declared it, under
 * three different names; a second `single` of one type is a silent override by load order, not a
 * compile error (`docs/screens/21` §6.2).
 *
 * Usage:
 * ```
 * val icons = koinInject<AppIconLoader>()
 * AsyncImage(
 *     model = icons.request(app.packageName),
 *     imageLoader = icons.imageLoader,
 *     contentDescription = null,
 * )
 * ```
 *
 * This is a class rather than an interface plus a `CoilAppIconLoader` — the shape
 * `docs/screens/21` §6.2's snippet writes — for the reason LLM.md §5 gives for `<Name>Impl`: there is
 * exactly one plausible mechanism, because §4.7 mandates Coil by name. Two public types would also
 * need two files, and §3.5's tree for `icon/` lists two files that are both already spoken for.
 */
@Stable // one instance for the process, one `val` set at construction: a composable taking it can skip
class AppIconLoader(context: Context) {

    /**
     * A request key. It is a type rather than a bare `String` so Coil routes it to
     * [PackageIconFetcher] instead of trying to read it as a URI.
     */
    @Immutable
    data class Request(val packageName: String)

    val imageLoader: ImageLoader = ImageLoader.Builder(context)
        .components {
            add(PackageIconFetcher.Factory(context), Request::class)
            add(PackageIconFetcher.CacheKeyer, Request::class)
        }
        .build()

    fun request(packageName: String): Request = Request(packageName)
}
