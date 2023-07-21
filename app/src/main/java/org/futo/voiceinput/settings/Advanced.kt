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
import org.futo.voiceinput.BuildConfig
import org.futo.voiceinput.DISALLOW_SYMBOLS
import org.futo.voiceinput.FORCE_SHOW_NOTICE
import org.futo.voiceinput.IS_ALREADY_PAID
import org.futo.voiceinput.NOTICE_REMINDER_TIME
import org.futo.voiceinput.Screen
import org.futo.voiceinput.VERBOSE_PROGRESS
import org.futo.voiceinput.ui.theme.Typography

@Composable
@Preview
fun AdvancedScreen(settingsViewModel: SettingsViewModel = viewModel(), navController: NavHostController = rememberNavController()) {
    Screen("Advanced Settings") {
        ScrollableList {
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

            @Suppress("KotlinConstantConditions")
            if(BuildConfig.FLAVOR == "dev") {
                val daysInstalled = useNumberOfDaysInstalled()
                Text("Payment testing [Developer Build only]", style = Typography.labelLarge)
                SettingToggle(
                    "Show payment notice despite not being past $TRIAL_PERIOD_DAYS days",
                    FORCE_SHOW_NOTICE,
                    subtitle = "You are currently at ${daysInstalled.value} days",
                    default = false
                )
                SettingToggle(
                    "Is paid?",
                    IS_ALREADY_PAID,
                    default = false
                )

                val reminder = useDataStore(NOTICE_REMINDER_TIME, default = 0L)
                val currTime = System.currentTimeMillis() / 1000L

                val subtitleValue = if(reminder.value > currTime) {
                    val diffDays = (reminder.value - currTime) / 60.0 / 60.0 / 24.0
                    "Reminding in ${"%.2f".format(diffDays)} days"
                } else { "Reminder unset" }
                SettingToggleRaw(
                    "Reminder Time",
                    reminder.value > currTime,
                    { if(!it) { reminder.setValue(0L) } },
                    subtitleValue,
                    reminder.value <= currTime,
                    { }
                )
            }

        }
    }
}
