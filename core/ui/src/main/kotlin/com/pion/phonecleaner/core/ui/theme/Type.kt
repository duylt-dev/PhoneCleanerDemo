package com.pion.phonecleaner.core.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * All fifteen scales, defined once.
 *
 * MVI §11 forbids `.copy(fontSize = …)` and a call-site `fontWeight` — weight belongs to a named
 * style. That is also where the competitor's `Sparerabl` custom `TextView` goes: it took a 1–1000
 * `textFontWeight` per call site and degraded to `BOLD`/`NORMAL` below API 28
 * (`docs/screens/21-shared-models-and-ui.md` §4.4). `minSdk` is 28, so the degradation branch has no
 * port, and the variable weight becomes one of the weights below.
 *
 * Sizes are `sp`, never `dp` — the competitor's generic empty state sizes its text in `dp` and so
 * ignores the user's font-scale setting on the one screen a low-vision user is most likely reading
 * (§4.6 C2).
 */
private val Sans = FontFamily.Default

internal val AppTypography = Typography(
    displayLarge = TextStyle(fontFamily = Sans, fontSize = 57.sp, lineHeight = 64.sp, fontWeight = FontWeight.Normal, letterSpacing = (-0.25).sp),
    displayMedium = TextStyle(fontFamily = Sans, fontSize = 45.sp, lineHeight = 52.sp, fontWeight = FontWeight.Normal),
    displaySmall = TextStyle(fontFamily = Sans, fontSize = 36.sp, lineHeight = 44.sp, fontWeight = FontWeight.Normal),
    headlineLarge = TextStyle(fontFamily = Sans, fontSize = 32.sp, lineHeight = 40.sp, fontWeight = FontWeight.SemiBold),
    headlineMedium = TextStyle(fontFamily = Sans, fontSize = 28.sp, lineHeight = 36.sp, fontWeight = FontWeight.SemiBold),
    headlineSmall = TextStyle(fontFamily = Sans, fontSize = 24.sp, lineHeight = 32.sp, fontWeight = FontWeight.SemiBold),
    titleLarge = TextStyle(fontFamily = Sans, fontSize = 20.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontFamily = Sans, fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.15.sp),
    titleSmall = TextStyle(fontFamily = Sans, fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.1.sp),
    bodyLarge = TextStyle(fontFamily = Sans, fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight.Normal, letterSpacing = 0.5.sp),
    bodyMedium = TextStyle(fontFamily = Sans, fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Normal, letterSpacing = 0.25.sp),
    bodySmall = TextStyle(fontFamily = Sans, fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Normal, letterSpacing = 0.4.sp),
    labelLarge = TextStyle(fontFamily = Sans, fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.1.sp),
    labelMedium = TextStyle(fontFamily = Sans, fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.5.sp),
    labelSmall = TextStyle(fontFamily = Sans, fontSize = 11.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.5.sp),
)
