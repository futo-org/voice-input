package org.futo.voiceinput.theme

import android.app.Activity
import android.content.Context
import android.os.Build
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.runBlocking
import org.futo.voiceinput.settings.THEME_KEY
import org.futo.voiceinput.settings.getSetting
import org.futo.voiceinput.settings.useDataStoreValueNullable
import org.futo.voiceinput.theme.presets.VoiceInputTheme

val DarkColorScheme = darkColorScheme(
    primary=Color(0xFF7EAEF2),
    onPrimary=Color(0xFF0E2545),
    primaryContainer=Color(0xFF13325E),
    onPrimaryContainer=Color(0xFFDEEDFC),
    secondary=Color(0xFFC5E0FC),
    onSecondary=Color(0xFF374D6E),
    secondaryContainer=Color(0xFF1B2738),
    onSecondaryContainer=Color(0xFF97B0C9),
    tertiary=Color(0xFFF2CB7D),
    onTertiary=Color(0xFF402D08),
    tertiaryContainer=Color(0xFF735825),
    onTertiaryContainer=Color(0xFFFFF0D1),
    error=Color(0xFFFF8C80),
    onError=Color(0xFF4D2B2B),
    errorContainer=Color(0xFF803B3B),
    onErrorContainer=Color(0xFFFFDFDB),
    outline=Color(0xFF9198A1),
    outlineVariant=Color(0xFF1F2833),
    surface=Color(0xFF0F172A),
    onSurface=Color(0xFFF8FAFC),
    onSurfaceVariant=Color(0xFFB4BABF),
    surfaceContainerHighest=Color(0xFF252E42),
)

fun applyWindowColors(context: Context, backgroundColor: Color) {
    val window = (context as? Activity)?.window ?: return

    val color = backgroundColor.toArgb()

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        window.isNavigationBarContrastEnforced = false
    }

    window.statusBarColor = color
    window.navigationBarColor = color

    window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
}

@Composable
fun StatusBarColorSetter() {
    val backgroundColor = MaterialTheme.colorScheme.background
    val context = LocalContext.current
    LaunchedEffect(backgroundColor) {
        applyWindowColors(context, backgroundColor)
    }
}

@Composable
fun UixThemeWrapper(colorScheme: ColorScheme, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = colorScheme,
        content = content,
    )
}

@Composable
fun UixThemeAuto(useSafeDrawingPadding: Boolean = true, content: @Composable () -> Unit) {
    val context = LocalContext.current

    val initialSetting = remember { runBlocking { context.getSetting(THEME_KEY.key, THEME_KEY.default) } }
    println("initial setting is $initialSetting")

    val initialThemeOption = remember {
        ThemeOptions[initialSetting].ensureAvailable(context)
    }
    val themeIdx = useDataStoreValueNullable(THEME_KEY.key, THEME_KEY.default)

    val theme: ThemeOption = themeIdx?.let { ThemeOptions[it].ensureAvailable(context) }
        ?: initialThemeOption
        ?: VoiceInputTheme

    println("Ok theme is ${theme.key}")
    val colors = remember(theme.key) { theme.obtainColors(context) }

    UixThemeWrapper(colorScheme = colors, {
        StatusBarColorSetter()
        Box(if(useSafeDrawingPadding) {
            Modifier.background(colors.background).safeDrawingPadding()
        } else {
            Modifier
        }) {
            content()
        }
    })
}