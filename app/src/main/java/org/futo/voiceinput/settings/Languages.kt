package org.futo.voiceinput.settings

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.Job
import org.futo.voiceinput.ENABLE_MULTILINGUAL
import org.futo.voiceinput.LANGUAGE_LIST
import org.futo.voiceinput.LANGUAGE_TOGGLES
import org.futo.voiceinput.MULTILINGUAL_MODEL_DATA
import org.futo.voiceinput.modelNeedsDownloading
import org.futo.voiceinput.startModelDownloadActivity


@Composable
fun LanguageToggle(id: String, name: String, languages: Set<String>, setLanguages: (Set<String>) -> Job, subtitle: String?) {
    SettingToggleRaw(
        name,
        languages.contains(id),
        { setLanguages( (languages.filter{ it != id} + if(it) { listOf(id) } else { listOf() } ).toSet() ) },
        subtitle = subtitle
    )
}

@Composable
@Preview
fun LanguagesScreen(settingsViewModel: SettingsViewModel = viewModel(), navController: NavHostController = rememberNavController()) {
    val (multilingual, setMultilingual) = useDataStore(key = ENABLE_MULTILINGUAL, default = false)
    val (languages, setLanguages) = useDataStore(key = LANGUAGE_TOGGLES, default = setOf("en"))
    val context = LocalContext.current

    LaunchedEffect(multilingual) {
        if(multilingual && context.modelNeedsDownloading(MULTILINGUAL_MODEL_DATA)) {
            context.startModelDownloadActivity(listOf(MULTILINGUAL_MODEL_DATA))
        }
    }

    LaunchedEffect(languages) {
        val newMultilingual = languages.count { it != "en" } > 0
        if(multilingual != newMultilingual) setMultilingual(newMultilingual)
    }

    SettingsScreen("Languages") {
        SettingList {
            LazyColumn {
                item {
                    Tip("The model will automatically detect which language you're speaking.")
                    Tip("Some languages may work better than others, depending on the amount of training data.")
                    Tip("Voice Input may be slower if you enable more than English.")
                }
                item {
                    SettingToggleRaw(
                        "English",
                        true,
                        {},
                        disabled = true,
                        subtitle = "Always on. Enabling others will increase English latency"
                    )
                }

                items(LANGUAGE_LIST.size) {
                    val language = LANGUAGE_LIST[it]

                    val subtitle = if(language.trainedHourCount < 500) {
                        "May be low accuracy (${language.trainedHourCount}h)"
                    } else {
                        "Trained on ${language.trainedHourCount} hours"
                    }

                    // Only show languages trained with over 1000 hours for now, as anything lower
                    // can be laughably bad on the tiny model
                    if(language.trainedHourCount > 1000) {
                        LanguageToggle(language.id, language.name, languages, setLanguages, subtitle)
                    }
                }
            }
        }
    }
}
