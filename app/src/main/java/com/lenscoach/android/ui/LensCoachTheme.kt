package com.lenscoach.android.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Scheme = darkColorScheme(
    primary = Color(0xFFF4E6C3),
    onPrimary = Color(0xFF1A1408),
    background = Color.Black,
    surface = Color(0xFF111318),
    onBackground = Color(0xFFF4F1EA),
    onSurface = Color(0xFFF4F1EA),
)

@Composable
fun LensCoachTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Scheme, content = content)
}
