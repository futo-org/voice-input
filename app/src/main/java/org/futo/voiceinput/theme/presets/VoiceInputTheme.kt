package org.futo.voiceinput.theme.presets

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import org.futo.voiceinput.theme.DarkColorScheme
import org.futo.voiceinput.theme.ThemeOption
import org.futo.voiceinput.R
import org.futo.voiceinput.theme.selector.ThemePreview

val VoiceInputTheme = ThemeOption(
    dynamic = false,
    key = "VoiceInputTheme",
    name = R.string.voice_input_theme_name,
    available = { true }
) {
    DarkColorScheme
}

@Composable
@Preview
private fun PreviewTheme() {
    ThemePreview(VoiceInputTheme)
}