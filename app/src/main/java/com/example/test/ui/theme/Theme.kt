package com.example.test.ui.theme

import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = AppPrimary,
    onPrimary = AppOnPrimary,
    secondary = AppAccent,
    tertiary = AppSuccess,
    onSecondary = AppOnAccent,
    background = AppBackground,
    onBackground = AppOnSurface,
    surface = AppSurface,
    onSurface = AppOnSurface,
    surfaceVariant = AppSurfaceHighest,
    onSurfaceVariant = AppOnSurfaceVariant,
    surfaceContainerLowest = AppBackground,
    surfaceContainerLow = AppSurface,
    surfaceContainer = AppSurface,
    surfaceContainerHigh = AppSurfaceHigh,
    surfaceContainerHighest = AppSurfaceHighest,
    outline = AppOutline,
    outlineVariant = AppOutlineMuted,
    primaryContainer = Color(0xFF123F3B),
    onPrimaryContainer = Color(0xFFD8FFFA),
    secondaryContainer = Color(0xFF3A3016),
    onSecondaryContainer = Color(0xFFFFE6A1),
    errorContainer = Color(0xFF4C1719),
    onErrorContainer = Color(0xFFFFDAD8),
    error = AppError
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF176B63),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD4F3EE),
    onPrimaryContainer = Color(0xFF00201D),
    secondary = Color(0xFF765B00),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFE596),
    onSecondaryContainer = Color(0xFF241A00),
    tertiary = Color(0xFF397A3E),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFBBF0BA),
    onTertiaryContainer = Color(0xFF002106),
    background = Color.White,
    onBackground = Color(0xFF17201F),
    surface = Color.White,
    onSurface = Color(0xFF17201F),
    surfaceVariant = Color(0xFFE1E9E6),
    onSurfaceVariant = Color(0xFF414A48),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF7F9F8),
    surfaceContainer = Color(0xFFF1F5F3),
    surfaceContainerHigh = Color(0xFFEAF0EE),
    surfaceContainerHighest = Color(0xFFE3EAE8),
    outline = Color(0xFF717B78),
    outlineVariant = Color(0xFFC1CAC7),
    error = Color(0xFFBA1A1A),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002)
)

@Composable
fun TestTheme(
    darkTheme: Boolean = true,
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && darkTheme && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            dynamicDarkColorScheme(context)
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
