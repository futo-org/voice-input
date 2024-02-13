package org.futo.voiceinput.settings.pages

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import org.futo.voiceinput.ENGLISH_MODELS
import org.futo.voiceinput.MULTILINGUAL_MODELS
import org.futo.voiceinput.R
import org.futo.voiceinput.settings.ENABLE_MULTILINGUAL
import org.futo.voiceinput.settings.ENGLISH_MODEL_INDEX
import org.futo.voiceinput.settings.LANGUAGE_TOGGLES
import org.futo.voiceinput.settings.MULTILINGUAL_MODEL_INDEX
import org.futo.voiceinput.settings.ScreenTitle
import org.futo.voiceinput.settings.ScrollableList
import org.futo.voiceinput.settings.SettingRadio
import org.futo.voiceinput.settings.SettingsViewModel
import org.futo.voiceinput.settings.Tip
import org.futo.voiceinput.settings.USE_LANGUAGE_SPECIFIC_MODELS
import org.futo.voiceinput.settings.useDataStore
import org.futo.voiceinput.startModelDownloadActivity

@Composable
fun modelsSubtitle(): String? {
    val (languages, _) = useDataStore(LANGUAGE_TOGGLES)
    val (useLanguageSpecificModels, _) = useDataStore(USE_LANGUAGE_SPECIFIC_MODELS)

    val (multilingual, _) = useDataStore(ENABLE_MULTILINGUAL)

    val (englishIdxActual, _) = useDataStore(ENGLISH_MODEL_INDEX)
    val (multilingualIdxActual, _) = useDataStore(MULTILINGUAL_MODEL_INDEX)

    // It doesn't matter what the multilingual model is set to if multilingual is disabled, the model
    // isn't used anyway. So suppress any text about its value by pretending it's default
    val multilingualIdx =
        if (multilingual) multilingualIdxActual else MULTILINGUAL_MODEL_INDEX.default

    val englishIdx = if((!multilingual) || (languages.contains("en") && useLanguageSpecificModels)) {
        englishIdxActual
    } else {
        ENGLISH_MODEL_INDEX.default
    }

    val totalDiff =
        (englishIdx - ENGLISH_MODEL_INDEX.default) + (multilingualIdx - MULTILINGUAL_MODEL_INDEX.default)
    val usePlural =
        ((englishIdx != ENGLISH_MODEL_INDEX.default) && (multilingualIdx != MULTILINGUAL_MODEL_INDEX.default))
    return if (totalDiff < 0) {
        if (usePlural) {
            stringResource(R.string.using_smaller_models_accuracy_may_be_worse)
        } else {
            stringResource(R.string.using_smaller_model_accuracy_may_be_worse)
        }
    } else if (totalDiff > 0) {
        if (usePlural) {
            stringResource(R.string.using_larger_models_speed_may_be_slower)
        } else {
            stringResource(R.string.using_larger_model_speed_may_be_slower)
        }
    } else if ((englishIdx != ENGLISH_MODEL_INDEX.default) || (multilingualIdx != MULTILINGUAL_MODEL_INDEX.default)) {
        if (usePlural) {
            stringResource(R.string.using_non_default_models)
        } else {
            stringResource(R.string.using_non_default_model)
        }
    } else {
        null
    }
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
                )
            )
        } else {
            context.startModelDownloadActivity(listOf(ENGLISH_MODELS[englishModelIndex.value]))
        }
    }

    LaunchedEffect(listOf(useMultilingual, englishModelIndex.value, multilingualModelIndex.value)) {
        launchDownloaderIfNecessary()
    }

    ScrollableList {
        ScreenTitle(stringResource(R.string.model_picker), showBack = true, navController = navController)

        Tip(stringResource(R.string.parameter_count_tip))

        if (useMultilingual) {
            SettingRadio(
                stringResource(R.string.multilingual_model),
                MULTILINGUAL_MODELS.indices.toList(),
                MULTILINGUAL_MODELS.map { it.name },
                MULTILINGUAL_MODEL_INDEX
            )
        }

        if((!useMultilingual) || (languages.contains("en") && useLanguageSpecificModels)) {
            SettingRadio(
                stringResource(R.string.english_model),
                ENGLISH_MODELS.indices.toList(),
                ENGLISH_MODELS.map { it.name },
                ENGLISH_MODEL_INDEX
            )
        }
    }
}
