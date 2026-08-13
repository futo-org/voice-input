package org.futo.voiceinput

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.LifecycleCoroutineScope
import androidx.lifecycle.lifecycleScope
import org.futo.voiceinput.migration.scheduleModelMigrationJob
import org.futo.voiceinput.settings.pages.ConditionalUnpaidNoticeInVoiceInputWindow
import org.futo.voiceinput.theme.UixThemeAuto
import org.futo.voiceinput.updates.scheduleUpdateCheckingJob
import java.lang.ref.WeakReference


@Composable
fun RecognizeWindow(forceNoUnpaidNotice: Boolean = false, allowClick: Boolean = false, onClose: (() -> Unit)?, onPauseVAD: (Boolean) -> Unit = { }, onFinish: () -> Unit = { }, content: @Composable ColumnScope.() -> Unit) {
    UixThemeAuto {
        Surface(
            modifier = Modifier
                .recognizerSurfaceClickable(disabled = !allowClick, onPauseVAD = onPauseVAD, onFinish = onFinish)
                .width(280.dp)
                .wrapContentHeight(),
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(8.dp)
        ) {
            val icon = painterResource(id = R.drawable.futo_o)
            val bgIconTint = MaterialTheme.colorScheme.outline

            Column(modifier = Modifier.drawBehind {
                with(icon) {
                    translate(left = -icon.intrinsicSize.width/2, top = -icon.intrinsicSize.height/2) {
                        translate(left = size.width / 4, top = size.height / 3) {
                            draw(icon.intrinsicSize, colorFilter = ColorFilter.tint(bgIconTint))
                        }
                    }
                }
            }){
                Box(modifier = Modifier.fillMaxWidth()) {
                    if(!forceNoUnpaidNotice) {
                        Box(modifier = Modifier.align(Alignment.CenterStart)) {
                            ConditionalUnpaidNoticeInVoiceInputWindow(onClose)
                        }
                    }

                    Box(modifier = Modifier.align(Alignment.CenterEnd)) {
                        if (onClose != null) {
                            IconButton(
                                onClick = onClose
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Cancel",
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        } else {
                            Spacer(modifier = Modifier.height(32.dp))
                        }
                    }
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
        InnerRecognize()
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
    companion object {
        // This activity used to be declared launchMode="singleInstance" to guarantee there is only
        // ever one recognizer window. That is incompatible with startActivityForResult, which is
        // how every ACTION_RECOGNIZE_SPEECH caller starts us (see the comment in the manifest), so
        // the guarantee is enforced here instead: a new recognizer tears down the previous one.
        private var currentInstance: WeakReference<RecognizeActivity>? = null
    }

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

        override fun sendPartialResult(result: String): Boolean {
            return false
        }

        override fun requestPermission() {
            this@RecognizeActivity.requestPermission()
        }

        override fun decodingStarted() {
            
        }

        @Composable
        override fun Window(onClose: () -> Unit, allowClick: Boolean, onPauseVAD: (Boolean) -> Unit, onFinish: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
            RecognizeWindow(onClose = onClose, onPauseVAD = onPauseVAD, onFinish = onFinish, allowClick = allowClick) {
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

        // Stop any recognizer that is still up before we grab the microphone, otherwise the old
        // AudioRecord is still holding it when startRecording runs.
        currentInstance?.get()?.let { previous ->
            if (previous !== this && !previous.isFinishing) {
                previous.recognizer.reset()
                previous.finish()
            }
        }
        currentInstance = WeakReference(this)

        recognizer.reset()
        recognizer.init()
        scheduleUpdateCheckingJob(applicationContext)
        scheduleModelMigrationJob(applicationContext)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Stay out of the input-method pipeline entirely. Without this flag, this window becoming
        // focused makes it the IME target, which deactivates the calling editor's InputConnection
        // for the whole dictation. The editor is only reactivated after we finish() and it regains
        // window focus - but keyboards deliver our RESULT_OK and insert the text within a few
        // dozen milliseconds of finish(), racing that reactivation. When the insert loses the
        // race, the app-side InputConnection silently discards it and the transcription is lost
        // (observed as e.g. "beginBatchEdit on inactive InputConnection" from the target app).
        // With this flag the editor's InputConnection stays active while the recognizer is up, so
        // the keyboard's insert always lands no matter the timing. This window has no text input
        // of its own, so opting out of the IME costs nothing.
        window.addFlags(WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM)
    }

    override fun onDestroy() {
        super.onDestroy()

        if (currentInstance?.get() === this) {
            currentInstance = null
        }

        recognizer.reset()
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
        returnIntent.putExtra(RecognizerIntent.EXTRA_CONFIDENCE_SCORES, floatArrayOf(1.0f))
        setResult(RESULT_OK, returnIntent)
        finish()
    }
}