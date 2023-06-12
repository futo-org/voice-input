package org.futo.voiceinput.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = Slate700,
    onPrimary = Slate50,

    primaryContainer = Slate600,
    onPrimaryContainer = Slate50,

    secondary = Zinc700,
    onSecondary = Zinc50,

    secondaryContainer = Zinc600,
    onSecondaryContainer = Zinc50,

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

private val LightColorScheme = lightColorScheme(
    primary = Sky500,
    secondary = Blue500,
    tertiary = Cyan500,

        /* Other default colors to override
    background = Color(0xFFFFFBFE),
    surface = Color(0xFFFFFBFE),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = Color(0xFF1C1B1F),
    onSurface = Color(0xFF1C1B1F),
    */
)

@Composable
fun WhisperVoiceInputTheme(
        darkTheme: Boolean = true,
        // Dynamic color is available on Android 12+
        dynamicColor: Boolean = true,
        content: @Composable () -> Unit
) {
    /*
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }
    */
    val colorScheme = DarkColorScheme // TODO: Figure out light/dynamic if it's worth it

    /*
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            if(view.context is Activity) {
                val window = (view.context as Activity).window
                window.statusBarColor = colorScheme.primary.toArgb()
                WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars =
                    darkTheme
            }
        }
    }
    */

    MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
    )
}