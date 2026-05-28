package com.example.ebaylister.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

@Composable
fun EbayListerTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Ink,
            onPrimary = Color.White,
            secondary = Teal,
            onSecondary = Color.White,
            tertiary = WarmAccent,
            onTertiary = Color.White,
            background = Paper,
            onBackground = Ink,
            surface = Color.White,
            onSurface = Ink,
            surfaceVariant = Color(0xFFF1F5F9),
            onSurfaceVariant = Slate,
            outline = SoftBorder,
        ),
        typography = AppTypography,
        content = content,
    )
}
