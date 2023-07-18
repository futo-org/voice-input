package org.futo.voiceinput.settings

import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
        Text(text, style= Typography.bodyMedium, modifier = Modifier.padding(2.dp, 4.dp))
    }

    val context = LocalContext.current
    val showAntiConfusionToast = {
        val toast = Toast.makeText(context, "This is just a demonstration of how Voice Input looks", Toast.LENGTH_SHORT)
        toast.show()
    }

    Screen("Help") {
        ScrollableList {
            textItem("You have installed Voice Input and enabled the Voice input method. You should now be able to use Voice Input within supported apps and keyboards.")
            textItem("When you open Voice Input, it will look something like this:")
            Column(modifier = Modifier.fillMaxWidth()) {
                Box(modifier = Modifier.align(CenterHorizontally)) {
                    RecognizeWindow(onClose = showAntiConfusionToast, forceNoUnpaidNotice = true) {
                        Text(
                            "Voice Input will look like this",
                            modifier = Modifier.align(CenterHorizontally),
                            textAlign = TextAlign.Center
                        )
                        Text("Look for the big off-center FUTO logo in the background!", style = Typography.bodyMedium, modifier = Modifier
                            .padding(2.dp, 4.dp)
                            .align(CenterHorizontally), textAlign = TextAlign.Center)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Column(modifier = Modifier.fillMaxWidth()) {
                Box(modifier = Modifier.align(CenterHorizontally)) {
                    RecognizeWindow(onClose = showAntiConfusionToast, forceNoUnpaidNotice = true) {
                        IconButton(onClick = showAntiConfusionToast, modifier = Modifier.align(CenterHorizontally)) {
                            Icon(
                                painter = painterResource(R.drawable.mic_2_),
                                contentDescription = "Stop Recording",
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.onSecondary
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Once you're done talking, you can hit the microphone button to stop", style = Typography.bodyMedium, modifier = Modifier
                            .padding(2.dp, 4.dp)
                            .align(CenterHorizontally), textAlign = TextAlign.Center)
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))

            textItem("You can use one of the following open-source keyboards that are tested to work:")
            Column(modifier = Modifier.padding(16.dp, 0.dp)) {
                textItem("• AOSP Keyboard, included in AOSP-based ROMs")
                textItem("• OpenBoard, available on F-Droid")
                textItem("• AnySoftKeyboard, available on F-Droid and Google Play")
            }

            Spacer(modifier = Modifier.height(24.dp))

            Tip("Everything you type is seen by your keyboard app, and proprietary commercial keyboards often have lengthy and complicated privacy policies. Choose carefully!")

            Spacer(modifier = Modifier.height(4.dp))

            textItem("The following proprietary keyboards may also work, but they are not recommended as they may not respect your privacy:")
            Column(modifier = Modifier.padding(16.dp, 0.dp)) {
                textItem("• Grammarly Keyboard")
                textItem("• Microsoft SwiftKey")
            }

            Spacer(modifier = Modifier.height(24.dp))

            Tip("Note: Not all keyboards are compatible with Voice Input. You need to make sure you're using a compatible keyboard.")

            Spacer(modifier = Modifier.height(4.dp))
            textItem("Some keyboards are simply incompatible, as they do not integrate with Android APIs for voice input. If your keyboard is listed here, you will need to use a different one as it is NOT compatible:")
            Column(modifier = Modifier.padding(16.dp, 0.dp)) {
                textItem("• Gboard")
                textItem("• TypeWise")
                textItem("• Simple Keyboard")
                textItem("• FlorisBoard")
                textItem("• Unexpected Keyboard")
            }

            Spacer(modifier = Modifier.height(16.dp))

            Tip("Some non-keyboard apps also support voice input! Look for a voice button in Firefox, Organic Maps, etc.")
            textItem("This app is still in development. Please report any issues or suggestions to ${stringResource(R.string.support_email)}")

            Spacer(modifier = Modifier.height(100.dp))
        }
    }
}
