package com.vernova.santhali.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkColors = darkColorScheme(
    primary          = Teal500,
    onPrimary        = OnSurface,
    primaryContainer = Teal300,
    secondary        = Indigo500,
    onSecondary      = OnSurface,
    background       = SurfaceDark,
    surface          = SurfaceCard,
    onBackground     = OnSurface,
    onSurface        = OnSurface,
    outline          = SurfaceBorder,
    error            = Red500,
)

@Composable
fun SanthaliTranslatorTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColors,
        typography  = AppTypography,
        content     = content
    )
}
