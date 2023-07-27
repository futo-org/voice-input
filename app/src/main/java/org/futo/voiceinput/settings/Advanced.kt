package org.futo.voiceinput.settings

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import org.futo.voiceinput.BuildConfig
import org.futo.voiceinput.DISALLOW_SYMBOLS
import org.futo.voiceinput.FORCE_SHOW_NOTICE
import org.futo.voiceinput.IS_ALREADY_PAID
import org.futo.voiceinput.NOTICE_REMINDER_TIME
import org.futo.voiceinput.R
import org.futo.voiceinput.Screen
import org.futo.voiceinput.VERBOSE_PROGRESS
import org.futo.voiceinput.ui.theme.Typography

@Composable
@Preview
fun AdvancedScreen(
    settingsViewModel: SettingsViewModel = viewModel(),
    navController: NavHostController = rememberNavController()
) {
    Screen(stringResource(R.string.advanced_settings)) {
        ScrollableList {
            SettingToggle(
                stringResource(R.string.suppress_non_speech_annotations),
                DISALLOW_SYMBOLS,
                default = true,
                subtitle = stringResource(R.string.suppress_non_speech_annotations_subtitle)
            )
            SettingToggle(
                stringResource(R.string.verbose_mode),
                VERBOSE_PROGRESS,
                default = false
            )
            val context = LocalContext.current
            SettingItem(
                title = stringResource(R.string.open_input_method_settings),
                onClick = { openImeOptions(context) }
            ) {
                Icon(Icons.Default.ArrowForward, contentDescription = stringResource(R.string.go))
            }
            SettingItem(title = stringResource(R.string.testing_menu), onClick = { navController.navigate("testing") }) {
                Icon(Icons.Default.ArrowForward, contentDescription = stringResource(R.string.go))
            }


            DevOnlySettings()
        }
    }
}
