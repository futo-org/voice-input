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
import androidx.datastore.preferences.core.edit
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import org.futo.voiceinput.FORCE_SHOW_NOTICE
import org.futo.voiceinput.Screen
import org.futo.voiceinput.dataStore


@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Preview
fun TestScreen(voiceIntentResult: String = "Voice Intent Result", navController: NavHostController = rememberNavController()) {
    var text by remember { mutableStateOf("") }


    LaunchedEffect(voiceIntentResult) {
        if((text != voiceIntentResult) && (voiceIntentResult != "Result goes here")) {
            text = voiceIntentResult
        }
    }

    val context = LocalContext.current

    LaunchedEffect(text) {
        if(text.lowercase().trim() == "@force30days") {
            context.dataStore.edit { it[FORCE_SHOW_NOTICE] = true }
            navController.popBackStack("home", false, false)
        }
    }

    Screen("Input testing") {
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