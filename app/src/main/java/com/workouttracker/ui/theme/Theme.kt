package com.workouttracker.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val WorkoutColors = darkColorScheme(
    primary = Lime,
    onPrimary = OnLime,
    primaryContainer = LimeContainer,
    onPrimaryContainer = OnLimeContainer,
    secondary = Orange,
    onSecondary = OnOrange,
    secondaryContainer = OrangeContainer,
    onSecondaryContainer = OnOrangeContainer,
    background = Ink,
    onBackground = TextPrimary,
    surface = Ink,
    onSurface = TextPrimary,
    surfaceVariant = Surface2,
    onSurfaceVariant = TextSecondary,
    surfaceContainerLowest = Ink,
    surfaceContainerLow = Surface1,
    surfaceContainer = Surface1,
    surfaceContainerHigh = Surface2,
    surfaceContainerHighest = Line,
    outline = LineStrong,
    outlineVariant = Line,
    // Snackbars use the inverse roles; keep them dark with a lime action.
    inverseSurface = Line,
    inverseOnSurface = TextPrimary,
    inversePrimary = Lime,
    error = ErrorRed,
    onError = OnErrorRed,
)

/** The app is dark-only by design: gyms are dim and the screen is glanced at between sets. */
@Composable
fun WorkoutTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = WorkoutColors,
        typography = WorkoutTypography,
        content = content,
    )
}
