package org.futo.voiceinput.settings

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.LinearGradient
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.content.getSystemService
import org.futo.voiceinput.R
import org.futo.voiceinput.theme.Typography


@Composable
fun SetupContainer(inner: @Composable () -> Unit) {
    Column(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.75f)
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_launcher_foreground),
                contentDescription = null,
                modifier = Modifier
                    .matchParentSize()
                    .align(Alignment.Center),
                tint = MaterialTheme.colorScheme.onBackground
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.CenterVertically)
                    .padding(32.dp)
            ) {
                Box(modifier = Modifier.align(Alignment.CenterVertically)) {
                    Column {
                        inner()
                    }
                }
            }
        }

        Spacer(modifier = Modifier.weight(0.25f))
    }
}

@Composable
fun Step(fraction: Float, text: String) {
    Column(modifier = Modifier.padding(16.dp)) {
        Text(text, style = Typography.SmallMl)
        LinearProgressIndicator(progress = fraction, modifier = Modifier.fillMaxWidth())
    }
}

fun openImeOptions(context: Context) {
    // TODO: look into direct boot to get rid of direct boot warning?
    val intent = Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)

    intent.flags = (Intent.FLAG_ACTIVITY_NEW_TASK
            or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
            or Intent.FLAG_ACTIVITY_NO_HISTORY
            or Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)

    context.startActivity(intent)
    // TODO: I believe some keyboards seem to poll repeatedly to see if it got enabled yet,
    // to exit settings and go back to the app, not requiring the user to press back
}

@Composable
private fun DialogWrapper(
    title: Int,
    body: Int,
    back: Int,
    forward: Int,
    onBack: () -> Unit,
    onForward: () -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(modifier = Modifier,
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(24.dp)) {

            Column(Modifier.padding(20.dp, 20.dp, 20.dp, 12.dp)) {
                Text(stringResource(title), style = Typography.Body.Medium)
                Spacer(Modifier.height(8.dp))
                Text(stringResource(body), style = Typography.SmallMl)

                Row(Modifier.align(Alignment.End)) {
                    TextButton(onClick = onBack) {
                        Text(stringResource(back))
                    }
                    TextButton(onClick = onForward) {
                        Text(stringResource(forward))
                    }
                }
            }
        }
    }
}

@Composable
fun SetupFUTOKeyboardConflictKeepUsingDialog(
    onBack: () -> Unit,
    onQuit: () -> Unit,
) = DialogWrapper(
        title = R.string.setup_which_voice_input_to_use_keyboard_use,
        body = R.string.setup_which_voice_input_to_use_keep_using,
        back = R.string.setup_which_voice_input_to_use_keep_using_back,
        forward = R.string.setup_which_voice_input_to_use_keep_using_quit,
        onBack = onBack,
        onForward = onQuit,
        onDismiss = onBack,
)

@Composable
fun SetupFUTOKeyboardConflictSwitchDialog(
    onBack: () -> Unit,
    onContinue: () -> Unit,
) {
    val context = LocalContext.current
    val defaultIME = useDefaultIME(0)

    DialogWrapper(
        title = R.string.setup_which_voice_input_to_use_voice_use,
        body = R.string.setup_which_voice_input_to_use_switch_manual,
        back = R.string.setup_which_voice_input_to_use_switch_settings,
        forward = R.string.setup_which_voice_input_to_use_switch_finished,
        onBack = {
            val pm = context.packageManager
            val intent = pm.getLaunchIntentForPackage(defaultIME.value.split("/")[0])!!

            intent.flags = (Intent.FLAG_ACTIVITY_NEW_TASK
                    or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
                    or Intent.FLAG_ACTIVITY_NO_HISTORY
                    or Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)

            context.startActivity(intent)
        },
        onForward = onContinue,
        onDismiss = onBack
    )
}

