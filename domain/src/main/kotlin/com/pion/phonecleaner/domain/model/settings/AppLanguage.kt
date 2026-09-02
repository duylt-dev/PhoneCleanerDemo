package com.pion.phonecleaner.domain.model.settings

/**
 * One entry of the in-app language picker.
 *
 * [tag] replaces the competitor's `(lnguage, country)` pair **and** its duplicated `index`, of which
 * two rows share the value 15. It closes three defects at once: that duplicate, the deprecated
 * Indonesian code `in`, and the inability to express `zh-Hans` / `zh-Hant` except through `CN` / `TW`
 * (`docs/screens/20-settings-language-and-push.md:57-75`).
 *
 * `isChoosed` does not survive: which language is selected is a property of the screen, not of a
 * process-global list element (`LLM.md` §8).
 *
 * DELIBERATE OMISSION — `docs/screens/20-settings-language-and-push.md:64` also puts
 * `@StringRes val displayNameRes: Int` on this model. That annotation is `androidx.annotation`, i.e.
 * Android, and `:domain` is compiled without it (`LLM.md` §2). The rule bends **by moving the type,
 * never by weakening the rule**: exactly as `FeatureCatalog` keeps `FeatureId` while
 * `FeatureDescriptor` in `:core:ui/catalog` holds the resource ids (`docs/system-architecture.md`
 * §4.3), the endonym mapping `AppLanguage -> @StringRes` belongs beside it in `:core:ui`. The
 * endonym must stay a string resource so it is never itself translated.
 */
data class AppLanguage(
    /** BCP-47 tag — the identity. "en-US", "zh-Hans-CN", "pt-PT", … */
    val tag: String,
)
