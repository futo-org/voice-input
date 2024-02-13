package org.futo.voiceinput.theme

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.material3.ColorScheme
import org.futo.voiceinput.theme.presets.AMOLEDDarkPurple
import org.futo.voiceinput.theme.presets.ClassicMaterialDark
import org.futo.voiceinput.theme.presets.ClassicMaterialLight
import org.futo.voiceinput.theme.presets.DynamicDarkTheme
import org.futo.voiceinput.theme.presets.DynamicLightTheme
import org.futo.voiceinput.theme.presets.DynamicSystemTheme
import org.futo.voiceinput.theme.presets.VoiceInputTheme

data class ThemeOption(
    val dynamic: Boolean,
    val key: String,
    @StringRes val name: Int,
    val available: (Context) -> Boolean,
    val obtainColors: (Context) -> ColorScheme,
)

fun ThemeOption?.ensureAvailable(context: Context): ThemeOption? {
    return if(this == null) {
        null
    } else {
        if(!this.available(context)) {
            null
        } else {
            this
        }
    }
}

val ThemeOptions = hashMapOf(
    DynamicSystemTheme.key to DynamicSystemTheme,
    DynamicDarkTheme.key to DynamicDarkTheme,
    DynamicLightTheme.key to DynamicLightTheme,

    ClassicMaterialDark.key to ClassicMaterialDark,
    ClassicMaterialLight.key to ClassicMaterialLight,
    VoiceInputTheme.key to VoiceInputTheme,
    AMOLEDDarkPurple.key to AMOLEDDarkPurple,
)

val ThemeOptionKeys = arrayOf(
    VoiceInputTheme.key,
    DynamicDarkTheme.key,
    DynamicLightTheme.key,
    DynamicSystemTheme.key,

    ClassicMaterialDark.key,
    ClassicMaterialLight.key,
    AMOLEDDarkPurple.key,
)