@Composable
@Preview
fun SetupFUTOKeyboardConflict(
    onSwitch: () -> Unit = {},
) {
    val scrollState = rememberScrollState()

    val scrollGradientAlpha = animateFloatAsState(if(scrollState.value >= scrollState.maxValue * 0.95f) { 0.0f } else { 1.0f })
    val dialogShownKeep = remember { mutableStateOf(false) }
    val dialogShownSwitch = remember { mutableStateOf(false) }

    val activity = LocalContext.current as? SettingsActivity
    if(dialogShownKeep.value) {
        SetupFUTOKeyboardConflictKeepUsingDialog(
            onBack = { dialogShownKeep.value = false },
            onQuit = { activity?.finish() }
        )
    }else if(dialogShownSwitch.value) {
        SetupFUTOKeyboardConflictSwitchDialog(
            onBack = { dialogShownSwitch.value = false },
            onContinue = { onSwitch() }
        )
    }

    Box(Modifier.fillMaxSize()) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .padding(8.dp)
                .fillMaxSize()
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {

            Spacer(Modifier.windowInsetsTopHeight(WindowInsets.systemBars))

            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(R.string.setup_which_voice_input_to_use),
                style = Typography.Heading.Medium
            )
            Spacer(Modifier.height(4.dp))

            Surface(
                modifier = Modifier,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = RoundedCornerShape(32.dp)
            ) {
                Column(
                    Modifier.padding(16.dp, 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxWidth()) {
                        Icon(
                            painterResource(R.drawable.keyboard_icon),
                            contentDescription = null,
                            modifier = Modifier.size(64.dp)
                        )
                        Text(
                            stringResource(R.string.setup_which_voice_input_to_use_keyboard),
                            style = Typography.Heading.Medium
                        )
                    }

                    Text(
                        "• " + stringResource(R.string.setup_which_voice_input_to_use_keyboard_details_1),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        "• " + stringResource(R.string.setup_which_voice_input_to_use_keyboard_details_2),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        "• " + stringResource(R.string.setup_which_voice_input_to_use_keyboard_details_3),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Button(onClick = {
                        dialogShownKeep.value = true
                    }, modifier = Modifier
                        .align(Alignment.End)
                        .padding(0.dp, 8.dp, 0.dp, 0.dp)) {
                        Text(stringResource(R.string.setup_which_voice_input_to_use_keyboard_use))
                    }
                }
            }


            Surface(
                modifier = Modifier,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = RoundedCornerShape(32.dp)
            ) {
                Column(
                    Modifier.padding(16.dp, 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxWidth()) {
                        Icon(
                            painterResource(R.drawable.voice_input_icon),
                            contentDescription = null,
                            modifier = Modifier.size(64.dp)
                        )
                        Text(
                            stringResource(R.string.setup_which_voice_input_to_use_voice),
                            style = Typography.Heading.Medium
                        )
                    }


                    Text(
                        "• " + stringResource(R.string.setup_which_voice_input_to_use_voice_details_1),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        "• " + stringResource(R.string.setup_which_voice_input_to_use_voice_details_2),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        "• " + stringResource(R.string.setup_which_voice_input_to_use_voice_details_3),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Button(onClick = {
                        dialogShownSwitch.value = true
                    }, modifier = Modifier
                        .align(Alignment.End)
                        .padding(0.dp, 8.dp, 0.dp, 0.dp)) {
                        Text(stringResource(R.string.setup_which_voice_input_to_use_voice_use))
                    }
                }
            }

            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(R.string.setup_which_voice_input_to_use_info),
                modifier = Modifier.fillMaxWidth(),
                style = Typography.SmallMl
            )

            Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.systemBars))
        }

        val bg = Brush.verticalGradient(listOf(
            Color.Transparent,
            MaterialTheme.colorScheme.background.copy(alpha = scrollGradientAlpha.value * 0.75f),
            MaterialTheme.colorScheme.background.copy(alpha = scrollGradientAlpha.value)
        ))
        Column(Modifier
            .fillMaxWidth()
            .background(bg)
            .align(Alignment.BottomCenter)
            .padding(0.dp, 8.dp)
            .alpha(scrollGradientAlpha.value), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(stringResource(R.string.setup_scroll_down_indicator), style = Typography.Body.Medium, modifier = Modifier.alpha(scrollGradientAlpha.value))
            Text("↓", style = Typography.Body.Medium, modifier = Modifier.alpha(scrollGradientAlpha.value))

            Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.systemBars))
        }
    }
}

@Composable
@Preview
fun SetupEnableIME(onClick: () -> Unit = { }) {
    val context = LocalContext.current

    val launchImeOptions = {
        openImeOptions(context)
        onClick()
    }

    SetupContainer {
        Step(fraction = 0.33f, text = stringResource(R.string.step_1_of_2))

        Text(
            stringResource(R.string.enable_ime_body),
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        Button(
            onClick = launchImeOptions,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(stringResource(R.string.open_input_method_settings))
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
        Step(fraction = 0.66f, text = stringResource(R.string.step_2_of_2))
        // TODO: Include some privacy statement
        Text(
            stringResource(R.string.grant_microphone_body),
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        Button(
            onClick = askMicAccess,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(stringResource(R.string.grant_microphone_permission))
        }
    }
}


@Composable
@Preview
fun SetupBlacklistedKeyboardWarning(
    info: BlacklistedInputMethod = BlacklistedInputMethod(
        "sample",
        "Example Keyboard is not compatible",
        "Let me continue"
    ), onClick: () -> Unit = { }
) {

    SetupContainer {
        Text(
            stringResource(R.string.incompatible_keyboard),
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            info.details,
            textAlign = TextAlign.Start,
            modifier = Modifier.fillMaxWidth(),
            style = Typography.Body.Regular
        )
        Button(
            onClick = onClick,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(info.dismiss)
        }
    }
}

@Composable
@Preview
fun SetupWrongDefaultWarning(
    info: DefaultVoiceInputIntent = DefaultVoiceInputIntent(
        kind = DefaultVoiceInputIntentKind.OTHER,
        name = ComponentName.unflattenFromString("com.google/.tts")
    ),
    onClick: () -> Unit = { }
) {
    SetupContainer {
        Text(
            stringResource(R.string.change_default_voice_input),
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            stringResource(R.string.wrong_default_body, info.name!!.packageName),
            textAlign = TextAlign.Start,
            modifier = Modifier.fillMaxWidth(),
            style = Typography.Body.Medium
        )

        val context = LocalContext.current
        Row {
            Button(
                onClick = { context.openSystemDefaultsSettings(info.name) },
                modifier = Modifier
                    .weight(2.0f)
                    .padding(6.dp, 16.dp)
            ) {
                Text(stringResource(R.string.clear_app_s_defaults))
            }
            Button(
                onClick = onClick,
                modifier = Modifier
                    .weight(1.5f)
                    .padding(6.dp, 16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondary,
                    contentColor = MaterialTheme.colorScheme.onSecondary
                )
            ) {
                Text(stringResource(R.string.dismiss))
            }
        }
    }
}
