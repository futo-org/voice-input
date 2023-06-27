package org.futo.voiceinput.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import org.futo.voiceinput.ENABLE_MULTILINGUAL
import org.futo.voiceinput.ENABLE_SOUND


@Composable
@Preview
fun HomeScreen(settingsViewModel: SettingsViewModel = viewModel(), navController: NavHostController = rememberNavController()) {
    val (multilingual, _) = useDataStore(key = ENABLE_MULTILINGUAL, default = false)
    val multilingualSubtitle = if(multilingual) {
        "Multilingual enabled, English latency will be increased"
    } else {
        null
    }

    SettingsScreen("Settings") {
        SettingList {
            SettingItem(title = "Help / Info", onClick = { navController.navigate("help") }) {
                Icon(Icons.Default.ArrowForward, contentDescription = "Go")
            }
            SettingToggle(
                "Sounds",
                ENABLE_SOUND,
                default = true,
                subtitle = "Play sound when recognition starts/cancels"
            )
            SettingItem(title = "Languages", onClick = { navController.navigate("languages") }, subtitle = multilingualSubtitle) {
                Icon(Icons.Default.ArrowForward, contentDescription = "Go")
            }
            SettingItem(title = "Credits and Acknowledgments", onClick = { navController.navigate("credits") }) {
                Icon(Icons.Default.ArrowForward, contentDescription = "Go")
            }
            SettingItem(title = "Advanced", onClick = { navController.navigate("advanced") }) {
                Icon(Icons.Default.ArrowForward, contentDescription = "Go")
            }
        }
    }
}
