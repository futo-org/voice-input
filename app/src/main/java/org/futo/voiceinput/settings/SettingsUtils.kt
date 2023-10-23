package org.futo.voiceinput.settings

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.Preferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavOptions
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.futo.voiceinput.HAS_SEEN_PAID_NOTICE
import org.futo.voiceinput.IS_ALREADY_PAID
import org.futo.voiceinput.R
import org.futo.voiceinput.Status
import org.futo.voiceinput.payments.BillingManager
import org.futo.voiceinput.ui.theme.Typography
import java.lang.Exception


data class SettingsUiState(
    val intentResultText: String = "...",
    val numberOfResumes: Int = 0
)

class SettingsViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    fun onResume() {
        _uiState.update { currentState ->
            currentState.copy(
                numberOfResumes = currentState.numberOfResumes + 1
            )
        }
    }

    fun onIntentResult(result: String) {
        _uiState.update { currentState ->
            currentState.copy(
                intentResultText = result
            )
        }
    }
}

fun Context.openSystemDefaultsSettings(component: ComponentName) {
    val uri = Uri.fromParts("package", component.packageName, null)

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        // Try the new intent, otherwise fall back to application details settings
        try {
            startActivity(
                Intent(Settings.ACTION_APP_OPEN_BY_DEFAULT_SETTINGS).apply {
                    data = uri
                }
            )

            return
        } catch(e: ActivityNotFoundException) {
            // pass
            println("Failed to open ACTION_APP_OPEN_BY_DEFAULT_SETTINGS")
        }
    }

    try {
        startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = uri
        })
    }catch(e: ActivityNotFoundException) {
        println("Failed to open ACTION_APPLICATION_DETAILS_SETTINGS")
    }
}


@Composable
@Preview
fun Tip(text: String = "This is an example tip") {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp), shape = RoundedCornerShape(4.dp)
    ) {
        Text(
            text,
            modifier = Modifier.padding(8.dp),
            style = Typography.bodyMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
    }
}


@Composable
fun SettingItem(
    title: String,
    subtitle: String? = null,
    onClick: () -> Unit,
    icon: (@Composable () -> Unit)? = null,
    disabled: Boolean = false,
    content: @Composable () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(0.dp, 68.dp)
            .clickable(enabled = !disabled, onClick = {
                if (!disabled) {
                    onClick()
                }
            })
            .padding(0.dp, 4.dp, 8.dp, 4.dp)
    ) {
        Column(
            modifier = Modifier
                .width(42.dp)
                .align(Alignment.CenterVertically)
        ) {
            Box(modifier = Modifier.align(Alignment.CenterHorizontally)) {
                if (icon != null) {
                    icon()
                }
            }
        }
        Row(
            modifier = Modifier
                .weight(1f)
                .align(Alignment.CenterVertically)
                .alpha(
                    if (disabled) {
                        0.5f
                    } else {
                        1.0f
                    }
                )
        ) {
            Column {
                Text(title, style = Typography.bodyLarge)

                if (subtitle != null) {
                    Text(
                        subtitle,
                        style = Typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
        }
        Box(modifier = Modifier.align(Alignment.CenterVertically)) {
            content()
        }
    }
}

@Composable
fun SettingToggleRaw(
    title: String,
    enabled: Boolean,
    setValue: (Boolean) -> Unit,
    subtitle: String? = null,
    disabled: Boolean = false,
    icon: (@Composable () -> Unit)? = null
) {
    SettingItem(
        title = title,
        subtitle = subtitle,
        onClick = {
            if (!disabled) {
                setValue(!enabled)
            }
        },
        icon = icon
    ) {
        Switch(checked = enabled, onCheckedChange = {
            if (!disabled) {
                setValue(!enabled)
            }
        }, enabled = !disabled)
    }
}

@Composable
fun SettingToggle(
    title: String,
    key: Preferences.Key<Boolean>,
    default: Boolean,
    subtitle: String? = null,
    disabledSubtitle: String? = null,
    disabled: Boolean = false,
    icon: (@Composable () -> Unit)? = null,
    onChanged: ((Boolean) -> Unit)? = null
) {
    val (enabled, setValue) = useDataStore(key, default)

    val subtitleValue = if (!enabled && disabledSubtitle != null) {
        disabledSubtitle
    } else {
        subtitle
    }

    SettingToggleRaw(title, enabled, {
        setValue(it)
        onChanged?.invoke(it)
     }, subtitleValue, disabled, icon)
}

@Composable
fun ScrollableList(content: @Composable () -> Unit) {
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
    ) {
        content()
    }
}

@Composable
fun SettingListLazy(content: LazyListScope.() -> Unit) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
    ) {
        content()
    }
}

