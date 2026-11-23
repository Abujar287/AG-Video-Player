package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.example.data.model.AppThemePreset

private val BaseDarkColorScheme = darkColorScheme(
    primary = DarkPrimary,
    onPrimary = DarkOnPrimary,
    primaryContainer = DarkPrimaryContainer,
    onPrimaryContainer = DarkOnPrimaryContainer,
    secondary = DarkSecondary,
    onSecondary = DarkOnSecondary,
    secondaryContainer = DarkSecondaryContainer,
    onSecondaryContainer = DarkOnSecondaryContainer,
    background = DarkCanvas,
    onBackground = DarkTextPrimary,
    surface = DarkSurface,
    onSurface = DarkTextPrimary,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkTextMuted,
    outline = DarkOutline,
    error = DarkError
)

private val BaseLightColorScheme = lightColorScheme(
    primary = LightPrimary,
    onPrimary = LightOnPrimary,
    primaryContainer = LightPrimaryContainer,
    onPrimaryContainer = LightOnPrimaryContainer,
    secondary = LightSecondary,
    onSecondary = LightOnSecondary,
    secondaryContainer = LightSecondaryContainer,
    onSecondaryContainer = LightOnSecondaryContainer,
    background = LightCanvas,
    onBackground = LightTextPrimary,
    surface = LightSurface,
    onSurface = LightTextPrimary,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightTextMuted,
    outline = LightOutline,
    error = LightError
)

fun resolveThemePrimaryColor(themeId: String, darkTheme: Boolean): Color {
    if (themeId.startsWith("RANDOM:")) {
        val hue = themeId.removePrefix("RANDOM:").toFloatOrNull()?.coerceIn(0f, 359f) ?: 170f
        return if (darkTheme) Color.hsl(hue, 0.80f, 0.60f) else Color.hsl(hue, 0.78f, 0.36f)
    }
    val preset = AppThemePreset.fromId(themeId) ?: AppThemePreset.TEAL_MINT
    return Color(if (darkTheme) preset.darkPrimaryHex else preset.lightPrimaryHex)
}

@Composable
fun VeloPlayTheme(
    darkTheme: Boolean = true,
    themeId: String = AppThemePreset.TEAL_MINT.id,
    content: @Composable () -> Unit
) {
    val colorScheme = if (themeId.startsWith("RANDOM:")) {
        val hue = themeId.removePrefix("RANDOM:").toFloatOrNull()?.coerceIn(0f, 359f) ?: 170f
        if (darkTheme) {
            val primary = Color.hsl(hue, 0.80f, 0.60f)
            val container = Color.hsl(hue, 0.72f, 0.20f)
            val secondary = Color.hsl((hue + 38f) % 360f, 0.85f, 0.60f)
            val secContainer = Color.hsl((hue + 38f) % 360f, 0.72f, 0.20f)
            BaseDarkColorScheme.copy(
                primary = primary,
                onPrimary = Color(0xFF041412),
                primaryContainer = container,
                onPrimaryContainer = primary,
                secondary = secondary,
                secondaryContainer = secContainer,
                onSecondaryContainer = secondary
            )
        } else {
            val primary = Color.hsl(hue, 0.78f, 0.36f)
            val container = Color.hsl(hue, 0.72f, 0.90f)
            val onContainer = Color.hsl(hue, 0.82f, 0.14f)
            val secondary = Color.hsl((hue + 38f) % 360f, 0.78f, 0.40f)
            val secContainer = Color.hsl((hue + 38f) % 360f, 0.75f, 0.90f)
            BaseLightColorScheme.copy(
                primary = primary,
                onPrimary = Color.White,
                primaryContainer = container,
                onPrimaryContainer = onContainer,
                secondary = secondary,
                secondaryContainer = secContainer,
                onSecondaryContainer = onContainer
            )
        }
    } else {
        val preset = AppThemePreset.fromId(themeId) ?: AppThemePreset.TEAL_MINT
        if (darkTheme) {
            val primary = Color(preset.darkPrimaryHex)
            val container = Color(preset.darkContainerHex)
            BaseDarkColorScheme.copy(
                primary = primary,
                primaryContainer = container,
                onPrimaryContainer = primary
            )
        } else {
            val primary = Color(preset.lightPrimaryHex)
            val container = Color(preset.lightContainerHex)
            BaseLightColorScheme.copy(
                primary = primary,
                primaryContainer = container,
                onPrimaryContainer = Color(0xFF0F172A)
            )
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = VeloTypography,
        content = content
    )
}
