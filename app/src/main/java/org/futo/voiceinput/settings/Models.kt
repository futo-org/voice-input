package org.futo.voiceinput.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.Preferences
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import org.futo.voiceinput.ENABLE_MULTILINGUAL
import org.futo.voiceinput.ENGLISH_MODELS
import org.futo.voiceinput.ENGLISH_MODEL_INDEX
import org.futo.voiceinput.ENGLISH_MODEL_INDEX_DEFAULT
import org.futo.voiceinput.MULTILINGUAL_MODELS
import org.futo.voiceinput.MULTILINGUAL_MODEL_INDEX
import org.futo.voiceinput.MULTILINGUAL_MODEL_INDEX_DEFAULT
import org.futo.voiceinput.ModelData
import org.futo.voiceinput.R
import org.futo.voiceinput.Screen
import org.futo.voiceinput.startModelDownloadActivity
import org.futo.voiceinput.LANGUAGE_TOGGLES
import org.futo.voiceinput.USE_LANGUAGE_SPECIFIC_MODELS


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelSizeItem(label: String, options: List<ModelData>, key: Preferences.Key<Int>, default: Int) {
    val (modelIndex, setModelIndex) = useDataStore(key = key, default = default)

    var expanded by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp)
    ) {
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = {
                expanded = !expanded
            },
            modifier = Modifier.align(Alignment.Center)
        ) {
            TextField(
                readOnly = true,
                value = options[modelIndex].name,
                onValueChange = { },
                label = { Text(label) },
                trailingIcon = {
                    ExposedDropdownMenuDefaults.TrailingIcon(
                        expanded = expanded
                    )
                },
                colors = ExposedDropdownMenuDefaults.textFieldColors(
                    focusedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    focusedLeadingIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    focusedIndicatorColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    focusedTrailingIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
                modifier = Modifier.menuAnchor()
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = {
                    expanded = false
                }
            ) {
                options.forEachIndexed { i, selectionOption ->
                    DropdownMenuItem(
                        text = {
                            Text(selectionOption.name)
                        },
                        onClick = {
                            setModelIndex(i)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}


@Composable
fun modelsSubtitle(): String? {
    val (languages, _) = useDataStore(key = LANGUAGE_TOGGLES, default = setOf("en"))
    val (useLanguageSpecificModels, _) = useDataStore(key = USE_LANGUAGE_SPECIFIC_MODELS, default = true)

    val (multilingual, _) = useDataStore(key = ENABLE_MULTILINGUAL, default = false)

    val (englishIdxActual, _) = useDataStore(
        key = ENGLISH_MODEL_INDEX,
        default = ENGLISH_MODEL_INDEX_DEFAULT
    )
    val (multilingualIdxActual, _) = useDataStore(
        key = MULTILINGUAL_MODEL_INDEX,
        default = MULTILINGUAL_MODEL_INDEX_DEFAULT
    )

    // It doesn't matter what the multilingual model is set to if multilingual is disabled, the model
    // isn't used anyway. So suppress any text about its value by pretending it's default
    val multilingualIdx =
        if (multilingual) multilingualIdxActual else MULTILINGUAL_MODEL_INDEX_DEFAULT

    val englishIdx = if((!multilingual) || (languages.contains("en") && useLanguageSpecificModels)) {
        englishIdxActual
    } else {
        ENGLISH_MODEL_INDEX_DEFAULT
    }

    val totalDiff =
        (englishIdx - ENGLISH_MODEL_INDEX_DEFAULT) + (multilingualIdx - MULTILINGUAL_MODEL_INDEX_DEFAULT)
    val usePlural =
        ((englishIdx != ENGLISH_MODEL_INDEX_DEFAULT) && (multilingualIdx != MULTILINGUAL_MODEL_INDEX_DEFAULT))
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
    } else if ((englishIdx != ENGLISH_MODEL_INDEX_DEFAULT) || (multilingualIdx != MULTILINGUAL_MODEL_INDEX_DEFAULT)) {
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
    val (useMultilingual, _) = useDataStore(key = ENABLE_MULTILINGUAL, default = false)

    val (englishModelIndex, _) = useDataStore(
        key = ENGLISH_MODEL_INDEX,
        default = ENGLISH_MODEL_INDEX_DEFAULT
    )
    val (multilingualModelIndex, _) = useDataStore(
        key = MULTILINGUAL_MODEL_INDEX,
        default = MULTILINGUAL_MODEL_INDEX_DEFAULT
    )

    val (languages, _) = useDataStore(key = LANGUAGE_TOGGLES, default = setOf("en"))
    val (useLanguageSpecificModels, _) = useDataStore(key = USE_LANGUAGE_SPECIFIC_MODELS, default = true)

    val context = LocalContext.current
    LaunchedEffect(
        listOf(
            if (useMultilingual) 1 else 0,
            englishModelIndex,
            multilingualModelIndex
        )
    ) {
        if (useMultilingual) {
            context.startModelDownloadActivity(
                listOf(
                    ENGLISH_MODELS[englishModelIndex],
                    MULTILINGUAL_MODELS[multilingualModelIndex]
                )
            )
        } else {
            context.startModelDownloadActivity(listOf(ENGLISH_MODELS[englishModelIndex]))
        }
    }

    Screen(stringResource(R.string.model_picker)) {
        ScrollableList {
            Tip(stringResource(R.string.parameter_count_tip))

            if((!useMultilingual) || (languages.contains("en") && useLanguageSpecificModels)) {
                Spacer(modifier = Modifier.height(16.dp))
                ModelSizeItem(
                    stringResource(R.string.english_model),
                    ENGLISH_MODELS,
                    ENGLISH_MODEL_INDEX,
                    ENGLISH_MODEL_INDEX_DEFAULT
                )
            }

            if (useMultilingual) {
                Spacer(modifier = Modifier.height(16.dp))
                ModelSizeItem(
                    stringResource(R.string.multilingual_model),
                    MULTILINGUAL_MODELS,
                    MULTILINGUAL_MODEL_INDEX,
                    MULTILINGUAL_MODEL_INDEX_DEFAULT
                )
            }
        }
    }
}
