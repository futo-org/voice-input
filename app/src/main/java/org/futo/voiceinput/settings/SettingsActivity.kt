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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment.Companion.CenterHorizontally
import androidx.compose.ui.Alignment.Companion.CenterVertically
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.futo.voiceinput.ENABLE_SOUND
import org.futo.voiceinput.Status
import org.futo.voiceinput.dataStore
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
@Preview
fun Settings() {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val enableSoundFlow: Flow<Boolean> = remember {
        context.dataStore.data.map {
            preferences -> preferences[ENABLE_SOUND] ?: true
        }
    }

    val enabled = enableSoundFlow.collectAsState(initial = true).value!!

    val onClick = { value: Boolean ->
        coroutineScope.launch {
            context.dataStore.edit { preferences ->
                preferences[ENABLE_SOUND] = value
            }
        }
    }

    Column(modifier = Modifier
        .fillMaxSize()
        .padding(8.dp)) {
        Row(modifier = Modifier
            .fillMaxWidth()
            .clickable {
                onClick(!enabled)
            }) {
            Row(modifier = Modifier
                .weight(1f)
                .align(CenterVertically)) {
                Column {
                    Text("Sounds", style = Typography.bodyLarge)
                    Text("Play a sound when recognition is started or cancelled", style = Typography.labelSmall)
                }
            }
            Switch(checked = enabled, onCheckedChange = { onClick(!enabled) })
        }
    }
}

@Composable
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
        // TODO: A settings menu instead of straight to InputTest
        //InputTest(settingsUiState.intentResultText)
        Column {
            InputTest(settingsUiState.intentResultText)
            Settings()
        }
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

        val _viewModel: SettingsViewModel by viewModels()
        viewModel = _viewModel

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
