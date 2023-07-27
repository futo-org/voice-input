package org.futo.voiceinput.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.Job
import org.futo.voiceinput.ENABLE_MULTILINGUAL
import org.futo.voiceinput.LANGUAGE_LIST
import org.futo.voiceinput.LANGUAGE_TOGGLES
import org.futo.voiceinput.MULTILINGUAL_MODELS
import org.futo.voiceinput.MULTILINGUAL_MODEL_INDEX
import org.futo.voiceinput.MULTILINGUAL_MODEL_INDEX_DEFAULT
import org.futo.voiceinput.R
import org.futo.voiceinput.Screen
import org.futo.voiceinput.startModelDownloadActivity


@Composable
fun LanguageToggle(
    id: String,
    name: String,
    languages: Set<String>,
    setLanguages: (Set<String>) -> Job,
    subtitle: String?
) {
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
        subtitle = subtitle
    )
}

@Composable
@Preview
fun LanguagesScreen(
    settingsViewModel: SettingsViewModel = viewModel(),
    navController: NavHostController = rememberNavController()
) {
    val (multilingual, setMultilingual) = useDataStore(key = ENABLE_MULTILINGUAL, default = false)
    val (multilingualModelIndex, _) = useDataStore(
        key = MULTILINGUAL_MODEL_INDEX,
        default = MULTILINGUAL_MODEL_INDEX_DEFAULT
    )
    val (languages, setLanguages) = useDataStore(key = LANGUAGE_TOGGLES, default = setOf("en"))
    val context = LocalContext.current


    LaunchedEffect(listOf(multilingualModelIndex, multilingual)) {
        if (multilingual) {
            context.startModelDownloadActivity(listOf(MULTILINGUAL_MODELS[multilingualModelIndex]))
        }
    }

    LaunchedEffect(languages) {
        val newMultilingual = languages.count { it != "en" } > 0
        if (multilingual != newMultilingual) setMultilingual(newMultilingual)
    }

    Screen(stringResource(R.string.languages_title)) {
        SettingListLazy {
            item {
                Tip(stringResource(R.string.language_tip_1))
                Tip(stringResource(R.string.language_tip_2))
                Tip(stringResource(R.string.language_tip_3))
            }
            item {
                SettingToggleRaw(
                    stringResource(R.string.english),
                    true,
                    {},
                    disabled = true,
                    subtitle = stringResource(R.string.english_subtitle)
                )
            }

            items(LANGUAGE_LIST.size) {
                val language = LANGUAGE_LIST[it]

                val subtitle = if (language.trainedHourCount < 500) {
                    stringResource(R.string.may_be_low_accuracy_x_hours, language.trainedHourCount)
                } else {
                    stringResource(R.string.trained_on_x_hours, language.trainedHourCount)
                }

                // Only show languages trained with over 1000 hours for now, as anything lower
                // can be laughably bad on the tiny model
                if (language.trainedHourCount > 1000) {
                    LanguageToggle(language.id, language.name, languages, setLanguages, subtitle)
                }
            }
        }
    }
}
