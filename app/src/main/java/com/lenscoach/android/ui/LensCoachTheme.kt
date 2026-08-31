package com.lenscoach.android.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val Scheme = darkColorScheme(
    primary = Viewfinder.Accent,
    onPrimary = Viewfinder.OnAccent,
    background = Viewfinder.Chrome,
    surface = Viewfinder.Surface,
    onBackground = Viewfinder.Text,
    onSurface = Viewfinder.Text,
)

@Composable
fun LensCoachTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Scheme, content = content)
}
