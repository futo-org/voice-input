package org.futo.voiceinput.settings.pages

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import org.futo.voiceinput.MULTILINGUAL_MODELS
import org.futo.voiceinput.R
import org.futo.voiceinput.settings.ALLOW_UNDERTRAINED_LANGUAGES
import org.futo.voiceinput.settings.DISALLOW_SYMBOLS
import org.futo.voiceinput.settings.DevOnlySettings
import org.futo.voiceinput.settings.MULTILINGUAL_MODEL_INDEX
import org.futo.voiceinput.settings.NavigationItem
import org.futo.voiceinput.settings.NavigationItemStyle
import org.futo.voiceinput.settings.ScreenTitle
import org.futo.voiceinput.settings.ScrollableList
import org.futo.voiceinput.settings.SettingToggleDataStore
import org.futo.voiceinput.settings.SettingsViewModel
import org.futo.voiceinput.settings.VERBOSE_PROGRESS
import org.futo.voiceinput.settings.openImeOptions
import org.futo.voiceinput.settings.useDataStore

@Composable
@Preview
fun AdvancedScreen(
    settingsViewModel: SettingsViewModel = viewModel(),
    navController: NavHostController = rememberNavController()
) {
    val context = LocalContext.current
    val (_, setMultilingualIdx) = useDataStore(
        key = MULTILINGUAL_MODEL_INDEX.key,
        default = MULTILINGUAL_MODEL_INDEX.default
    )

    ScrollableList {
        ScreenTitle(title = stringResource(id = R.string.advanced_settings), showBack = true, navController = navController)

        SettingToggleDataStore(
            stringResource(R.string.suppress_non_speech_annotations),
            DISALLOW_SYMBOLS,
            subtitle = stringResource(R.string.suppress_non_speech_annotations_subtitle)
        )

        SettingToggleDataStore(
            stringResource(R.string.verbose_mode),
            VERBOSE_PROGRESS
        )

        SettingToggleDataStore(
            stringResource(R.string.allow_undertrained_languages),
            ALLOW_UNDERTRAINED_LANGUAGES,
            subtitle = stringResource(R.string.allow_undertrained_languages_subtitle),
            onChanged = {
                // Automatically change model to largest one
                if(it) {
                    setMultilingualIdx(MULTILINGUAL_MODELS.size - 1)
                }
            }
        )

        NavigationItem(
            title = stringResource(R.string.open_input_method_settings),
            style = NavigationItemStyle.Misc,
            navigate = { openImeOptions(context) }
        )

        DevOnlySettings()
    }
}
