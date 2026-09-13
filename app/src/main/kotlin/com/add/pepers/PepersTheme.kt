package com.add.pepers

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val PepersLightColors = lightColorScheme(
    primary = Purple,
    onPrimary = AppSurface,
    primaryContainer = AppPrimarySoft,
    onPrimaryContainer = AppText,
    secondary = Green,
    onSecondary = AppSurface,
    secondaryContainer = CardWorkBg,
    onSecondaryContainer = AppText,
    tertiary = Blue,
    onTertiary = AppSurface,
    error = Red,
    onError = AppSurface,
    background = AppBackground,
    onBackground = AppText,
    surface = AppSurface,
    onSurface = AppText,
    surfaceVariant = AppSurfaceAlt,
    onSurfaceVariant = AppMuted,
    outline = AppBorder
)

private val PepersTypography = Typography()

@Composable
internal fun PepersTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = PepersLightColors,
        typography = PepersTypography,
        content = content
    )
}
