package org.futo.voiceinput.settings

import android.app.PendingIntent
import android.content.Intent
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Alignment.Companion.CenterHorizontally
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.futo.voiceinput.R
import org.futo.voiceinput.RecognizeWindow
import org.futo.voiceinput.Screen
import org.futo.voiceinput.ui.theme.Typography


@Composable
@Preview
fun HelpScreen() {
    val textItem: @Composable (text: String) -> Unit = { text ->
        Text(text, style = Typography.bodyMedium, modifier = Modifier.padding(2.dp, 4.dp))
    }

    val context = LocalContext.current
    val antiConfusionText = stringResource(R.string.just_a_demonstration)
    val showAntiConfusionToast = {
        val toast = Toast.makeText(
            context,
            antiConfusionText,
            Toast.LENGTH_SHORT
        )
        toast.show()
    }

    Screen(stringResource(R.string.help_title)) {
        ScrollableList {
            textItem(stringResource(R.string.help_paragraph_1))
            textItem(stringResource(R.string.help_paragraph_2))
            Column(modifier = Modifier.fillMaxWidth()) {
                Box(modifier = Modifier.align(CenterHorizontally)) {
                    RecognizeWindow(onClose = showAntiConfusionToast, forceNoUnpaidNotice = true) {
                        Text(
                            stringResource(R.string.voice_input_will_look_like_this),
                            modifier = Modifier.align(CenterHorizontally),
                            textAlign = TextAlign.Center
                        )
                        Text(
                            stringResource(R.string.look_for_the_big_off_center_futo_logo_in_the_background),
                            style = Typography.bodyMedium,
                            modifier = Modifier
                                .padding(2.dp, 4.dp)
                                .align(CenterHorizontally),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Column(modifier = Modifier.fillMaxWidth()) {
                Box(modifier = Modifier.align(CenterHorizontally)) {
                    RecognizeWindow(onClose = showAntiConfusionToast, forceNoUnpaidNotice = true) {
                        IconButton(
                            onClick = showAntiConfusionToast,
                            modifier = Modifier.align(CenterHorizontally)
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.mic_2_),
                                contentDescription = stringResource(R.string.stop_recording),
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.onSecondary
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            stringResource(R.string.once_you_re_done_talking_you_can_hit_the_microphone_button_to_stop),
                            style = Typography.bodyMedium,
                            modifier = Modifier
                                .padding(2.dp, 4.dp)
                                .align(CenterHorizontally),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))

            textItem(stringResource(R.string.help_paragraph_3))
            Column(modifier = Modifier.padding(16.dp, 0.dp)) {
                textItem(stringResource(R.string.aosp_keyboard_included_in_aosp_based_roms))
                textItem(stringResource(R.string.openboard_available_on_f_droid))
                textItem(stringResource(R.string.anysoftkeyboard_available_on_f_droid_and_google_play))
            }

            Spacer(modifier = Modifier.height(24.dp))

            Tip(stringResource(R.string.help_paragraph_5))

            Spacer(modifier = Modifier.height(4.dp))

            textItem(stringResource(R.string.help_paragraph_6))
            Column(modifier = Modifier.padding(16.dp, 0.dp)) {
                textItem(stringResource(R.string.grammarly_keyboard))
                textItem(stringResource(R.string.microsoft_swiftkey))
            }

            Spacer(modifier = Modifier.height(24.dp))

            Tip(stringResource(R.string.help_paragraph_7))

            Spacer(modifier = Modifier.height(4.dp))
            textItem(stringResource(R.string.help_paragraph_8))
            Column(modifier = Modifier.padding(16.dp, 0.dp)) {
                textItem(stringResource(R.string.gboard))
                textItem(stringResource(R.string.samsung_keyboard_one_ui_5))
                textItem(stringResource(R.string.typewise))
                textItem(stringResource(R.string.simple_keyboard))
                textItem(stringResource(R.string.unexpected_keyboard))
            }

            Spacer(modifier = Modifier.height(16.dp))

            Tip(stringResource(R.string.help_paragraph_9))

            Spacer(modifier = Modifier.height(16.dp))

            textItem(stringResource(R.string.wrong_voice_input_body))
            Column(modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = { openImeOptions(context) },
                    modifier = Modifier.align(CenterHorizontally)
                ) {
                    Text(stringResource(R.string.open_input_method_settings))
                }
            }

            Spacer(modifier = Modifier.height(32.dp))


            textItem(stringResource(R.string.help_with_email))
            ShareFeedbackOption()

            Spacer(modifier = Modifier.height(100.dp))
        }
    }
}
