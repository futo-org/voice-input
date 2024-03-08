package org.futo.voiceinput.settings.pages

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import org.futo.voiceinput.R
import org.futo.voiceinput.openURI
import org.futo.voiceinput.theme.Typography
import org.futo.voiceinput.BuildConfig
import org.futo.voiceinput.migration.ConditionalModelUpdate
import org.futo.voiceinput.settings.ConditionalUpdate
import org.futo.voiceinput.settings.ENABLE_MULTILINGUAL
import org.futo.voiceinput.settings.IS_ALREADY_PAID
import org.futo.voiceinput.settings.NavigationItem
import org.futo.voiceinput.settings.NavigationItemStyle
import org.futo.voiceinput.settings.ScreenTitle
import org.futo.voiceinput.settings.ScrollableList
import org.futo.voiceinput.settings.SettingItem
import org.futo.voiceinput.settings.SettingsViewModel
import org.futo.voiceinput.settings.useDataStore


@Composable
fun ShareFeedbackOption(title: String = stringResource(R.string.send_feedback)) {
    val context = LocalContext.current
    val mailUri = "mailto:${stringResource(R.string.support_email)}"

    val color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.75f)
    val icon = painterResource(id = R.drawable.mail)
    SettingItem(title = title, onClick = {
        context.openURI(mailUri)
    }, icon = {
        Canvas(modifier = Modifier.fillMaxSize()) {
            translate(
                left = this.size.width / 2.0f - icon.intrinsicSize.width / 2.0f,
                top = this.size.height / 2.0f - icon.intrinsicSize.height / 2.0f
            ) {
                with(icon) {
                    draw(icon.intrinsicSize, colorFilter = ColorFilter.tint(color))
                }
            }
        }
    }) {
        Icon(Icons.Default.Send, contentDescription = stringResource(R.string.go))
    }
}

@Composable
fun IssueTrackerOption(title: String = stringResource(R.string.issue_tracker)) {
    val context = LocalContext.current
    val mailUri = "https://github.com/futo-org/voice-input/issues"

    NavigationItem(
        title = title,
        style = NavigationItemStyle.Misc,
        navigate = { context.openURI(mailUri) },
        icon = painterResource(R.drawable.alert_circle)
    )
}

@Composable
fun SettingsSeparator(text: String) {
    Spacer(modifier = Modifier.height(16.dp))
    Text(text, style = Typography.labelMedium, modifier = Modifier.padding(16.dp, 0.dp))
}

@Composable
@Preview
fun HomeScreen(
    settingsViewModel: SettingsViewModel = viewModel(),
    navController: NavHostController = rememberNavController()
) {
    val isAlreadyPaid = useDataStore(IS_ALREADY_PAID)
    val isMultilingual = useDataStore(ENABLE_MULTILINGUAL)
    val multilingualSubtitle = if (isMultilingual.value) {
        stringResource(R.string.multilingual_enabled_english_will_be_slower)
    } else {
        null
    }

    ScrollableList {
        Spacer(modifier = Modifier.height(24.dp))
        ScreenTitle(stringResource(R.string.futo_voice_input_settings))
        Text(
            "v${BuildConfig.VERSION_NAME}",
            style = Typography.labelSmall,
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp, 4.dp),
            textAlign = TextAlign.End
        )


        ConditionalUnpaidNoticeWithNav(navController)
        ConditionalUpdate()
        ConditionalModelUpdate()

        SettingsSeparator(stringResource(R.string.options))
        NavigationItem(
            title = stringResource(R.string.languages),
            subtitle = multilingualSubtitle,
            style = NavigationItemStyle.Misc,
            navigate = { navController.navigate("languages") },
            icon = painterResource(R.drawable.globe)
        )

        NavigationItem(
            title = stringResource(R.string.model),
            subtitle = modelsSubtitle(),
            style = NavigationItemStyle.Misc,
            navigate = { navController.navigate("models") },
            icon = painterResource(R.drawable.cpu)
        )

        NavigationItem(
            title = stringResource(R.string.input_options),
            style = NavigationItemStyle.Misc,
            navigate = { navController.navigate("input") },
            icon = painterResource(R.drawable.shift)
        )

        SettingsSeparator(stringResource(R.string.miscellaneous))
        NavigationItem(
            title = stringResource(R.string.theme),
            style = NavigationItemStyle.Misc,
            navigate = { navController.navigate("themes") },
            icon = painterResource(R.drawable.eye)
        )

        NavigationItem(
            title = stringResource(R.string.testing_menu),
            subtitle = stringResource(R.string.try_out_voice_input),
            style = NavigationItemStyle.Misc,
            navigate = { navController.navigate("testing") },
            icon = painterResource(R.drawable.edit)
        )

        UnpaidNoticeCondition(showOnlyIfReminder = true) {
            NavigationItem(
                title = stringResource(R.string.payment),
                style = NavigationItemStyle.Misc,
                navigate = { navController.navigate("pleasePay") },
                icon = painterResource(R.drawable.dollar_sign)
            )
        }

        NavigationItem(
            title = stringResource(R.string.advanced),
            style = NavigationItemStyle.Misc,
            navigate = { navController.navigate("advanced") },
            icon = painterResource(R.drawable.code)
        )

        SettingsSeparator(stringResource(R.string.about))
        NavigationItem(
            title = stringResource(R.string.help),
            style = NavigationItemStyle.Misc,
            navigate = { navController.navigate("help") },
            icon = painterResource(R.drawable.help_circle)
        )

        NavigationItem(
            title = stringResource(R.string.credits),
            style = NavigationItemStyle.Misc,
            navigate = { navController.navigate("credits") },
            icon = painterResource(R.drawable.users)
        )
        ShareFeedbackOption()
        IssueTrackerOption()

        Spacer(modifier = Modifier.height(32.dp))

        if (isAlreadyPaid.value) {
            Text(
                stringResource(R.string.thank_you_for_using_the_paid_version_of_futo_voice_input),
                style = Typography.bodyMedium,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
        }
        Spacer(modifier = Modifier.height(32.dp))
    }
}
