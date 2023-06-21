package org.futo.voiceinput.settings

import android.Manifest
import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment.Companion.CenterHorizontally
import androidx.compose.ui.Alignment.Companion.CenterVertically
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.Preferences
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.futo.voiceinput.DISALLOW_SYMBOLS
import org.futo.voiceinput.ENABLE_ENGLISH
import org.futo.voiceinput.ENABLE_MULTILINGUAL
import org.futo.voiceinput.ENABLE_SOUND
import org.futo.voiceinput.Status
import org.futo.voiceinput.TINY_MULTILINGUAL_MODEL_DATA
import org.futo.voiceinput.VERBOSE_PROGRESS
import org.futo.voiceinput.modelNeedsDownloading
import org.futo.voiceinput.startModelDownloadActivity
import org.futo.voiceinput.ui.theme.Typography
import org.futo.voiceinput.ui.theme.WhisperVoiceInputTheme

data class SettingsUiState(
    val intentResultText: String = "Result goes here",
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


@Composable
fun SettingItem(title: String, subtitle: String? = null, onClick: () -> Unit, icon: (@Composable () -> Unit)? = null, disabled: Boolean = false, content: @Composable () -> Unit) {
    Row(modifier = Modifier
        .fillMaxWidth()
        .height(68.dp)
        .clickable(enabled = !disabled, onClick = {
            if (!disabled) {
                onClick()
            }
        })
        .padding(0.dp, 4.dp, 8.dp, 4.dp)
    ) {
        Column(modifier = Modifier
            .width(42.dp)
            .align(CenterVertically)) {
            Box(modifier = Modifier.align(CenterHorizontally)) {
                if (icon != null) {
                    icon()
                }
            }
        }
        Row(
            modifier = Modifier
                .weight(1f)
                .align(CenterVertically)
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
                    Text(subtitle, style = Typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                }
            }
        }
        Box(modifier = Modifier.align(CenterVertically)) {
            content()
        }
    }
}


@Composable
fun SettingToggle(title: String, key: Preferences.Key<Boolean>, default: Boolean, subtitle: String? = null, disabled: Boolean = false, icon: (@Composable () -> Unit)? = null) {
    val (enabled, setValue) = useDataStore(key, default)

    SettingItem(
        title = title,
        subtitle = subtitle,
        onClick = { if(!disabled) { setValue(!enabled) } },
        icon = icon
    ) {
        Switch(checked = enabled, onCheckedChange = { if(!disabled) { setValue(!enabled) } }, enabled = !disabled)
    }
}

@Composable
fun SettingList(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(0.dp, 8.dp)
    ) {
        content()
    }
}

@Composable
@Preview
fun SettingsHome(settingsViewModel: SettingsViewModel = viewModel(), navController: NavHostController = rememberNavController()) {
    Column(modifier = Modifier
        .padding(16.dp)
        .fillMaxHeight()) {
        Text("Settings", style = Typography.titleLarge)

        SettingList {
            SettingToggle(
                "Sounds",
                ENABLE_SOUND,
                default = true,
                subtitle = "Play sound when recognition starts/cancels"
            )
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
            SettingItem(title = "Languages", onClick = { navController.navigate("languages") }) {
                Icon(Icons.Default.ArrowForward, contentDescription = "Go")
            }
            SettingItem(title = "Testing Menu", onClick = { navController.navigate("testing") }) {
                Icon(Icons.Default.ArrowForward, contentDescription = "Go")
            }
        }
    }
}

@Composable
@Preview
fun SettingsLanguages(settingsViewModel: SettingsViewModel = viewModel(), navController: NavHostController = rememberNavController()) {
    val (multilingual, _) = useDataStore(key = ENABLE_MULTILINGUAL, default = false)
    val context = LocalContext.current

    LaunchedEffect(multilingual) {
        if(multilingual && context.modelNeedsDownloading(TINY_MULTILINGUAL_MODEL_DATA)) {
            context.startModelDownloadActivity(TINY_MULTILINGUAL_MODEL_DATA)
        }
    }

    Column(modifier = Modifier
        .padding(16.dp)
        .fillMaxHeight()) {
        Text("Languages", style = Typography.titleLarge)

        SettingList {
            SettingToggle(
                "English",
                ENABLE_ENGLISH,
                default = true,
                disabled = true,
                subtitle = "Always on"
            )
            SettingToggle(
                "Other languages",
                ENABLE_MULTILINGUAL,
                default = false,

                subtitle = "The accuracy is currently very bad. Enabling this will also reduce English accuracy"
            )
        }
    }
}

@Composable
@Preview
fun SettingsMain(settingsViewModel: SettingsViewModel = viewModel(), navController: NavHostController = rememberNavController()) {
    val settingsUiState by settingsViewModel.uiState.collectAsState()

    NavHost(
        navController = navController,
        startDestination = "settingsHome"
    ) {
        composable("settingsHome") {
            SettingsHome(settingsViewModel, navController)
        }
        composable("languages") {
            SettingsLanguages(settingsViewModel, navController)
        }
        composable("testing") {
            InputTest(settingsUiState.intentResultText)
        }
    }

}

@Composable
@Preview
fun SetupOrMain(settingsViewModel: SettingsViewModel = viewModel()) {
    val settingsUiState by settingsViewModel.uiState.collectAsState()

    val inputMethodEnabled = useIsInputMethodEnabled(settingsUiState.numberOfResumes)
    val microphonePermitted = useIsMicrophonePermitted(settingsUiState.numberOfResumes)

    if (inputMethodEnabled.value == Status.False) {
        SetupEnableIME()
    } else if (microphonePermitted.value == Status.False) {
        SetupEnableMic()
    } else if ((inputMethodEnabled.value == Status.Unknown) || (microphonePermitted.value == Status.Unknown)) {
        Row(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier
                .fillMaxWidth()
                .align(CenterVertically)) {
                CircularProgressIndicator(
                    modifier = Modifier.align(CenterHorizontally),
                    color = MaterialTheme.colorScheme.onPrimary
                )
            }
        }
    } else {
        SettingsMain(settingsViewModel)
    }
}

class SettingsActivity : ComponentActivity() {
    private fun updateContent() {
        setContent {
            WhisperVoiceInputTheme {
                // A surface container using the 'background' color from the theme
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    SetupOrMain()
                }
            }
        }
    }

    private val permission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        viewModel.onResume()
    }


    private val voiceIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
    private val runVoiceIntent = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        viewModel.onIntentResult(when(it.resultCode){
            RESULT_OK -> {
                val result = it.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                if(result.isNullOrEmpty()) {
                    "Intent result is null or empty"
                } else {
                    result[0]
                }
            }
            RESULT_CANCELED -> "Intent was cancelled"
            else -> "Unknown intent result"
        })
    }

    internal fun requestPermission() {
        permission.launch(Manifest.permission.RECORD_AUDIO)
    }
    internal fun launchVoiceIntent() {
        runVoiceIntent.launch(voiceIntent)
    }

    private lateinit var viewModel: SettingsViewModel
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        viewModel = viewModels<SettingsViewModel>().value

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect {
                    updateContent()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()

        viewModel.onResume()
    }

    override fun onRestart() {
        super.onRestart()

        viewModel.onResume()
    }
}
