package org.futo.voiceinput.settings.pages

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import org.futo.voiceinput.R
import org.futo.voiceinput.settings.ALLOW_UNDERTRAINED_LANGUAGES
import org.futo.voiceinput.settings.BEAM_SEARCH
import org.futo.voiceinput.settings.DISALLOW_SYMBOLS
import org.futo.voiceinput.settings.DevOnlySettings
import org.futo.voiceinput.settings.ENABLE_30S_LIMIT
import org.futo.voiceinput.settings.NavigationItem
import org.futo.voiceinput.settings.NavigationItemStyle
import org.futo.voiceinput.settings.STREAMING_TIER
import org.futo.voiceinput.settings.ScreenTitle
import org.futo.voiceinput.settings.SettingRadio
import org.futo.voiceinput.settings.SettingToggleDataStore
import org.futo.voiceinput.settings.SettingsRootList
import org.futo.voiceinput.settings.SettingsViewModel
import org.futo.voiceinput.settings.VERBOSE_PROGRESS
import org.futo.voiceinput.settings.openImeOptions

@Composable
@Preview
fun AdvancedScreen(
    settingsViewModel: SettingsViewModel = viewModel(),
    navController: NavHostController = rememberNavController()
) {
    val context = LocalContext.current
    SettingsRootList {
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

        SettingToggleDataStore(stringResource(R.string.use_beam_search), BEAM_SEARCH, subtitle = stringResource(R.string.recommended))

        SettingToggleDataStore(
            stringResource(R.string.allow_undertrained_languages),
            ALLOW_UNDERTRAINED_LANGUAGES,
            subtitle = stringResource(R.string.allow_undertrained_languages_subtitle),
            onChanged = {
            }
        )

        NavigationItem(
            title = stringResource(R.string.open_input_method_settings),
            style = NavigationItemStyle.Misc,
            navigate = { openImeOptions(context) }
        )

        SettingToggleDataStore(
            stringResource(R.string.re_enable_30s_limit),
            ENABLE_30S_LIMIT,
        )

        SettingRadio(
            stringResource(R.string.model_settings_streaming_tier),
            listOf(0, 1, 2, 3),
            listOf(
                stringResource(R.string.model_settings_streaming_tier_0),
                stringResource(R.string.model_settings_streaming_tier_1),
                stringResource(R.string.model_settings_streaming_tier_2),
                stringResource(R.string.model_settings_streaming_tier_3),
            ),
            STREAMING_TIER,
        )

        DevOnlySettings()
    }
}
