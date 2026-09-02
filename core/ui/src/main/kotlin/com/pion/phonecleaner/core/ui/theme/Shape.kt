package com.pion.phonecleaner.core.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * The corner scale. MVI §11: a corner is `MaterialTheme.shapes.X`, [Pill] or `CircleShape` —
 * never a call-site `RoundedCornerShape(20.dp)`.
 */
internal val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

/**
 * A fully-rounded capsule. Not a `Shapes` slot because it is a *shape rule* rather than a size:
 * the radius follows the height, so one value serves a 20 dp badge and a 56 dp button.
 */
val Pill: RoundedCornerShape = RoundedCornerShape(percent = 50)
