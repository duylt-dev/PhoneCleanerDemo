package com.pion.phonecleaner.core.ui.catalog

import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.vector.ImageVector
import com.pion.phonecleaner.domain.catalog.FeatureCatalog
import com.pion.phonecleaner.domain.model.feature.FeatureId
import com.pion.phonecleaner.domain.model.permission.AppPermission
import kotlinx.collections.immutable.ImmutableSet

/**
 * Everything the UI needs to draw one feature. **This is the one place the "no Android in `:domain`"
 * rule bends, and it bends by moving the type rather than by weakening the rule** (LLM.md §2):
 * `FeatureId` and the permission table stay in `:domain`; the resource ids live here, because a
 * rendering concern is where a rendering concern is allowed to know about `R`.
 *
 * The reason is the competitor's worst catalogue behaviour, not tidiness. `ae.i2` resolves feature
 * names and descriptions to `String`s from `Resources` **at object initialisation**
 * (`java/ae/i2.java:315-415`), and this app ships a 17-locale in-app language picker — so switching
 * language leaves every feature name stale until the process restarts. Ids resolved by
 * `stringResource` at render is the fix (`docs/screens/21` §5.2, §5.4 G3).
 *
 * ### Two deliberate deviations from `docs/screens/21` §5.2's declaration
 *
 * **1. The three icon fields are `ImageVector`, not `@DrawableRes Int`.** This repository has none of
 * the competitor's icon assets, and authoring twenty vector drawables would be inventing content, not
 * porting it. They come from `androidx.compose.material.icons` — already a dependency of this module —
 * and [resultIcon] and [exitIcon] default to [icon] because no source states a different glyph for the
 * result tile or the exit dialog. When real art arrives, these three fields become `@DrawableRes Int`
 * and `FeatureDescriptors` is the only file that changes.
 *
 * **2. [requires] is not a stored field; it delegates to [FeatureCatalog].** §5.2 writes it as a
 * constructor property, but the permission table is `:domain`'s — `FeatureCatalog.requires` is what
 * the reducer gate reads (system-architecture §4.8), and a second copy here would be two tables
 * maintained by hand, which is the exact defect that produced the competitor's drifted `tag 8` /
 * `tag 14` arms. `FeatureCatalog` also carries the reasons most rows are deliberately blank — the
 * storage half belongs to `FileAccessProfile` (§8.4) and `RunningApps` is pending owner decision 3.
 */
@Immutable
data class FeatureDescriptor(
    val feature: FeatureId,
    @param:StringRes @get:StringRes val titleRes: Int,
    @param:StringRes @get:StringRes val descriptionRes: Int,
    val icon: ImageVector,
    /** Replaces the competitor's four-way `if` over the exit dialog's call to action. */
    @param:StringRes @get:StringRes val exitCtaRes: Int,
    /** The promo tile on the clean-result screen. */
    val resultIcon: ImageVector = icon,
    /** The exit dialog's glyph. */
    val exitIcon: ImageVector = icon,
) {
    /** Reads `:domain`'s table. See the class KDoc, deviation 2. */
    val requires: ImmutableSet<AppPermission> get() = FeatureCatalog.requires(feature)
}
