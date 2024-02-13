package org.futo.voiceinput.theme.selector

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.futo.voiceinput.R
import org.futo.voiceinput.settings.THEME_KEY
import org.futo.voiceinput.settings.useDataStore
import org.futo.voiceinput.theme.ThemeOption
import org.futo.voiceinput.theme.ThemeOptionKeys
import org.futo.voiceinput.theme.ThemeOptions
import org.futo.voiceinput.theme.Typography
import org.futo.voiceinput.theme.UixThemeWrapper
import org.futo.voiceinput.theme.presets.AMOLEDDarkPurple
import org.futo.voiceinput.theme.presets.ClassicMaterialDark
import org.futo.voiceinput.theme.presets.VoiceInputTheme

// TODO: For Dynamic System we need to show the user that it switches between light/dark
@Composable
fun ThemePreview(theme: ThemeOption, isSelected: Boolean = false, onClick: () -> Unit = { }) {
    val context = LocalContext.current
    val colors = remember { theme.obtainColors(context) }

    val currColors = MaterialTheme.colorScheme

    val borderWidth = if (isSelected) {
        2.dp
    } else {
        0.dp
    }

    val borderColor = if (isSelected) {
        currColors.inversePrimary
    } else {
        Color.Transparent
    }

    val textColor = colors.onBackground

    val voiceCircleColor = colors.primaryContainer
    val micIconColor = colors.onPrimaryContainer

    val keyboardShape = RoundedCornerShape(8.dp)

    Surface(
        modifier = Modifier
            .padding(12.dp)
            .width(172.dp)
            .height(128.dp)
            .border(borderWidth, borderColor, keyboardShape)
            .clickable { onClick() },
        color = colors.surface,
        shape = keyboardShape
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Theme name and action bar
            Text(
                text = stringResource(theme.name),
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .background(colors.background)
                    .fillMaxWidth()
                    .padding(4.dp),
                color = textColor,
                style = Typography.labelSmall
            )

            // Keyboard contents
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp, 32.dp, 8.dp, 8.dp)
            ) {
                val icon = painterResource(R.drawable.mic_2_)
                // Button
                Surface(
                    modifier = Modifier
                        .size(48.dp)
                        .align(Alignment.Center),
                    color = voiceCircleColor,
                    shape = CircleShape
                ) {
                    /*Canvas(modifier = Modifier.fillMaxSize()) {
                        with(icon) {
                            translate(size.width / 4.0f, size.height / 4.0f) {
                                draw(size / 2.0f, colorFilter = ColorFilter.tint(micIconColor))
                            }
                        }
                    }*/
                }
            }
        }
    }
}

@Composable
fun AddCustomThemeButton(onClick: () -> Unit = { }) {
    val context = LocalContext.current
    val currColors = MaterialTheme.colorScheme

    val keyboardShape = RoundedCornerShape(8.dp)

    Surface(
        modifier = Modifier
            .padding(12.dp)
            .width(172.dp)
            .height(128.dp)
            .clickable { onClick() },
        color = currColors.surfaceVariant,
        shape = keyboardShape
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Icon(
                Icons.Default.Add, contentDescription = "", modifier = Modifier
                    .size(48.dp)
                    .align(
                        Alignment.Center
                    )
            )
        }
    }
}

@Composable
fun ThemePicker(onSelected: (ThemeOption) -> Unit) {
    val context = LocalContext.current

    val currentTheme = useDataStore(THEME_KEY.key, "").value

    val isInspecting = LocalInspectionMode.current
    val availableThemeOptions = remember {
        ThemeOptionKeys.mapNotNull { key ->
            ThemeOptions[key]?.let { Pair(key, it) }
        }.filter {
            it.second.available(context)
        }.filter {
            when (isInspecting) {
                true -> !it.second.dynamic
                else -> true
            }
        }
    }

    LazyVerticalGrid(
        modifier = Modifier.fillMaxWidth(),
        columns = GridCells.Adaptive(minSize = 172.dp)
    ) {
        items(availableThemeOptions.count()) {
            val themeOption = availableThemeOptions[it].second

            ThemePreview(themeOption, isSelected = themeOption.key == currentTheme) {
                onSelected(themeOption)
            }
        }

        item {
            AddCustomThemeButton {
                // TODO: Custom themes
                val toast = Toast.makeText(context, "Custom themes coming eventually", Toast.LENGTH_SHORT)
                toast.show()
            }
        }
    }
}


@Preview
@Composable
private fun ThemePickerPreview() {
    Column {
        UixThemeWrapper(VoiceInputTheme.obtainColors(LocalContext.current)) {
            Surface(
                color = MaterialTheme.colorScheme.background
            ) {
                ThemePicker {}
            }
        }
        UixThemeWrapper(ClassicMaterialDark.obtainColors(LocalContext.current)) {
            Surface(
                color = MaterialTheme.colorScheme.background
            ) {
                ThemePicker {}
            }
        }
        UixThemeWrapper(AMOLEDDarkPurple.obtainColors(LocalContext.current)) {
            Surface(
                color = MaterialTheme.colorScheme.background
            ) {
                ThemePicker {}
            }
        }
    }
}