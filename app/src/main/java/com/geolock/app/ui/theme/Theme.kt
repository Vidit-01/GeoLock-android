package com.geolock.app.ui.theme

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

val Teal = Color(0xFF1B6B66)
val TealDark = Color(0xFF8ED4CE)
val Active = Color(0xFF1F8A4C)
val Degraded = Color(0xFFC47D12)
val Disabled = Color(0xFF6B7280)

private val LightColors = lightColorScheme(
    primary = Teal,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD5EFEB),
    onPrimaryContainer = Color(0xFF08332F),
    secondary = Color(0xFF4A635F),
    background = Color(0xFFF6F8F7),
    surface = Color(0xFFF6F8F7),
    surfaceVariant = Color(0xFFE4EEEC),
    onSurface = Color(0xFF1A1C1C),
    outline = Color(0xFF6F7977)
)

private val DarkColors = darkColorScheme(
    primary = TealDark,
    onPrimary = Color(0xFF003733),
    primaryContainer = Color(0xFF0F4E4A),
    onPrimaryContainer = Color(0xFFD5EFEB),
    secondary = Color(0xFFB1CCC7),
    background = Color(0xFF101414),
    surface = Color(0xFF101414),
    surfaceVariant = Color(0xFF3F4947),
    onSurface = Color(0xFFE1E3E2),
    outline = Color(0xFF899390)
)

@Composable
fun GeoLockTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val colors = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else {
        if (darkTheme) DarkColors else LightColors
    }
    MaterialTheme(
        colorScheme = colors,
        content = content
    )
}
