package fi.veneappi.app.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val OceanDark =
    darkColorScheme(
        primary = Color(0xFF7EC8E3),
        secondary = Color(0xFF0B3D91),
        tertiary = Color(0xFFB8E0FF),
        background = Color(0xFF0B1220),
        surface = Color(0xFF121A2B),
    )

private val OceanLight =
    lightColorScheme(
        primary = Color(0xFF0B3D91),
        secondary = Color(0xFF1565C0),
        tertiary = Color(0xFF7EC8E3),
        background = Color(0xFFF4F8FB),
        surface = Color(0xFFFFFFFF),
    )

@Composable
fun VeneappiTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val scheme = if (darkTheme) OceanDark else OceanLight
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val activity = view.context as? Activity ?: return@SideEffect
            val window = activity.window
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }
    MaterialTheme(
        colorScheme = scheme,
        typography = Typography,
        content = content,
    )
}