@Composable
@Preview
fun SettingsMain(
    settingsViewModel: SettingsViewModel = viewModel(),
    navController: NavHostController = rememberNavController(),
    billing: BillingManager? = null
) {
    val settingsUiState by settingsViewModel.uiState.collectAsState()

    val isAlreadyPaid = useDataStore(IS_ALREADY_PAID, default = false)
    val hasSeenNotice = useDataStore(HAS_SEEN_PAID_NOTICE, default = false)
    val paymentDest = if (!isAlreadyPaid.value && hasSeenNotice.value) {
        "error"
    } else if (isAlreadyPaid.value && !hasSeenNotice.value) {
        "paid"
    } else {
        "pleasePay"
    }

    LaunchedEffect(paymentDest) {
        if (paymentDest != "pleasePay") {
            navController.popBackStack("home", false)
            navController.navigate(
                paymentDest,
                NavOptions.Builder().setLaunchSingleTop(true).build()
            )
        }
    }

    NavHost(
        navController = navController,
        startDestination = "home"
    ) {
        composable("home") {
            HomeScreen(settingsViewModel, navController)
        }
        composable("advanced") {
            AdvancedScreen(settingsViewModel, navController)
        }
        composable("help") {
            HelpScreen()
        }
        composable("languages") {
            LanguagesScreen(settingsViewModel, navController)
        }
        composable("testing") {
            TestScreen(settingsUiState.intentResultText, navController)
        }
        composable("credits") {
            CreditsScreen(openDependencies = {
                navController.navigate("dependencies")
            })
        }
        composable("dependencies") {
            DependenciesScreen()
        }
        composable("models") {
            ModelsScreen(settingsViewModel, navController)
        }

        composable("input") {
            InputScreen(settingsViewModel, navController)
        }

        composable("pleasePay") {
            PaymentScreen(
                settingsViewModel,
                navController,
                { navController.popBackStack() },
                billing!!
            )
        }

        composable("paid") {
            PaymentThankYouScreen { navController.popBackStack() }
        }

        composable("error") {
            PaymentFailedScreen { navController.popBackStack() }
        }
    }
}

data class BlacklistedInputMethod(val packageName: String, val details: String, val dismiss: String)


@Composable
fun SetupOrMain(settingsViewModel: SettingsViewModel = viewModel(), billing: BillingManager) {
    val blacklistedMethods =
        listOf(
            BlacklistedInputMethod(
                // No issue for this as far as I can tell
                // Maybe file one at https://issuetracker.google.com/issues?q=gboard
                "com.google.android.inputmethod.latin/com.android.inputmethod.latin.LatinIME",
                details = stringResource(R.string.gboard_incompatible_details),
                dismiss = stringResource(R.string.gboard_incompatible_accept)
            ),
            BlacklistedInputMethod( // Issue: https://suggestions.typewise.app/suggestions/65517/voice-to-text-dictation
                "ch.icoaching.typewise/ch.icoaching.wrio.Wrio",
                details = stringResource(R.string.typewise_incompatible_details),
                dismiss = stringResource(R.string.typewise_incompatible_accept)
            ),
            BlacklistedInputMethod(
                "com.samsung.android.honeyboard/.service.HoneyBoardService",
                details = stringResource(R.string.samsung_keyboard_incompatible_details),
                dismiss = stringResource(R.string.samsung_keyboard_incompatible_accept)
            ),

            // NOTE: These are two entirely different keyboards with the same name, both incompatible
            BlacklistedInputMethod( // Issue: https://github.com/SimpleMobileTools/Simple-Keyboard/issues/201
                "com.simplemobiletools.keyboard/.services.SimpleKeyboardIME",
                details = stringResource(R.string.simplekeyboard_incompatible_details),
                dismiss = stringResource(R.string.simplekeyboard_incompatible_accept)
            ),
            BlacklistedInputMethod( // Issue: https://github.com/rkkr/simple-keyboard/issues/133
                "rkr.simplekeyboard.inputmethod/.latin.LatinIME",
                details = stringResource(R.string.simplekeyboard_incompatible_details),
                dismiss = stringResource(R.string.simplekeyboard_incompatible_accept)
            )
        )

    val settingsUiState by settingsViewModel.uiState.collectAsState()

    val inputMethodEnabled = useIsInputMethodEnabled(settingsUiState.numberOfResumes)
    val microphonePermitted = useIsMicrophonePermitted(settingsUiState.numberOfResumes)
    val defaultIME = useDefaultIME(settingsUiState.numberOfResumes)

    val acknowledgedBlacklistedWarning = rememberSaveable { mutableStateOf(false) }
    val blacklistedKeyboardInfo =
        blacklistedMethods.firstOrNull { it.packageName == defaultIME.value }

    val acknowledgedWrongDefaultWarning = rememberSaveable { mutableStateOf(false) }
    val defaultVoiceInputIntent = useDefaultVoiceInputIntent(settingsUiState.numberOfResumes)

    if(defaultVoiceInputIntent.value.kind == DefaultVoiceInputIntentKind.OTHER && defaultVoiceInputIntent.value.name != null && !acknowledgedWrongDefaultWarning.value) {
        SetupWrongDefaultWarning(
            defaultVoiceInputIntent.value
        ) { acknowledgedWrongDefaultWarning.value = true }
    }else if (blacklistedKeyboardInfo != null && !acknowledgedBlacklistedWarning.value) {
        SetupBlacklistedKeyboardWarning(
            blacklistedKeyboardInfo
        ) { acknowledgedBlacklistedWarning.value = true }
    } else if (inputMethodEnabled.value == Status.False) {
        SetupEnableIME()
    } else if (microphonePermitted.value == Status.False) {
        SetupEnableMic()
    } else if ((inputMethodEnabled.value == Status.Unknown) || (microphonePermitted.value == Status.Unknown)) {
        Row(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.CenterVertically)
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                    color = MaterialTheme.colorScheme.onPrimary
                )
            }
        }
    } else {
        SettingsMain(settingsViewModel, billing = billing)
    }
}
