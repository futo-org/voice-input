package org.futo.voiceinput.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp


@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Preview
fun InputTest(stateIdx: Int = 0) {
    var text by remember { mutableStateOf("") }

    val context = LocalContext.current

    // TODO: Research better state management than this
    var voiceIntentResult by remember { mutableStateOf("Voice Intent Result") }
    LaunchedEffect(stateIdx) {
        voiceIntentResult = (context as SettingsActivity).resultText
    }


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
            Button(onClick = { (context as SettingsActivity).launchVoiceIntent() }, modifier = Modifier.fillMaxWidth()) {
                Text("Trigger voice input intent")
            }
            Text(voiceIntentResult, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())

        }
    }
}