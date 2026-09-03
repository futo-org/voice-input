package org.futo.voiceinput

import android.Manifest
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
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import org.futo.voiceinput.settings.pages.ConditionalUnpaidNoticeInVoiceInputWindow
import org.futo.voiceinput.shared.ModelDoesNotExistException
import org.futo.voiceinput.shared.RecognizerView
import org.futo.voiceinput.shared.RecognizerViewListener
import org.futo.voiceinput.shared.ui.MicrophoneDeviceState
import org.futo.voiceinput.theme.UixThemeAuto
import org.futo.voiceinput.updates.scheduleUpdateCheckingJob


@Composable
fun RecognizeWindow(forceNoUnpaidNotice: Boolean = false, allowClick: Boolean = false, onClose: (() -> Unit)?, onPauseVAD: (Boolean) -> Unit = { }, onFinish: () -> Unit = { }, content: @Composable () -> Unit) {
    UixThemeAuto {
        Surface(
            modifier = Modifier
                .recognizerSurfaceClickable(disabled = !allowClick, onPauseVAD = onPauseVAD, onFinish = onFinish)
                .width(280.dp)
                .wrapContentHeight()
                .heightIn(200.dp),
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
            }){}

            Box(
                modifier = Modifier
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                content()
            }

            Column {
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
            }


        }
    }
}

class RecognizeActivity : ComponentActivity() {
    private var recognizer: RecognizerView? = null
    private var onPermissionGranted: () -> Unit = {}
    private var onPermissionRejected: () -> Unit = {}

    fun initRecognizerView(): Boolean {
        val settings = loadRecognizerSettings(inlinePartialResult = true)
        try {
            recognizer = RecognizerView(
                context = this@RecognizeActivity,
                listener = object : RecognizerViewListener {
                    override fun cancelled() {
                        onCancel()
                    }

                    override fun recordingStarted(device: MicrophoneDeviceState) {

                    }

                    override fun finished(result: String) {
                        this@RecognizeActivity.sendResult(result)
                    }

                    override fun partialResult(result: String) {

                    }

                    override fun requestPermission(
                        onGranted: () -> Unit,
                        onRejected: () -> Unit
                    ): Boolean {
                        this@RecognizeActivity.onPermissionGranted = onGranted
                        this@RecognizeActivity.onPermissionRejected = onRejected
                        this@RecognizeActivity.requestPermission()
                        return true
                    }

                    override fun openSettings() {

                    }

                },
                settings = settings,
                lifecycleScope = this@RecognizeActivity.lifecycleScope,
                modelManager = CrashLoggingApplication.getModelManager(this@RecognizeActivity)!!,
            )
            return true
        } catch(e: ModelDoesNotExistException) {
            val models = listOf(
                settings.modelRunConfiguration.primaryModel
            ) + settings.modelRunConfiguration.languageSpecificModels.values

            startModelDownloadActivity(models)
            onCancel()
            return false
        }
    }

    private fun onCancel() {
        setResult(RESULT_CANCELED, null)
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if(recognizer == null) {
            if(!initRecognizerView()) {
                return
            }
        }
        recognizer!!.reset()
        scheduleUpdateCheckingJob(applicationContext)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        recognizer!!.start()

        setContent {
            //forceNoUnpaidNotice: Boolean = false, allowClick: Boolean = false, onClose: (() -> Unit)?, onPauseVAD: (Boolean) -> Unit = { }, onFinish: () -> Unit = { }, content: @Composable ColumnScope.() -> Unit
            RecognizeWindow(
                forceNoUnpaidNotice = false,
                allowClick = recognizer!!.isFinishable.value,
                onClose = { recognizer!!.cancel() },
                onPauseVAD = { v ->
                    recognizer!!.inhibitAutoStop(v)
                },
                onFinish = { recognizer!!.finish() }
            ) {
                recognizer!!.Content()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        recognizer!!.reset()
    }

    private val permission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        if(it){
            onPermissionGranted()
        } else {
            onPermissionRejected()
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