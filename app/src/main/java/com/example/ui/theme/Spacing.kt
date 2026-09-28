package com.example.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Immutable
data class Spacing(
    val spaceXs: Dp = 4.dp,
    val spaceSm: Dp = 8.dp,
    val spaceMd: Dp = 16.dp,
    val spaceLg: Dp = 24.dp,
    val spaceXl: Dp = 32.dp,
    val margin: Dp = 16.dp,
    val marginMd: Dp = 24.dp,
    val marginLg: Dp = 32.dp,
    val gutter: Dp = 16.dp,
    val gutterLg: Dp = 24.dp,
    val minTouchTarget: Dp = 48.dp
)

@Immutable
data class ElevationTokens(
    val default: Dp = 0.dp,
    val card: Dp = 1.dp,
    val cardHover: Dp = 2.dp,
    val stickyHeader: Dp = 3.dp,
    val floatingAction: Dp = 6.dp,
    val modalBottomSheet: Dp = 8.dp
)

val LocalSpacing = staticCompositionLocalOf { Spacing() }
val LocalElevations = staticCompositionLocalOf { ElevationTokens() }
