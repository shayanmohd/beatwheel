package com.mohdshayan.beatwheel.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/*
 * The whole radius scale, and the rule for using it:
 *   RadiusSm  4dp   chips and fields
 *   RadiusMd  10dp  buttons and the drone strip
 *   RadiusLg  20dp  sheet tops and dialogs
 * Lists are unboxed. Nothing in the app uses a corner radius that is not one of these.
 */
val RadiusSm = 4.dp
val RadiusMd = 10.dp
val RadiusLg = 20.dp

val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(RadiusSm),
    small = RoundedCornerShape(RadiusSm),
    medium = RoundedCornerShape(RadiusMd),
    large = RoundedCornerShape(RadiusLg),
    extraLarge = RoundedCornerShape(RadiusLg),
)

val ButtonShape = RoundedCornerShape(RadiusMd)
val SheetShape = RoundedCornerShape(topStart = RadiusLg, topEnd = RadiusLg)
