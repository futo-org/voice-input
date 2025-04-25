package org.futo.voiceinput.theme

import android.app.Activity
import android.content.Context
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
    primary = Slate600,
    onPrimary = Slate50,

    primaryContainer = Slate700,
    onPrimaryContainer = Slate50,

    secondary = Slate700,
    onSecondary = Slate50,

    secondaryContainer = Slate600,
    onSecondaryContainer = Slate50,

    tertiary = Stone700,
    onTertiary = Stone50,

    tertiaryContainer = Stone600,
    onTertiaryContainer = Stone50,

    background = Slate900,
    onBackground = Slate50,

    surface = Slate800,
    onSurface = Slate50,

    outline = Slate300,

    surfaceVariant = Slate800,
    onSurfaceVariant = Slate300
)

fun applyWindowColors(context: Context, backgroundColor: Color) {
    val window = (context as Activity).window
    val color = backgroundColor.copy(alpha = 0.75f).toArgb()

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
            typography = Typography,
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
        Box(if(useSafeDrawingPadding) {
            Modifier.background(colors.background).safeDrawingPadding()
        } else {
            Modifier
        }) {
            content()
        }
    })
}