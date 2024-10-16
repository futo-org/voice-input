package org.futo.voiceinput.settings.pages

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import org.futo.voiceinput.MULTILINGUAL_MODELS
import org.futo.voiceinput.R
import org.futo.voiceinput.settings.ALLOW_UNDERTRAINED_LANGUAGES
import org.futo.voiceinput.settings.ENABLE_MULTILINGUAL
import org.futo.voiceinput.settings.LANGUAGE_TOGGLES
import org.futo.voiceinput.settings.MULTILINGUAL_MODEL_INDEX
import org.futo.voiceinput.settings.ScreenTitle
import org.futo.voiceinput.settings.SettingListLazy
import org.futo.voiceinput.settings.SettingToggleRaw
import org.futo.voiceinput.settings.SettingsViewModel
import org.futo.voiceinput.settings.Tip
import org.futo.voiceinput.settings.useDataStore
import org.futo.voiceinput.startModelDownloadActivity


@Composable
fun LanguageToggle(
    id: String,
    name: String,
    languages: Set<String>,
    setLanguages: (Set<String>) -> Job,
    subtitle: String?
) {
    val disabled = languages.contains(id) && languages.size == 1

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
    val (multilingual, setMultilingual) = useDataStore(ENABLE_MULTILINGUAL)
    val (multilingualModelIndex, _) = useDataStore(MULTILINGUAL_MODEL_INDEX)
    val (languages, setLanguages) = useDataStore(LANGUAGE_TOGGLES)
    val context = LocalContext.current

    val (allowUndertrainedLanguages, _) = useDataStore(ALLOW_UNDERTRAINED_LANGUAGES)


    LaunchedEffect(listOf(multilingualModelIndex, multilingual)) {
        if (multilingual) {
            context.startModelDownloadActivity(listOf(MULTILINGUAL_MODELS[multilingualModelIndex]))
        }
    }

    LaunchedEffect(languages) {
        val newMultilingual = languages.count { it != "en" } > 0
        if (multilingual != newMultilingual) setMultilingual(newMultilingual)
    }

    SettingListLazy {
        item {
            ScreenTitle(stringResource(R.string.languages_title), showBack = true, navController = navController)
        }

        item {
            Tip(stringResource(R.string.language_tip_1))
            Tip(stringResource(R.string.language_tip_2))
            Tip(stringResource(R.string.language_tip_3))
        }

        items(LANGUAGE_LIST.size) {
            val language = LANGUAGE_LIST[it]

            if(allowUndertrainedLanguages && it > 0) {
                if (language.trainedHourCount < 1000 && LANGUAGE_LIST[it - 1].trainedHourCount >= 1000) {
                    Spacer(modifier = Modifier.height(48.dp))
                    Tip(stringResource(R.string.language_unsupported_warning_1000))
                } else if (language.trainedHourCount < 100 && LANGUAGE_LIST[it - 1].trainedHourCount >= 100) {
                    Spacer(modifier = Modifier.height(48.dp))
                    Tip(stringResource(R.string.language_unsupported_warning_100))
                }
            }

            val subtitle = if (language.trainedHourCount < 1000) {
                stringResource(R.string.may_be_low_accuracy_x_hours, language.trainedHourCount)
            } else {
                stringResource(R.string.trained_on_x_hours, language.trainedHourCount)
            }

            // Only show languages trained with over 1000 hours for now, as anything lower
            // can be laughably bad on the tiny model
            if (allowUndertrainedLanguages || language.trainedHourCount > 1000) {
                LanguageToggle(language.id, language.name, languages, setLanguages, subtitle)
            }
        }
    }
}
