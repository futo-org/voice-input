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
import androidx.compose.foundation.lazy.LazyColumn
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.futo.voiceinput.DISALLOW_SYMBOLS
import org.futo.voiceinput.ENABLE_MULTILINGUAL
import org.futo.voiceinput.ENABLE_SOUND
import org.futo.voiceinput.LANGUAGE_TOGGLES
import org.futo.voiceinput.MULTILINGUAL_MODEL_DATA
import org.futo.voiceinput.Status

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
fun SettingToggleRaw(title: String, enabled: Boolean, setValue: (Boolean) -> Unit, subtitle: String? = null, disabled: Boolean = false, icon: (@Composable () -> Unit)? = null) {
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
fun SettingToggle(title: String, key: Preferences.Key<Boolean>, default: Boolean, subtitle: String? = null, disabled: Boolean = false, icon: (@Composable () -> Unit)? = null) {
    val (enabled, setValue) = useDataStore(key, default)

    SettingToggleRaw(title, enabled, { setValue(it) }, subtitle, disabled, icon)
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
    val (multilingual, setMultilingual) = useDataStore(key = ENABLE_MULTILINGUAL, default = false)
    val multilingualSubtitle = if(multilingual) {
        "Multilingual enabled, English latency will be increased"
    } else {
        null
    }

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
            SettingItem(title = "Languages", onClick = { navController.navigate("languages") }, subtitle = multilingualSubtitle) {
                Icon(Icons.Default.ArrowForward, contentDescription = "Go")
            }
            SettingItem(title = "Testing Menu", onClick = { navController.navigate("testing") }) {
                Icon(Icons.Default.ArrowForward, contentDescription = "Go")
            }
        }
    }
}

@Composable
fun LanguageToggle(id: String, name: String, languages: Set<String>, setLanguages: (Set<String>) -> Job, subtitle: String?) {
    SettingToggleRaw(
        name,
        languages.contains(id),
        { setLanguages( (languages.filter{ it != id} + if(it) { listOf(id) } else { listOf() } ).toSet() ) },
        subtitle = subtitle
    )
}

data class LanguageEntry(val id: String, val name: String, val trainedHourCount: Int)

