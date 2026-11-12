package com.habitramp.presentation.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import android.os.Build

private val LightColors = lightColorScheme(
    primary = Color(0xFF0B6B57),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFB8EEDD),
    onPrimaryContainer = Color(0xFF002019),
    secondary = Color(0xFF4B635B),
    secondaryContainer = Color(0xFFCDE8DE),
    tertiary = Color(0xFFB05A00),
    tertiaryContainer = Color(0xFFFFDCC2),
    background = Color(0xFFF6FBF7),
    surface = Color(0xFFF6FBF7),
    surfaceVariant = Color(0xFFDBE5DF),
    error = Color(0xFFBA1A1A)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF9CD3C3),
    onPrimary = Color(0xFF00382C),
    primaryContainer = Color(0xFF005141),
    onPrimaryContainer = Color(0xFFB8EEDD),
    secondary = Color(0xFFB2CCC2),
    secondaryContainer = Color(0xFF334B44),
    tertiary = Color(0xFFFFB77C),
    tertiaryContainer = Color(0xFF6E3900),
    background = Color(0xFF0F1512),
    surface = Color(0xFF0F1512),
    surfaceVariant = Color(0xFF3F4945),
    error = Color(0xFFFFB4AB)
)

@Composable
fun HabitRampTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        darkTheme -> DarkColors
        else -> LightColors
    }
    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
