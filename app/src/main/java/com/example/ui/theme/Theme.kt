package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme =
    darkColorScheme(
        primary = CyberCyan,
        onPrimary = Color(0xFF00363D),
        primaryContainer = Color(0xFF004F58),
        onPrimaryContainer = Color(0xFF8CF4FF),
        secondary = CyberEmerald,
        onSecondary = Color(0xFF00391A),
        secondaryContainer = Color(0xFF005328),
        onSecondaryContainer = Color(0xFF67FE9A),
        tertiary = CyberBlue,
        onTertiary = Color.White,
        background = DarkBg,
        onBackground = Color(0xFFE2E8F0),
        surface = DarkSurface,
        onSurface = Color(0xFFF1F5F9),
        surfaceVariant = DarkSurfaceVariant,
        onSurfaceVariant = Color(0xFFCBD5E1),
        outline = DarkOutline,
        error = CyberRed,
        onError = Color.White
    )

private val LightColorScheme =
    lightColorScheme(
        primary = CyberCyanDark,
        onPrimary = Color.White,
        primaryContainer = Color(0xFFE0F7FA),
        onPrimaryContainer = Color(0xFF004D40),
        secondary = CyberEmeraldDark,
        onSecondary = Color.White,
        secondaryContainer = Color(0xFFE8F5E9),
        onSecondaryContainer = Color(0xFF1B5E20),
        tertiary = CyberBlue,
        onTertiary = Color.White,
        background = LightBg,
        onBackground = Color(0xFF0F172A),
        surface = LightSurface,
        onSurface = Color(0xFF0F172A),
        surfaceVariant = LightSurfaceVariant,
        onSurfaceVariant = Color(0xFF475569),
        outline = LightOutline,
        error = Color(0xFFBA1A1A),
        onError = Color.White
    )

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true, // Default to high-tech dark theme for security sandbox
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
