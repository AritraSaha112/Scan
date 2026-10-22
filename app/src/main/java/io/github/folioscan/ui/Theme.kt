package io.github.folioscan.ui

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

private val LightColors = lightColorScheme(
    primary = Color(0xFF0B6E69), onPrimary = Color.White,
    primaryContainer = Color(0xFFA6F2EA), onPrimaryContainer = Color(0xFF00201E),
    secondary = Color(0xFF4A6360), tertiary = Color(0xFF8A5A00),
    background = Color(0xFFF7FAF9), surface = Color(0xFFF7FAF9),
)
private val DarkColors = darkColorScheme(
    primary = Color(0xFF8AD5CE), onPrimary = Color(0xFF003734),
    primaryContainer = Color(0xFF00504C), onPrimaryContainer = Color(0xFFA6F2EA),
    secondary = Color(0xFFB1CCC8), tertiary = Color(0xFFFFC94D),
    background = Color(0xFF0F1514), surface = Color(0xFF0F1514),
)

/** Material You: follows the wallpaper colours on Android 12+ (your S21 FE), teal otherwise. */
@Composable
fun FolioTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val ctx = LocalContext.current
    val scheme = when {
        Build.VERSION.SDK_INT >= 31 -> if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        dark -> DarkColors
        else -> LightColors
    }
    MaterialTheme(colorScheme = scheme, content = content)
}
