package org.futo.voiceinput.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import org.futo.voiceinput.ENABLE_MULTILINGUAL
import org.futo.voiceinput.ENABLE_SOUND
import org.futo.voiceinput.ENGLISH_MODEL_INDEX
import org.futo.voiceinput.ENGLISH_MODEL_INDEX_DEFAULT
import org.futo.voiceinput.MULTILINGUAL_MODEL_INDEX
import org.futo.voiceinput.MULTILINGUAL_MODEL_INDEX_DEFAULT
import org.futo.voiceinput.R
import org.futo.voiceinput.Screen


@Composable
@Preview
fun HomeScreen(settingsViewModel: SettingsViewModel = viewModel(), navController: NavHostController = rememberNavController()) {
    val (multilingual, _) = useDataStore(key = ENABLE_MULTILINGUAL, default = false)
    val multilingualSubtitle = if(multilingual) {
        "Multilingual enabled, English will be slower"
    } else {
        null
    }


    val (englishIdx, _) = useDataStore(key = ENGLISH_MODEL_INDEX, default = ENGLISH_MODEL_INDEX_DEFAULT)
    val (multilingualIdxActual, _) = useDataStore(key = MULTILINGUAL_MODEL_INDEX, default = MULTILINGUAL_MODEL_INDEX_DEFAULT)

    // It doesn't matter what the multilingual model is set to if multilingual is disabled, the model
    // isn't used anyway. So suppress any text about its value by pretending it's default
    val multilingualIdx = if(multilingual) multilingualIdxActual else MULTILINGUAL_MODEL_INDEX_DEFAULT

    val totalDiff =
        (englishIdx - ENGLISH_MODEL_INDEX_DEFAULT) + (multilingualIdx - MULTILINGUAL_MODEL_INDEX_DEFAULT)
    val modelPlural =
        if ((englishIdx != ENGLISH_MODEL_INDEX_DEFAULT) && (multilingualIdx != MULTILINGUAL_MODEL_INDEX_DEFAULT)) {
            "models"
        } else {
            "model"
        }
    val modelSubtitle = if (totalDiff < 0) {
        "Using smaller $modelPlural, accuracy may be worse"
    } else if (totalDiff > 0) {
        "Using larger $modelPlural, speed may be slower"
    } else if ((englishIdx != ENGLISH_MODEL_INDEX_DEFAULT) || (multilingualIdx != MULTILINGUAL_MODEL_INDEX_DEFAULT)) {
        "Using non-default $modelPlural"
    } else {
        null
    }

    Screen("${stringResource(R.string.app_name)} Settings") {
        ScrollableList {
            ConditionalUnpaidNoticeWithNav(navController)
            SettingItem(title = "Help", onClick = { navController.navigate("help") }) {
                Icon(Icons.Default.ArrowForward, contentDescription = "Go")
            }
            SettingItem(title = "Languages", onClick = { navController.navigate("languages") }, subtitle = multilingualSubtitle) {
                Icon(Icons.Default.ArrowForward, contentDescription = "Go")
            }
            SettingItem(title = "Models", onClick = { navController.navigate("models") }, subtitle = modelSubtitle) {
                Icon(Icons.Default.ArrowForward, contentDescription = "Go")
            }
            SettingToggle(
                "Sounds",
                ENABLE_SOUND,
                default = true,
                subtitle = "Will play a sound when started / cancelled",
                disabledSubtitle = "Will not play sounds when started / cancelled"
            )
            SettingItem(title = "Advanced", onClick = { navController.navigate("advanced") }) {
                Icon(Icons.Default.ArrowForward, contentDescription = "Go")
            }
            SettingItem(title = "Credits", onClick = { navController.navigate("credits") }) {
                Icon(Icons.Default.ArrowForward, contentDescription = "Go")
            }
        }
    }
}
