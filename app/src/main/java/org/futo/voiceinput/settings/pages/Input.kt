package org.futo.voiceinput.settings.pages

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.withContext
import org.futo.voiceinput.R
import org.futo.voiceinput.settings.ENABLE_ANIMATIONS
import org.futo.voiceinput.settings.ENABLE_SOUND
import org.futo.voiceinput.settings.IS_VAD_ENABLED
import org.futo.voiceinput.settings.LANGUAGE_TOGGLES
import org.futo.voiceinput.settings.MANUALLY_SELECT_LANGUAGE
import org.futo.voiceinput.settings.NavigationItem
import org.futo.voiceinput.settings.NavigationItemStyle
import org.futo.voiceinput.settings.PERSONAL_DICTIONARY
import org.futo.voiceinput.settings.ScreenTitle
import org.futo.voiceinput.settings.ScrollableList
import org.futo.voiceinput.settings.SettingToggleDataStore
import org.futo.voiceinput.settings.SettingsViewModel
import org.futo.voiceinput.settings.Tip
import org.futo.voiceinput.settings.USE_LANGUAGE_SPECIFIC_MODELS
import org.futo.voiceinput.settings.getSettingBlocking
import org.futo.voiceinput.settings.useDataStore

@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Preview
fun InputScreen(
    settingsViewModel: SettingsViewModel = viewModel(),
    navController: NavHostController = rememberNavController()
) {
    val languages = useDataStore(LANGUAGE_TOGGLES)

    ScrollableList {
        ScreenTitle(title = stringResource(id = R.string.input_options), showBack = true, navController = navController)

        NavigationItem(
            title = stringResource(R.string.testing_menu),
            subtitle = stringResource(R.string.try_out_voice_input),
            style = NavigationItemStyle.Misc,
            navigate = { navController.navigate("testing") }
        )

        SettingToggleDataStore(
            stringResource(R.string.sounds),
            ENABLE_SOUND,
            subtitle = stringResource(R.string.will_play_a_sound_when_started_cancelled),
            disabledSubtitle = stringResource(R.string.will_not_play_sounds_when_started_cancelled)
        )

        SettingToggleDataStore(
            stringResource(R.string.animations),
            ENABLE_ANIMATIONS
        )

        if(languages.value.size > 1) {
            SettingToggleDataStore(stringResource(R.string.manually_select_language), MANUALLY_SELECT_LANGUAGE)
        }


        Spacer(modifier = Modifier.height(32.dp))

        Tip(stringResource(R.string.stop_on_silence_info))
        SettingToggleDataStore(stringResource(R.string.stop_on_silence), IS_VAD_ENABLED)

        // Option only has effect when English is active and at least one other language
        if(languages.value.size > 1 && languages.value.contains("en")) {
            Spacer(modifier = Modifier.height(32.dp))

            Tip(stringResource(R.string.use_language_specific_models_info))
            SettingToggleDataStore(
                stringResource(R.string.use_language_specific_models),
                USE_LANGUAGE_SPECIFIC_MODELS
            )
        }

        val personalDict = useDataStore(PERSONAL_DICTIONARY)
        val context = LocalContext.current
        val textFieldValue = remember { mutableStateOf(context.getSettingBlocking(
            PERSONAL_DICTIONARY.key, PERSONAL_DICTIONARY.default)) }

        LaunchedEffect(textFieldValue.value) {
            personalDict.setValue(textFieldValue.value)
        }

        TextField(value = textFieldValue.value, onValueChange = {
            textFieldValue.value = it
        }, placeholder = { Text("Personal dictionary") })
    }
}
