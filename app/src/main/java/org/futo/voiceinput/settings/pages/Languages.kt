package org.futo.voiceinput.settings.pages

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.Job
import org.futo.voiceinput.LANGUAGE_LIST
import org.futo.voiceinput.R
import org.futo.voiceinput.parseModelOptions
import org.futo.voiceinput.settings.ALLOW_UNDERTRAINED_LANGUAGES
import org.futo.voiceinput.settings.LANGUAGE_TOGGLES
import org.futo.voiceinput.settings.MODEL_OPTIONS
import org.futo.voiceinput.settings.ScreenTitle
import org.futo.voiceinput.settings.SettingToggleRaw
import org.futo.voiceinput.settings.SettingsRootListLazy
import org.futo.voiceinput.settings.SettingsViewModel
import org.futo.voiceinput.settings.Tip
import org.futo.voiceinput.settings.TipKind
import org.futo.voiceinput.settings.useDataStore
import org.futo.voiceinput.shared.types.getLanguageFromWhisperString


@Composable
fun LanguageToggle(
    id: String,
    name: String,
    languages: Set<String>,
    setLanguages: (Set<String>) -> Job,
    subtitle: String?,
    disabled: Boolean
) {
    val disabled = disabled || (languages.contains(id) && languages.size == 1)

    SettingToggleRaw(
        name,
        languages.contains(id),
        {
            setLanguages((languages.filter { it != id } + if (it) {
                listOf(id)
            } else {
                listOf()
            }).toSet())
        },
        subtitle = if(disabled) { stringResource(R.string.only_language_enabled) } else { subtitle },
        disabled = disabled
    )
}

@Composable
@Preview
fun LanguagesScreen(
    settingsViewModel: SettingsViewModel = viewModel(),
    navController: NavHostController = rememberNavController()
) {
    val (languages, setLanguages) = useDataStore(LANGUAGE_TOGGLES)
    val context = LocalContext.current

    val (allowUndertrainedLanguages, _) = useDataStore(ALLOW_UNDERTRAINED_LANGUAGES)

    val modelOptions = useDataStore(MODEL_OPTIONS)
    val model = remember(modelOptions.value) {
        modelOptions.value.filter { it.startsWith("multi:") }.parseModelOptions()[0].second
    }

    SettingsRootListLazy {
        item {
            ScreenTitle(stringResource(R.string.languages_title), showBack = true, navController = navController)
        }

        item {
            Tip(stringResource(R.string.language_tip_1), TipKind.Info)

            val unsupportedLanguages = languages.map { getLanguageFromWhisperString(it) }.filter { it !in model.caps.supportedLanguages }

            if(unsupportedLanguages.isNotEmpty()) {
                Tip(
                    stringResource(
                        R.string.language_settings_model_doesnt_support_languages,
                        stringResource(model.name),
                        unsupportedLanguages.joinToString(", ")
                    ),
                    TipKind.Error
                )
            }
        }

        items(LANGUAGE_LIST.size) {
            val language = LANGUAGE_LIST[it]

            if(allowUndertrainedLanguages && it > 0 && model.caps.isWhisper) {
                if (language.trainedHourCount < 1000 && LANGUAGE_LIST[it - 1].trainedHourCount >= 1000) {
                    Spacer(modifier = Modifier.height(48.dp))
                    Tip(stringResource(R.string.language_unsupported_warning_1000), TipKind.Info)
                } else if (language.trainedHourCount < 100 && LANGUAGE_LIST[it - 1].trainedHourCount >= 100) {
                    Spacer(modifier = Modifier.height(48.dp))
                    Tip(stringResource(R.string.language_unsupported_warning_100), TipKind.Info)
                }
            }

            val modelSupportsLanguage = getLanguageFromWhisperString(language.id) in model.caps.supportedLanguages
            val subtitle = when {
                 !modelSupportsLanguage ->
                     stringResource(R.string.language_settings_language_unsupported_by_model, stringResource(model.name))

                !model.caps.isWhisper && model.caps.unrecommendedLanguages.contains(getLanguageFromWhisperString(language.id))
                        && languages.contains(language.id) ->
                    stringResource(R.string.whisper_recommended_subtitle)

                !model.caps.isWhisper ->
                    ""

                language.trainedHourCount < 1000 ->
                    stringResource(R.string.may_be_low_accuracy_x_hours, language.trainedHourCount)

                else ->
                    stringResource(R.string.trained_on_x_hours, language.trainedHourCount)
            }

            if(!modelSupportsLanguage && !languages.contains(language.id)) return@items
            if(model.caps.isWhisper && !allowUndertrainedLanguages && language.trainedHourCount <= 1000) return@items

            LanguageToggle(language.id, language.name, languages, setLanguages, subtitle, false)
        }
    }
}
