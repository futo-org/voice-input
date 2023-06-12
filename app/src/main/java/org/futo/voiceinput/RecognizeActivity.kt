package org.futo.voiceinput

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.LifecycleCoroutineScope
import androidx.lifecycle.lifecycleScope
import org.futo.voiceinput.ui.theme.WhisperVoiceInputTheme


@Composable
fun RecognizeWindow(onClose: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    WhisperVoiceInputTheme {
        Surface(
            modifier = Modifier
                .width(280.dp)
                .wrapContentHeight(),
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(8.dp)
        ) {
            val icon = painterResource(id = R.drawable.futo_o)

            Column(modifier = Modifier.drawBehind {
                with(icon) {
                    translate(left = -icon.intrinsicSize.width/2, top = -icon.intrinsicSize.height/2) {
                        translate(left = size.width / 4, top = size.height / 3) {
                            draw(icon.intrinsicSize)

                        }
                    }
                }
            }){
                IconButton( onClick = onClose, modifier = Modifier.align(Alignment.End) ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Cancel",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(0.dp, 0.dp, 0.dp, 40.dp)
                ) {
                    content()
                }
            }
        }
    }
}

@Preview
@Composable
fun RecognizeLoadingPreview() {
    RecognizeWindow(onClose = { }) {
        RecognizeLoadingCircle()
    }
}

@Preview
@Composable
fun PreviewRecognizeViewLoaded() {
    RecognizeWindow(onClose = { }) {
        InnerRecognize(onFinish = { })
    }
}
@Preview
@Composable
fun PreviewRecognizeViewNoMic() {
    RecognizeWindow(onClose = { }) {
        RecognizeMicError(openSettings = { })
    }
}

class RecognizeActivity : ComponentActivity() {
    private val PERMISSION_CODE = 904151

    private val recognizer = object : RecognizerView() {
        override val context: Context
            get() = this@RecognizeActivity
        override val lifecycleScope: LifecycleCoroutineScope
            get() = this@RecognizeActivity.lifecycleScope

        override fun setContent(content: @Composable () -> Unit) {
            this@RecognizeActivity.setContent { content() }
        }

        override fun onCancel() {
            this@RecognizeActivity.onCancel()
        }

        override fun sendResult(result: String) {
            this@RecognizeActivity.sendResult(result)
        }

        override fun requestPermission() {
            this@RecognizeActivity.requestPermission()
        }

        @Composable
        override fun window(onClose: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
            RecognizeWindow(onClose = onClose) {
                content()
            }
        }
    }
    private fun onCancel() {
        setResult(RESULT_CANCELED, null)
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        recognizer.reset()
        recognizer.init()
    }

    private val permission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        if(it){
            recognizer.permissionResultGranted()
        } else {
            recognizer.permissionResultRejected()
        }
    }
    private fun requestPermission() {
        permission.launch(Manifest.permission.RECORD_AUDIO)
    }

    private fun sendResult(result: String) {
        val returnIntent = Intent()

        val results = listOf(result)
        returnIntent.putStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS, ArrayList(results))
        setResult(RESULT_OK, returnIntent)
        finish()
    }
}