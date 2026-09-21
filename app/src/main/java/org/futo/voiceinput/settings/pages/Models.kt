package org.futo.voiceinput.settings.pages

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import org.futo.voiceinput.R
import org.futo.voiceinput.loadCurrentModelList
import org.futo.voiceinput.settings.ENABLE_MULTILINGUAL
import org.futo.voiceinput.settings.ENGLISH_MODEL_INDEX
import org.futo.voiceinput.settings.LANGUAGE_TOGGLES
import org.futo.voiceinput.settings.MANUALLY_SELECT_LANGUAGE
import org.futo.voiceinput.settings.MULTILINGUAL_MODEL_INDEX
import org.futo.voiceinput.settings.PERSONAL_DICTIONARY
import org.futo.voiceinput.settings.STREAMING_TIER
import org.futo.voiceinput.settings.ScreenTitle
import org.futo.voiceinput.settings.SettingRadio
import org.futo.voiceinput.settings.SettingToggleDataStore
import org.futo.voiceinput.settings.SettingsRootList
import org.futo.voiceinput.settings.SettingsViewModel
import org.futo.voiceinput.settings.Tip
import org.futo.voiceinput.settings.USE_LANGUAGE_SPECIFIC_MODELS
import org.futo.voiceinput.settings.useDataStore
import org.futo.voiceinput.shared.ENGLISH_MODELS
import org.futo.voiceinput.shared.MULTILINGUAL_MODELS
import org.futo.voiceinput.shared.types.getLanguageFromWhisperString
import org.futo.voiceinput.startModelDownloadActivity

@Composable
fun modelsSubtitle(): String? {
    val context = LocalContext.current
    val models = remember {
        context.loadCurrentModelList()
    }

    return models.joinToString { context.getString(it.name) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonalDictionaryEditor(disabled: Boolean) {
    val personalDict = useDataStore(PERSONAL_DICTIONARY)
    val textFieldValue = remember { mutableStateOf(personalDict.value) }

    LaunchedEffect(textFieldValue.value) {
        personalDict.setValue(textFieldValue.value)
    }
    
    ScreenTitle(title = stringResource(R.string.personal_dictionary))

    TextField(
        value = textFieldValue.value,
        onValueChange = {
            textFieldValue.value = it
        },
        placeholder = { Text(stringResource(R.string.personal_dictionary_placeholder)) },
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp, 4.dp),
        enabled = !disabled
    )

}

@Composable
@Preview
fun ModelsScreen(
    settingsViewModel: SettingsViewModel = viewModel(),
    navController: NavHostController = rememberNavController()
) {
    val (useMultilingual, _) = useDataStore(ENABLE_MULTILINGUAL)

    val englishModelIndex = useDataStore(ENGLISH_MODEL_INDEX)
    val multilingualModelIndex = useDataStore(MULTILINGUAL_MODEL_INDEX)

    val (languages, _) = useDataStore(LANGUAGE_TOGGLES)
    val (useLanguageSpecificModels, _) = useDataStore(USE_LANGUAGE_SPECIFIC_MODELS)

    val context = LocalContext.current

    val launchDownloaderIfNecessary = {
        if (useMultilingual) {
            context.startModelDownloadActivity(
                listOf(
                    ENGLISH_MODELS[englishModelIndex.value],
                    MULTILINGUAL_MODELS[multilingualModelIndex.value]
                ),
                forUpgrade = false
            )
        } else {
            context.startModelDownloadActivity(listOf(ENGLISH_MODELS[englishModelIndex.value]), forUpgrade = false)
        }
    }

    LaunchedEffect(listOf(useMultilingual, englishModelIndex.value, multilingualModelIndex.value)) {
        launchDownloaderIfNecessary()
    }

    val modelEn = ENGLISH_MODELS[englishModelIndex.value]
    val model = MULTILINGUAL_MODELS[multilingualModelIndex.value]

    SettingsRootList {
        ScreenTitle(stringResource(R.string.model_options), showBack = true, navController = navController)

        if(languages.size > 1) {
            SettingToggleDataStore(
                stringResource(R.string.manually_select_language),
                MANUALLY_SELECT_LANGUAGE,
                subtitle = stringResource(R.string.manual_language_selection_toggle_subtitle)
            )
        }

        if(!modelEn.caps.supportsPersonalDict && (useLanguageSpecificModels || languages == setOf("en"))) {
            Tip(stringResource(R.string.model_settings_model_doesnt_support_personal_dict, stringResource(modelEn.name)))
        }
        if(!model.caps.supportsPersonalDict && languages != setOf("en")) {
            Tip(stringResource(R.string.model_settings_model_doesnt_support_personal_dict, stringResource(model.name)))
        }

        PersonalDictionaryEditor(disabled = false)

        Spacer(modifier = Modifier.height(32.dp))

        if (useMultilingual) {
            if(!model.caps.supportsLanguageDetection) {
                Tip(stringResource(R.string.model_settings_model_doesnt_support_detection))
            }

            val unsupportedLanguages = languages.map { getLanguageFromWhisperString(it) }.filter { it !in model.caps.supportedLanguages }
            if(unsupportedLanguages.isNotEmpty()) {
                Tip(stringResource(
                    R.string.language_settings_model_doesnt_support_languages,
                    stringResource(model.name),
                    unsupportedLanguages.joinToString(", ")
                ))
            }

            SettingRadio(
                stringResource(R.string.multilingual_model),
                MULTILINGUAL_MODELS.indices.toList(),
                MULTILINGUAL_MODELS.map { stringResource(it.name) },
                MULTILINGUAL_MODEL_INDEX
            )
        }

        if((!useMultilingual) || (languages.contains("en") && useLanguageSpecificModels)) {
            SettingRadio(
                stringResource(R.string.english_model),
                ENGLISH_MODELS.indices.toList(),
                ENGLISH_MODELS.map { stringResource(it.name) },
                ENGLISH_MODEL_INDEX
            )
        }

        if(model.caps.supportsStreaming || modelEn.caps.supportsStreaming) {
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
        }

        Tip(stringResource(R.string.parameter_count_tip))
    }
}
