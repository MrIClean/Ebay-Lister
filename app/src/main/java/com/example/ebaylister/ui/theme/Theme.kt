package com.example.ebaylister.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

@Composable
fun EbayListerTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = NeonCyan,
            onPrimary = NeonBlack,
            secondary = NeonMagenta,
            onSecondary = NeonInk,
            tertiary = NeonLime,
            onTertiary = NeonInk,
            background = NeonBlack,
            onBackground = NeonInk,
            surface = NeonPanel,
            onSurface = NeonInk,
            surfaceVariant = NeonPanelAlt,
            onSurfaceVariant = NeonMuted,
            outline = NeonBorder,
            outlineVariant = NeonBorder,
            inverseSurface = NeonInk,
            inverseOnSurface = NeonBlack,
        ),
        typography = AppTypography,
        content = content,
    )
}
