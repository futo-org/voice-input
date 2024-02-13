package org.futo.voiceinput.settings

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
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
import org.futo.voiceinput.R
import org.futo.voiceinput.Status
import org.futo.voiceinput.payments.BillingManager
import org.futo.voiceinput.settings.pages.AdvancedScreen
import org.futo.voiceinput.settings.pages.CreditsScreen
import org.futo.voiceinput.settings.pages.DependenciesScreen
import org.futo.voiceinput.settings.pages.HelpScreen
import org.futo.voiceinput.settings.pages.HomeScreen
import org.futo.voiceinput.settings.pages.InputScreen
import org.futo.voiceinput.settings.pages.LanguagesScreen
import org.futo.voiceinput.settings.pages.ModelsScreen
import org.futo.voiceinput.settings.pages.PaymentFailedScreen
import org.futo.voiceinput.settings.pages.PaymentScreen
import org.futo.voiceinput.settings.pages.PaymentThankYouScreen
import org.futo.voiceinput.settings.pages.TestScreen
import org.futo.voiceinput.settings.pages.ThemeScreen


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
fun SettingsMain(
    settingsViewModel: SettingsViewModel = viewModel(),
    navController: NavHostController = rememberNavController(),
    billing: BillingManager? = null
) {
    val settingsUiState by settingsViewModel.uiState.collectAsState()

    val isAlreadyPaid = useDataStore(IS_ALREADY_PAID.key, default = IS_ALREADY_PAID.default)
    val hasSeenNotice = useDataStore(HAS_SEEN_PAID_NOTICE.key, default = HAS_SEEN_PAID_NOTICE.default)
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
        composable("home") { HomeScreen(settingsViewModel, navController) }
        composable("advanced") { AdvancedScreen(settingsViewModel, navController) }
        composable("help") { HelpScreen(navController) }
        composable("languages") { LanguagesScreen(settingsViewModel, navController) }
        composable("testing") { TestScreen(settingsUiState.intentResultText, navController) }
        composable("models") { ModelsScreen(settingsViewModel, navController) }
        composable("input") { InputScreen(settingsViewModel, navController) }
        composable("themes") { ThemeScreen(navController) }

        composable("credits") {
            CreditsScreen(openDependencies = {
                navController.navigate("dependencies")
            }, navController = navController)
        }
        composable("dependencies") { DependenciesScreen(navController) }

        composable("pleasePay") {
            PaymentScreen(
                settingsViewModel,
                navController,
                { navController.navigateUp() },
                billing!!
            )
        }

        composable("paid") {
            PaymentThankYouScreen { navController.navigateUp() }
        }

        composable("error") {
            PaymentFailedScreen { navController.navigateUp() }
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
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    } else {
        SettingsMain(settingsViewModel, billing = billing)
    }
}
