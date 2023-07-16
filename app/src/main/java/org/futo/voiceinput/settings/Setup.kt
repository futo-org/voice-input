package org.futo.voiceinput.settings

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment.Companion.CenterHorizontally
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.futo.voiceinput.R
import org.futo.voiceinput.ui.theme.Slate300
import org.futo.voiceinput.ui.theme.Typography


@Composable
fun SetupContainer(inner: @Composable () -> Unit) {
    Column(modifier = Modifier.fillMaxSize()) {
        ScrollableList {

            Spacer(modifier = Modifier.height(48.dp))

            Icon(
                painter = painterResource(id = R.drawable.futo_logo),
                contentDescription = "FUTO Logo",
                modifier = Modifier
                    .size(LocalConfiguration.current.screenWidthDp.dp * 0.75f, LocalConfiguration.current.screenHeightDp.dp * 0.2f)
                    .align(CenterHorizontally),
                tint = Slate300
            )

            Box(modifier = Modifier.align(CenterHorizontally)) {
                // If the system font size is way big, the content may be big enough to overlap
                // with the FUTO logo, so just block the logo with a surface to keep text readable
                Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.padding(16.dp).fillMaxSize()) {
                    Column {
                        inner()
                    }
                }
            }

        }
    }
}


@Composable
fun Step(fraction: Float, text: String) {
    Column(modifier = Modifier.padding(16.dp)) {
        Text(text, style = Typography.labelSmall)
        LinearProgressIndicator(progress = fraction, modifier = Modifier.fillMaxWidth())
    }
}

// TODO: May wish to have a skip option
@Composable
@Preview
fun SetupEnableIME(onClick: () -> Unit = { }) {
    val context = LocalContext.current

    val launchImeOptions = {
        // TODO: look into direct boot to get rid of direct boot warning?
        val intent = Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)

        intent.flags = (Intent.FLAG_ACTIVITY_NEW_TASK
                or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
                or Intent.FLAG_ACTIVITY_NO_HISTORY
                or Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)

        context.startActivity(intent)
        // TODO: I believe some keyboards seem to poll repeatedly to see if it got enabled yet,
        // to exit settings and go back to the app, not requiring the user to press back

        onClick()
    }

    SetupContainer {
        Step(fraction = 0.33f, text = "Step 1 of 2")

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

@Composable
@Preview
fun SetupEnableMic(onClick: () -> Unit = { }) {
    val context = LocalContext.current

    var askedCount by remember { mutableStateOf(0) }
    val askMicAccess = {
        if (askedCount++ >= 2) {
            val packageName = context.packageName
            val myAppSettings = Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse(
                    "package:$packageName"
                )
            )
            myAppSettings.addCategory(Intent.CATEGORY_DEFAULT)
            myAppSettings.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            context.startActivity(myAppSettings)
        } else {
            (context as SettingsActivity).requestPermission()
        }
        onClick()
    }

    SetupContainer {
        Step(fraction = 0.66f, text = "Step 2 of 2")
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


@Composable
@Preview
fun SetupBlacklistedKeyboardWarning(info: BlacklistedInputMethod = BlacklistedInputMethod("sample", "Example Keyboard", "This keyboard is incompatible because xyz.."), onClick: () -> Unit = { }) {

    SetupContainer {
        Text(
            "Incompatible keyboard",
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            "You appear to be using ${info.name}. ${info.reason}",
            textAlign = TextAlign.Start,
            modifier = Modifier.fillMaxWidth(),
            style = Typography.bodyMedium
        )
        Button(
            onClick = onClick,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text("I understand ${info.name} is incompatible")
        }
    }
}
