package com.pion.phonecleaner.core.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * The palette, written once. `internal` on purpose: a screen reads colour through
 * `MaterialTheme.colorScheme`, never by naming a swatch — MVI §11's rule for text styles applies to
 * colour for the same reason. The competitor's design system is `<include>` plus `findViewById`, with
 * no styles and no themed attributes at all (system-architecture §4.7), so every screen picked its own.
 *
 * These are Material 3 roles, not brand names, so a re-skin is one file.
 */

// ── light ────────────────────────────────────────────────────────────────────────────────────────
internal val LightPrimary = Color(0xFF2A5BD7)
internal val LightOnPrimary = Color(0xFFFFFFFF)
internal val LightPrimaryContainer = Color(0xFFDBE1FF)
internal val LightOnPrimaryContainer = Color(0xFF001551)

internal val LightSecondary = Color(0xFF00696E)
internal val LightOnSecondary = Color(0xFFFFFFFF)
internal val LightSecondaryContainer = Color(0xFF9CF1F6)
internal val LightOnSecondaryContainer = Color(0xFF002022)

internal val LightTertiary = Color(0xFF6B4FA1)
internal val LightOnTertiary = Color(0xFFFFFFFF)
internal val LightTertiaryContainer = Color(0xFFEADDFF)
internal val LightOnTertiaryContainer = Color(0xFF250B58)

internal val LightError = Color(0xFFBA1A1A)
internal val LightOnError = Color(0xFFFFFFFF)
internal val LightErrorContainer = Color(0xFFFFDAD6)
internal val LightOnErrorContainer = Color(0xFF410002)

internal val LightBackground = Color(0xFFF7F8FC)
internal val LightOnBackground = Color(0xFF1A1B21)
internal val LightSurface = Color(0xFFFFFFFF)
internal val LightOnSurface = Color(0xFF1A1B21)
internal val LightSurfaceVariant = Color(0xFFE2E1EC)
internal val LightOnSurfaceVariant = Color(0xFF45464F)
internal val LightOutline = Color(0xFF767680)
internal val LightOutlineVariant = Color(0xFFC6C6D0)

// ── dark ─────────────────────────────────────────────────────────────────────────────────────────
internal val DarkPrimary = Color(0xFFB4C4FF)
internal val DarkOnPrimary = Color(0xFF032978)
internal val DarkPrimaryContainer = Color(0xFF0C41A2)
internal val DarkOnPrimaryContainer = Color(0xFFDBE1FF)

internal val DarkSecondary = Color(0xFF80D4DA)
internal val DarkOnSecondary = Color(0xFF00363A)
internal val DarkSecondaryContainer = Color(0xFF004F53)
internal val DarkOnSecondaryContainer = Color(0xFF9CF1F6)

internal val DarkTertiary = Color(0xFFD3BBFF)
internal val DarkOnTertiary = Color(0xFF3B1E6F)
internal val DarkTertiaryContainer = Color(0xFF523687)
internal val DarkOnTertiaryContainer = Color(0xFFEADDFF)

internal val DarkError = Color(0xFFFFB4AB)
internal val DarkOnError = Color(0xFF690005)
internal val DarkErrorContainer = Color(0xFF93000A)
internal val DarkOnErrorContainer = Color(0xFFFFDAD6)

internal val DarkBackground = Color(0xFF121319)
internal val DarkOnBackground = Color(0xFFE3E1E9)
internal val DarkSurface = Color(0xFF1A1B21)
internal val DarkOnSurface = Color(0xFFE3E1E9)
internal val DarkSurfaceVariant = Color(0xFF45464F)
internal val DarkOnSurfaceVariant = Color(0xFFC6C6D0)
internal val DarkOutline = Color(0xFF90909A)
internal val DarkOutlineVariant = Color(0xFF45464F)
