package br.ufg.akcit.smartglasses.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = AppColor.DeepBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF1E4976),
    onPrimaryContainer = Color(0xFFD1E4FF),

    secondary = Color(0xFF90CAF9),
    onSecondary = Color(0xFF003258),
    secondaryContainer = Color(0xFF263238),
    onSecondaryContainer = Color(0xFFCFD8DC),

    error = AppColor.Red,
    onError = Color.White,
    errorContainer = Color(0xFF4A0010),
    onErrorContainer = Color(0xFFFFB3B3),

    background = Color(0xFF121212),
    onBackground = Color.White,
    surface = Color(0xFF1E1E1E),
    onSurface = Color.White,
    surfaceVariant = Color(0xFF2C2C2C),
    onSurfaceVariant = Color(0xFFAAAAAA),
    outline = Color(0xFF8A8A8A),
    outlineVariant = Color(0xFF444746),

    surfaceContainerLowest = Color(0xFF0F0F0F),
    surfaceContainerLow = Color(0xFF1A1A1A),
    surfaceContainer = Color(0xFF202020),
    surfaceContainerHigh = Color(0xFF2B2B2B),
    surfaceContainerHighest = Color(0xFF363636),
)

private val LightColorScheme = lightColorScheme(
    primary = AppColor.DeepBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD8E6FF),
    onPrimaryContainer = Color(0xFF003273),

    secondary = Color(0xFF4A6278),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE2E8F0),
    onSecondaryContainer = Color(0xFF1E293B),

    error = AppColor.Red,
    onError = Color.White,
    errorContainer = AppColor.DestructiveBackground,
    onErrorContainer = AppColor.DestructiveForeground,

    background = Color(0xFFF8F9FA),
    onBackground = Color(0xFF1C1B1F),
    surface = Color.White,
    onSurface = Color(0xFF1C1B1F),
    surfaceVariant = Color(0xFFE7E0EC),
    onSurfaceVariant = Color(0xFF6B6B6B),
    outline = Color(0xFF79747E),
    outlineVariant = Color(0xFFCAC4D0),

    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF3F4F6),
    surfaceContainer = Color(0xFFECEEF1),
    surfaceContainerHigh = Color(0xFFE2E4E8),
    surfaceContainerHighest = Color(0xFFD8DADC),
)

@Composable
fun SmartGlassesTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    val appColorScheme = AppColorScheme(
        success = AppColor.Green,
        onSuccess = Color.White,
        warning = AppColor.Yellow,
        onWarning = Color.Black,
        warningContainer = if (darkTheme) Color(0xFF3E2E04) else AppColor.UpdateRequiredBackground,
        onWarningContainer = if (darkTheme) Color(0xFFFFE082) else AppColor.UpdateRequiredForeground,
    )

    CompositionLocalProvider(LocalAppColorScheme provides appColorScheme) {
        MaterialTheme(
            colorScheme = colorScheme,
            content = content,
        )
    }
}
