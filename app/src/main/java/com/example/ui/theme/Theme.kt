package com.example.ui.theme

import android.app.Activity
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

private val VeloDarkColorScheme = darkColorScheme(
    primary = ElectricTeal,
    onPrimary = OnTealDark,
    primaryContainer = DeepTealContainer,
    onPrimaryContainer = ElectricTeal,
    secondary = AmberBoost,
    onSecondary = OnTealDark,
    secondaryContainer = AmberContainerDark,
    onSecondaryContainer = AmberBoost,
    background = ObsidianBackground,
    onBackground = IceWhiteText,
    surface = MidnightSurface,
    onSurface = IceWhiteText,
    surfaceVariant = SlateSurfaceVariant,
    onSurfaceVariant = MutedSlateText,
    outline = SlateOutline
)

private val VeloLightColorScheme = lightColorScheme(
    primary = OceanTeal,
    onPrimary = CrispWhiteSurface,
    primaryContainer = LightTealContainer,
    onPrimaryContainer = DeepTealText,
    secondary = RichAmber,
    onSecondary = CrispWhiteSurface,
    secondaryContainer = LightAmberContainer,
    onSecondaryContainer = CharcoalText,
    background = AlabasterBackground,
    onBackground = CharcoalText,
    surface = CrispWhiteSurface,
    onSurface = CharcoalText,
    surfaceVariant = SoftCoolVariant,
    onSurfaceVariant = SlateGrayText,
    outline = LightOutline
)

private val VeloShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp)
)

@Composable
fun VeloPlayTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) VeloDarkColorScheme else VeloLightColorScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                WindowCompat.getInsetsController(window, view).apply {
                    isAppearanceLightStatusBars = !darkTheme
                    isAppearanceLightNavigationBars = !darkTheme
                }
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = VeloTypography,
        shapes = VeloShapes,
        content = content
    )
}
