package br.ufg.akcit.smartglasses.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

data class AppColorScheme(
    val success: Color,
    val onSuccess: Color,
    val warning: Color,
    val onWarning: Color,
    val warningContainer: Color,
    val onWarningContainer: Color,
)

val LocalAppColorScheme = staticCompositionLocalOf {
    AppColorScheme(
        success = AppColor.Green,
        onSuccess = Color.White,
        warning = AppColor.Yellow,
        onWarning = Color.Black,
        warningContainer = AppColor.UpdateRequiredBackground,
        onWarningContainer = AppColor.UpdateRequiredForeground,
    )
}

val MaterialTheme.appColors: AppColorScheme
    @Composable get() = LocalAppColorScheme.current
