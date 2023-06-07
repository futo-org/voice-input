package org.futo.voiceinput

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.speech.RecognizerIntent
import android.view.inputmethod.InputMethodManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.futo.voiceinput.ui.theme.WhisperVoiceInputTheme


class MainActivity : ComponentActivity() {
    private var resultText = "Result goes here"
    private var needsToEnableIME = true
    private fun updateContent() {
        needsToEnableIME = !isThisImeEnabled(
            this,
            getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        )

        setContent {
            WhisperVoiceInputTheme {
                // A surface container using the 'background' color from the theme
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    InputTest(voiceIntentCallback = {
                        launchVoiceIntent()
                    }, voiceIntentResult = resultText,
                    needToEnableIME = needsToEnableIME,
                    enableIME = {
                        invokeLanguageAndInputSettings()
                    })
                }
            }
        }
    }

    private fun isThisImeEnabled(
        context: Context,
        imm: InputMethodManager
    ): Boolean {
        val packageName = context.packageName
        for (imi in imm.enabledInputMethodList) {
            if (packageName == imi.packageName) {
                return true
            }
        }
        return false
    }

    private fun invokeLanguageAndInputSettings() {
        val intent = Intent()
        intent.action = Settings.ACTION_INPUT_METHOD_SETTINGS
        intent.addCategory(Intent.CATEGORY_DEFAULT)
        startActivity(intent)
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
        updateContent()
    }

    override fun onRestart() {
        super.onRestart()
        updateContent()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InputTest(voiceIntentCallback: () -> Unit = { }, voiceIntentResult: String = "Result goes here", needToEnableIME: Boolean = true, enableIME: () -> Unit = {}) {
    var text by remember { mutableStateOf("") }

    LaunchedEffect(voiceIntentResult) {
        if((text != voiceIntentResult) && (voiceIntentResult != "Result goes here")) {
            text = voiceIntentResult
        }
    }

    Column() {
        if(needToEnableIME) {
            Button(onClick = { enableIME() }, modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                Text("The Voice Input Method appears to be disabled. Please enable the Voice Input Method to use integration with AOSP/OpenBoard keyboards")
            }
        }
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