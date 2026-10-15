package com.example.offlinetranslator.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.example.offlinetranslator.data.ThemeMode

@Composable
fun OfflineTranslatorTheme(
    themeMode: ThemeMode = ThemeMode.FOLLOW_SYSTEM,
    content: @Composable () -> Unit,
) {
    val useDark = when (themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.FOLLOW_SYSTEM -> isSystemInDarkTheme()
    }

    val colors = if (useDark) {
        darkColorScheme(
            primary = PrimaryDark,
            onPrimary = Color(0xFF00315D),
            primaryContainer = PrimaryContainerDark,
            onPrimaryContainer = Color(0xFFD3E3FD),
            secondary = Color(0xFFBBC7DB),
            secondaryContainer = Color(0xFF3B4858),
            background = BackgroundDark,
            onBackground = OnBackgroundDark,
            surface = SurfaceDark,
            onSurface = OnSurfaceDark,
            surfaceVariant = Color(0xFF43474E),
            onSurfaceVariant = OnSurfaceVariantDark,
            outline = Color(0xFF8D9199),
            error = ErrorDark,
            onError = OnErrorDark,
        )
    } else {
        lightColorScheme(
            primary = Primary,
            onPrimary = OnPrimary,
            primaryContainer = PrimaryContainer,
            onPrimaryContainer = Color(0xFF001C3A),
            secondary = Secondary,
            secondaryContainer = SecondaryContainer,
            onSecondaryContainer = Color(0xFF101C2B),
            background = Background,
            onBackground = OnBackground,
            surface = Surface,
            onSurface = OnSurface,
            surfaceVariant = SurfaceVariant,
            onSurfaceVariant = OnSurfaceVariant,
            outline = Outline,
            error = Error,
            onError = OnPrimary,
            tertiary = Tertiary,
            onTertiary = OnPrimary,
        )
    }

    MaterialTheme(
        colorScheme = colors,
        typography = AppTypography,
        content = content,
    )
}
