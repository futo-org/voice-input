package org.futo.voiceinput.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import org.futo.voiceinput.DISALLOW_SYMBOLS
import org.futo.voiceinput.FORCE_SHOW_NOTICE
import org.futo.voiceinput.IS_ALREADY_PAID
import org.futo.voiceinput.Screen
import org.futo.voiceinput.VERBOSE_PROGRESS
import org.futo.voiceinput.ui.theme.Typography
import org.futo.voiceinput.BuildConfig

@Composable
@Preview
fun AdvancedScreen(settingsViewModel: SettingsViewModel = viewModel(), navController: NavHostController = rememberNavController()) {
    Screen("Advanced Settings") {
        SettingList {
            SettingToggle(
                "Suppress non-speech annotations",
                DISALLOW_SYMBOLS,
                default = true,
                subtitle = "[cough], [music], etc"
            )
            SettingToggle(
                "Verbose Mode",
                VERBOSE_PROGRESS,
                default = false
            )
            SettingItem(title = "Testing Menu", onClick = { navController.navigate("testing") }) {
                Icon(Icons.Default.ArrowForward, contentDescription = "Go")
            }

            if(BuildConfig.FLAVOR == "dev") {
                Text("Payment testing", style = Typography.labelLarge)
                SettingToggle(
                    "Force show payment notice",
                    FORCE_SHOW_NOTICE,
                    default = false
                )
                SettingToggle(
                    "Is paid?",
                    IS_ALREADY_PAID,
                    default = false
                )
            }

        }
    }
}
