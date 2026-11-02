package org.futo.voiceinput.settings.pages

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import org.futo.voiceinput.R
import org.futo.voiceinput.loadCurrentModelList
import org.futo.voiceinput.parseModelOptions
import org.futo.voiceinput.settings.LANGUAGE_TOGGLES
import org.futo.voiceinput.settings.MANUALLY_SELECT_LANGUAGE
import org.futo.voiceinput.settings.MODEL_OPTIONS
import org.futo.voiceinput.settings.PERSONAL_DICTIONARY
import org.futo.voiceinput.settings.ScreenTitle
import org.futo.voiceinput.settings.SettingItem
import org.futo.voiceinput.settings.SettingToggleDataStoreItem
import org.futo.voiceinput.settings.SettingsRootList
import org.futo.voiceinput.settings.SettingsViewModel
import org.futo.voiceinput.settings.Tip
import org.futo.voiceinput.settings.TipKind
import org.futo.voiceinput.settings.useDataStore
import org.futo.voiceinput.settings.useInvertedDataStore
import org.futo.voiceinput.shared.ALL_MODELS
import org.futo.voiceinput.shared.types.Language
import org.futo.voiceinput.shared.types.ModelLoader
import org.futo.voiceinput.shared.types.getLanguageFromWhisperString
import org.futo.voiceinput.shared.types.toWhisperString
import org.futo.voiceinput.theme.Typography

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

    if(textFieldValue.value.isNotEmpty() && !textFieldValue.value.contains(',') && (textFieldValue.value.contains(' ') || textFieldValue.value.contains('\n'))) {
        Tip(stringResource(R.string.personal_dictionary_no_comma_warning), TipKind.Warning)
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
    val context = LocalContext.current

    val (languages, _) = useDataStore(LANGUAGE_TOGGLES)
    val (modelsRaw, setModels) = useDataStore(MODEL_OPTIONS)

    val models = remember(modelsRaw) { modelsRaw.parseModelOptions().toMap() }

    val multilingualShouldBeShown = remember(languages, models) {
        val languagesWithModelsSet = models.keys.filterNotNull().toSet()
        languages.any { getLanguageFromWhisperString(it) !in languagesWithModelsSet }
    }

    val personalDict = useDataStore(PERSONAL_DICTIONARY)

    val multilingualModel = models[null]!!

    val updateModelOptions = { language: String, option: ModelLoader? ->
        val newModels = modelsRaw.toMutableSet()
        newModels.removeIf { it.startsWith("$language:") }
        if(option != null) {
            newModels.add("$language:${option.id}")
        } else {
            newModels.add("$language:")
        }

        setModels(newModels)
    }

    SettingsRootList {
        ScreenTitle(stringResource(R.string.model_options), showBack = true, navController = navController)

        if(languages.size > 1 && multilingualModel.caps.supportsLanguageDetection && multilingualShouldBeShown) {
            SettingToggleDataStoreItem(
                stringResource(R.string.autodetect_language),
                useInvertedDataStore(MANUALLY_SELECT_LANGUAGE),
                subtitle = stringResource(R.string.autodetect_language_subtitle)
            )
        }

        if(models.any { it.value.caps.supportsPersonalDict }) {
            PersonalDictionaryEditor(disabled = false)
        }

        Spacer(modifier = Modifier.height(32.dp))

        val modelItems = @Composable { title: Int, curr: ModelLoader?, set: (ModelLoader?) -> Unit, list: List<ModelLoader?> ->
            Text(stringResource(title), style = Typography.Heading.Medium.copy(fontSize = 16.sp), modifier = Modifier
                .padding(24.dp, 4.dp).alpha(0.8f))
            list.forEach {
                SettingItem(
                    title = stringResource(it?.name ?: R.string.model_disabled_use_multilingual),
                    subtitle = if(it != null && curr == it && !it.caps.supportsPersonalDict && personalDict.value.isNotBlank()) stringResource(R.string.no_personal_dict_subtitle) else null,
                    onClick = {
                        set(it)
                    },
                    disabled = false,
                    icon = { RadioButton(selected = curr == it, onClick = { set(it)  }) }
                ) { }
            }
        }

        if (multilingualShouldBeShown) {
            val unsupportedLanguages = languages.map { getLanguageFromWhisperString(it) }.filter { it !in multilingualModel.caps.supportedLanguages }
            if(unsupportedLanguages.isNotEmpty()) {
                Tip(stringResource(
                    R.string.language_settings_model_doesnt_support_languages,
                    stringResource(multilingualModel.name),
                    unsupportedLanguages.joinToString(", ")
                ), TipKind.Error)
            }

            Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = RoundedCornerShape(24.dp), modifier = Modifier.padding(16.dp)) {
                Column(Modifier.padding(0.dp, 8.dp)) {
                    Text(stringResource(R.string.multilingual_model), style = Typography.Heading.Medium,
                        modifier = Modifier.align(Alignment.CenterHorizontally).padding(0.dp, 16.dp))

                    val sortedModels = ALL_MODELS.filter { it.caps.supportedLanguages.size > 1 }
                        .sortedBy { it.caps.paramCount }

                    modelItems(
                        R.string.streaming_models, models[null]!!, { if(it != null) updateModelOptions("multi", it) },
                        sortedModels.filter { it.caps.supportsStreaming })

                    modelItems(
                        R.string.nonstreaming_models, models[null]!!, { if(it != null) updateModelOptions("multi", it) },
                        sortedModels.filter { !it.caps.supportsStreaming })
                }
            }
            Spacer(Modifier.height(16.dp))
        }


        if(languages.contains("en")) {
            Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = RoundedCornerShape(24.dp), modifier = Modifier.padding(16.dp)) {
                Column(Modifier.padding(0.dp, 8.dp)) {
                    Text(stringResource(R.string.english_model), style = Typography.Heading.Medium,
                        modifier = Modifier.align(Alignment.CenterHorizontally).padding(0.dp, 16.dp))

                    val sortedModels = ALL_MODELS.filter { it.caps.supportedLanguages == setOf(Language.English) }
                        .sortedBy { it.caps.paramCount }

                    modelItems(
                        R.string.streaming_models_recommended, models[Language.English], { updateModelOptions(Language.English.toWhisperString(), it) },
                        sortedModels.filter { it.caps.supportsStreaming && !it.caps.banishedToOther })

                    modelItems(
                        R.string.nonstreaming_models, models[Language.English], { updateModelOptions(Language.English.toWhisperString(), it) },
                        sortedModels.filter { !it.caps.supportsStreaming && !it.caps.banishedToOther })

                    modelItems(
                        R.string.other_models, models[Language.English], { updateModelOptions(Language.English.toWhisperString(), it) },
                        sortedModels.filter { it.caps.banishedToOther } + listOf(null))
                }
            }

            Spacer(Modifier.height(16.dp))
        }


        if(languages.contains("es")) {
            Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = RoundedCornerShape(24.dp), modifier = Modifier.padding(16.dp)) {
                Column(Modifier.padding(0.dp, 8.dp)) {
                    Text(stringResource(R.string.spanish_model), style = Typography.Heading.Medium,
                        modifier = Modifier.align(Alignment.CenterHorizontally).padding(0.dp, 16.dp))

                    val sortedModels = ALL_MODELS.filter { it.caps.supportedLanguages == setOf(Language.Spanish) }
                        .sortedBy { it.caps.paramCount }

                    modelItems(
                        R.string.all_models, models[Language.Spanish], { updateModelOptions(Language.Spanish.toWhisperString(), it) },
                        sortedModels + listOf(null))
                }
            }

            Spacer(Modifier.height(16.dp))
        }

        Tip(stringResource(R.string.parameter_count_tip), TipKind.Info)
    }
}
