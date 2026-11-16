package app.hdaplan

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

private val LightFallback = lightColorScheme(
    primary = Color(0xFF1B5FB4),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD6E3FF),
    onPrimaryContainer = Color(0xFF001B3E),
    secondaryContainer = Color(0xFFDCE2F9),
    onSecondaryContainer = Color(0xFF151B2C)
)

private val DarkFallback = darkColorScheme(
    primary = Color(0xFFA8C8FF),
    onPrimary = Color(0xFF003063),
    primaryContainer = Color(0xFF00468C),
    onPrimaryContainer = Color(0xFFD6E3FF),
    secondaryContainer = Color(0xFF3F4759),
    onSecondaryContainer = Color(0xFFDCE2F9)
)

/** Hell/Dunkel automatisch; ab Android 12 mit den Farben des Handy-Hintergrunds (sonst Blau). */
@Composable
fun AppTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val ctx = LocalContext.current
    val scheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && dark -> dynamicDarkColorScheme(ctx)
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> dynamicLightColorScheme(ctx)
        dark -> DarkFallback
        else -> LightFallback
    }
    MaterialTheme(colorScheme = scheme, content = content)
}

/** Festes Farbpaar pro Veranstaltungstyp (bleibt unabhängig vom Hintergrund-Design gut unterscheidbar). */
class TypeColors(val container: Color, val onContainer: Color, val accent: Color, val onAccent: Color)

@Composable
fun typeColors(type: Char): TypeColors {
    val dark = isSystemInDarkTheme()
    return when (type) {
        'V' -> if (dark) {
            TypeColors(Color(0xFF1F3F6B), Color(0xFFD6E3FF), Color(0xFFA8C8FF), Color(0xFF002F65))
        } else {
            TypeColors(Color(0xFFD6E3FF), Color(0xFF001B3E), Color(0xFF1B5FB4), Color.White)
        }
        'Ü' -> if (dark) {
            TypeColors(Color(0xFF1F4D30), Color(0xFFC4EED0), Color(0xFF8FD5A5), Color(0xFF00391B))
        } else {
            TypeColors(Color(0xFFC4EED0), Color(0xFF00210D), Color(0xFF1F6B3A), Color.White)
        }
        'L' -> if (dark) {
            TypeColors(Color(0xFF5C3100), Color(0xFFFFDCC2), Color(0xFFFFB77C), Color(0xFF4E2600))
        } else {
            TypeColors(Color(0xFFFFDCC2), Color(0xFF2E1500), Color(0xFF9A4A00), Color.White)
        }
        else -> if (dark) {
            TypeColors(Color(0xFF4F378B), Color(0xFFEADDFF), Color(0xFFCFBCFF), Color(0xFF381E72))
        } else {
            TypeColors(Color(0xFFEADDFF), Color(0xFF21005E), Color(0xFF6750A4), Color.White)
        }
    }
}
