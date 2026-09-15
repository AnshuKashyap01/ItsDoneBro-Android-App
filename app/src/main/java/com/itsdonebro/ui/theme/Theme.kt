package com.itsdonebro.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary          = BrandRed,
    onPrimary        = Color.White,
    primaryContainer = Color(0xFF3A0F0D),
    onPrimaryContainer = Color(0xFFFFB4AE),

    secondary        = BrandOrange,
    onSecondary      = Color.White,

    tertiary         = BrandGreen,
    onTertiary       = Color.Black,

    background       = Background,
    onBackground     = OnBackground,

    surface          = Surface,
    onSurface        = OnSurface,
    surfaceVariant   = SurfaceVariant,
    onSurfaceVariant = OnSurfaceDim,

    outline          = Divider,
    outlineVariant   = OnSurfaceFaint,

    error            = BrandRed,
    onError          = Color.White
)

@Composable
fun ItsDoneBroTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography  = ItsDoneBroTypography,
        content     = content
    )
}