@Composable
@Preview
fun SettingsLanguages(settingsViewModel: SettingsViewModel = viewModel(), navController: NavHostController = rememberNavController()) {
    val (multilingual, setMultilingual) = useDataStore(key = ENABLE_MULTILINGUAL, default = false)
    val (languages, setLanguages) = useDataStore(key = LANGUAGE_TOGGLES, default = setOf("en"))
    val context = LocalContext.current

    LaunchedEffect(multilingual) {
        if(multilingual && context.modelNeedsDownloading(MULTILINGUAL_MODEL_DATA)) {
            context.startModelDownloadActivity(MULTILINGUAL_MODEL_DATA)
        }
    }

    LaunchedEffect(languages) {
        val newMultilingual = languages.count { it != "en" } > 0
        if(multilingual != newMultilingual) setMultilingual(newMultilingual)
    }

    // Numbers from Appendix E. Figure 11. https://cdn.openai.com/papers/whisper.pdf
    val languageList = listOf(
        LanguageEntry("zh", "Chinese", 23446),
        LanguageEntry("de", "German", 13344),
        LanguageEntry("es", "Spanish", 11100),
        LanguageEntry("ru", "Russian", 9761),
        LanguageEntry("fr", "French", 9752),
        LanguageEntry("pt", "Portuguese", 8573),
        LanguageEntry("ko", "Korean", 7993),
        LanguageEntry("ja", "Japanese", 7054),
        LanguageEntry("tr", "Turkish", 4333),
        LanguageEntry("pl", "Polish", 4278),
        LanguageEntry("it", "Italian", 2585),
        LanguageEntry("sv", "Swedish", 2119),
        LanguageEntry("nl", "Dutch", 2077),
        LanguageEntry("ca", "Catalan", 1883),
        LanguageEntry("fi", "Finnish", 1066),
        LanguageEntry("id", "Indonesian", 1014),
        LanguageEntry("ar", "Arabic", 739),
        LanguageEntry("uk", "Ukrainian", 697),
        LanguageEntry("vi", "Vietnamese", 691),
        LanguageEntry("he", "Hebrew", 688),
        LanguageEntry("el", "Greek", 529),
        LanguageEntry("da", "Danish", 473),
        LanguageEntry("ms", "Malay", 382),
        LanguageEntry("hu", "Hungarian", 379),
        LanguageEntry("ro", "Romanian", 356),
        LanguageEntry("no", "Norwegian", 266),
        LanguageEntry("th", "Thai", 226),
        LanguageEntry("cs", "Czech", 192),
        LanguageEntry("ta", "Tamil", 134),
        LanguageEntry("ur", "Urdu", 104),
        LanguageEntry("hr", "Croatian", 91),
        LanguageEntry("sk", "Slovak", 90),
        LanguageEntry("bg", "Bulgarian", 86),
        LanguageEntry("tl", "Tagalog", 75),
        LanguageEntry("cy", "Welsh", 73),
        LanguageEntry("lt", "Lithuanian", 67),
        LanguageEntry("lv", "Latvian", 65),
        LanguageEntry("az", "Azerbaijani", 47),
        LanguageEntry("et", "Estonian", 41),
        LanguageEntry("sl", "Slovenian", 41),
        LanguageEntry("sr", "Serbian", 28),
        LanguageEntry("fa", "Persian", 24),
        LanguageEntry("eu", "Basque", 21),
        LanguageEntry("is", "Icelandic", 16),
        LanguageEntry("mk", "Macedonian", 16),
        LanguageEntry("hy", "Armenian", 13),
        LanguageEntry("kk", "Kazakh", 12),
        LanguageEntry("hi", "Hindi", 12),
        LanguageEntry("bs", "Bosnian", 11),
        LanguageEntry("gl", "Galician", 9),
        LanguageEntry("sq", "Albanian", 6),
        LanguageEntry("si", "Sinhala", 5),
        LanguageEntry("sw", "Swahili", 5),
        LanguageEntry("te", "Telugu", 4),
        LanguageEntry("af", "Afrikaans", 4),
        LanguageEntry("kn", "Kannada", 4),
        LanguageEntry("be", "Belarusian", 2),
        LanguageEntry("km", "Khmer", 1),
        LanguageEntry("bn", "Bengali", 1),
        LanguageEntry("mt", "Maltese", 1),
        LanguageEntry("ht", "Haitian Creole", 1),
        LanguageEntry("pa", "Punjabi", 1),
        LanguageEntry("mr", "Marathi", 1),
        LanguageEntry("ne", "Nepali", 1),
        LanguageEntry("ka", "Georgian", 1),
        LanguageEntry("ml", "Malayalam", 1),

        // Languages below trained on fewer than 0.5 hours of data
        LanguageEntry("yi", "Yiddish", 0),
        LanguageEntry("uz", "Uzbek", 0),
        LanguageEntry("gu", "Gujarati", 0),
        LanguageEntry("tg", "Tajik", 0),
        LanguageEntry("mg", "Malagasy", 0),
        LanguageEntry("my", "Burmese", 0),
        LanguageEntry("su", "Sundanese", 0),
        LanguageEntry("lo", "Lao", 0)
    )

    Column(modifier = Modifier
        .padding(16.dp)
        .fillMaxHeight()) {
        Text("Languages", style = Typography.titleLarge)

        SettingList {
            LazyColumn {
                item {
                    SettingToggleRaw(
                        "English",
                        true,
                        {},
                        disabled = true,
                        subtitle = "Always on. Enabling others will increase English latency"
                    )
                }

                items(languageList.size) {
                    val language = languageList[it]

                    val subtitle = if(language.trainedHourCount < 500) {
                        "May be low accuracy (${language.trainedHourCount}h)"
                    } else {
                        "Trained on ${language.trainedHourCount} hours"
                    }

                    // Only show languages trained with over 1000 hours for now, as anything lower
                    // can be laughably bad on the tiny model
                    if(language.trainedHourCount > 1000) {
                        LanguageToggle(language.id, language.name, languages, setLanguages, subtitle)
                    }
                }
            }
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
