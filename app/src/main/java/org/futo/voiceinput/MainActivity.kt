package org.futo.voiceinput

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.speech.RecognizerIntent
import android.view.inputmethod.InputMethodManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment.Companion.CenterHorizontally
import androidx.compose.ui.Alignment.Companion.CenterVertically
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.futo.voiceinput.ui.theme.Slate300
import org.futo.voiceinput.ui.theme.WhisperVoiceInputTheme

@Composable
fun useIsInputMethodEnabled(i: Int): MutableState<Boolean> {
    val enabled = remember { mutableStateOf(false) }

    val context = LocalContext.current
    LaunchedEffect(i) {
        val packageName = context.packageName
        val imm = context.getSystemService(ComponentActivity.INPUT_METHOD_SERVICE) as InputMethodManager

        var found = false
        for (imi in imm.enabledInputMethodList) {
            if (packageName == imi.packageName) {
                found = true
            }
        }

        enabled.value = found
    }

    return enabled
}

@Composable
fun useIsMicrophonePermitted(i: Int): MutableState<Boolean> {
    val permitted = rememberSaveable { mutableStateOf(false) }

    val context = LocalContext.current
    LaunchedEffect(i) {
        if(!permitted.value) {
            permitted.value =
                context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        }
    }

    return permitted
}

@Composable
fun SetupContainer(inner: @Composable () -> Unit) {
    WhisperVoiceInputTheme {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth(fraction = 1.0f)
                    .fillMaxHeight(fraction = 0.4f)
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.futo_logo),
                    contentDescription = "FUTO Logo",
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(0.75f)
                        .align(CenterHorizontally),
                    tint = Slate300
                )
            }

            Row(
                modifier = Modifier.fillMaxSize()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(fraction = 0.5f)
                        .align(CenterVertically)
                        .padding(32.dp)
                ) {
                    Box(modifier = Modifier.align(CenterVertically)) {
                        inner()
                    }
                }
            }
        }
    }
}



// TODO: May wish to have a skip option
@Composable
@Preview
fun SetupEnableIME(onClick: () -> Unit = { }) {
    val context = LocalContext.current
    val launchImeOptions = {
        val intent = Intent()
        intent.action = Settings.ACTION_INPUT_METHOD_SETTINGS
        intent.addCategory(Intent.CATEGORY_DEFAULT)
        context.startActivity(intent)

        onClick()
    }

    SetupContainer {
        Column {
            Text(
                "To integrate with existing keyboards, you need to enable the Voice Input Method.",
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Button(
                onClick = launchImeOptions,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text("Open Input Method Settings")
            }
        }
    }
}

@Composable
@Preview
fun SetupEnableMic(onClick: () -> Unit = { }) {
    val context = LocalContext.current
    val askMicAccess = {
        (context as MainActivity).requestPermission()
        onClick()
    }

    SetupContainer {
        Column {
            // TODO: Include some privacy statement
            Text(
                "In order to use Voice Input, you need to grant microphone permission.",
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Button(
                onClick = askMicAccess,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text("Grant Microphone")
            }
        }
    }
}

@Composable
fun SetupSwitch(voiceIntentCallback: () -> Unit = { }, voiceIntentResult: String = "Result goes here", micI: Int = 0) {
    var i by remember { mutableStateOf(0) }

    val inputMethodEnabled = useIsInputMethodEnabled(i)
    val microphonePermitted = useIsMicrophonePermitted(i)

    val refresh = {
        i += 1
    }

    LaunchedEffect(micI) { refresh() }

    if (!inputMethodEnabled.value) {
        SetupEnableIME(onClick = refresh)
    } else if (!microphonePermitted.value) {
        SetupEnableMic(onClick = refresh)
    } else {
        InputTest(voiceIntentCallback, voiceIntentResult)
    }
}

class MainActivity : ComponentActivity() {
    private var resultText = "Result goes here"
    private var micI = 0
    private fun updateContent() {
        setContent {
            WhisperVoiceInputTheme {
                // A surface container using the 'background' color from the theme
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    SetupSwitch(
                        voiceIntentCallback = { launchVoiceIntent() },
                        voiceIntentResult = resultText,
                        micI = micI
                    )
                }
            }
        }
    }

    private val permission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        micI++ // TODO: Kinda hacky
        updateContent()
    }

    internal fun requestPermission() {
        permission.launch(Manifest.permission.RECORD_AUDIO)
    }

    private val voiceIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
    private val runVoiceIntent = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        resultText = when(it.resultCode){
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
        }

        updateContent()
    }

    private fun launchVoiceIntent() {
        runVoiceIntent.launch(voiceIntent)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        updateContent()
    }

    override fun onResume() {
        super.onResume()

        micI += 1
        updateContent()
    }

    override fun onRestart() {
        super.onRestart()
        updateContent()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InputTest(voiceIntentCallback: () -> Unit = { }, voiceIntentResult: String = "Result goes here") {
    var text by remember { mutableStateOf("") }

    LaunchedEffect(voiceIntentResult) {
        if((text != voiceIntentResult) && (voiceIntentResult != "Result goes here")) {
            text = voiceIntentResult
        }
    }

    Column() {
        TextField(
            value = text,
            onValueChange = { text = it },
            placeholder = { Text("Input test field") },
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        )

        Column(modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)){
            Button(onClick = { voiceIntentCallback() }, modifier = Modifier.fillMaxWidth()) {
                Text("Trigger voice input intent")
            }
            Text(voiceIntentResult, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())

        }
    }
}

@Preview
@Composable
fun InputTestPreview() {
    InputTest()
}