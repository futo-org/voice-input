package org.futo.voiceinput

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.math.MathUtils.clamp
import androidx.lifecycle.LifecycleCoroutineScope
import com.google.android.material.math.MathUtils
import kotlinx.coroutines.launch
import org.futo.voiceinput.ui.theme.WhisperVoiceInputTheme

@Composable
fun AnimatedRecognizeCircle(magnitude: Float = 0.5f) {
    var radius by remember { mutableStateOf(0.0f) }
    var lastMagnitude by remember { mutableStateOf(0.0f) }

    LaunchedEffect(magnitude) {
        val lastMagnitudeValue = lastMagnitude
        if (lastMagnitude != magnitude) {
            lastMagnitude = magnitude
        }

        launch {
            val startTime = withFrameMillis { it }

            while (true) {
                val time = withFrameMillis { frameTime ->
                    val t = (frameTime - startTime).toFloat() / 100.0f

                    val t1 = clamp(t * t * (3f - 2f * t), 0.0f, 1.0f)

                    radius = MathUtils.lerp(lastMagnitudeValue, magnitude, t1)

                    frameTime
                }
                if(time > (startTime + 100)) break
            }
        }
    }

    Canvas( modifier = Modifier.fillMaxSize() ) {
        // TODO: This seems to scale differently on 2 different devices
        drawCircle(color = Color.White, radius = radius * 256.0f + 128.0f, alpha = 0.1f)
    }
}

@Composable
fun InnerRecognize(onFinish: () -> Unit, magnitude: Float = 0.5f, state: MagnitudeState = MagnitudeState.MIC_MAY_BE_BLOCKED) {
    IconButton(
        onClick = onFinish, modifier = Modifier
            .fillMaxWidth()
            .height(80.dp)
            .padding(16.dp)
    ) {
        AnimatedRecognizeCircle(magnitude = magnitude)
        Icon(
            painter = painterResource(R.drawable.mic_2_),
            contentDescription = "Stop Recording",
            modifier = Modifier.size(48.dp)
        )

    }

    val text = when(state) {
        MagnitudeState.NOT_TALKED_YET -> "Try saying something"
        MagnitudeState.MIC_MAY_BE_BLOCKED -> "No audio detected, is your microphone blocked?"
        MagnitudeState.TALKING -> "Listening..."
    }

    Text(
        text,
        modifier = Modifier.fillMaxWidth(),
        textAlign = TextAlign.Center
    )
}


@Composable
fun ColumnScope.RecognizeLoadingCircle() {
    CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally) )
}

@Composable
fun ColumnScope.RecognizeMicError(openSettings: () -> Unit) {
    Text("Grant microphone permission to use Voice Input",
        modifier = Modifier
            .padding(8.dp, 2.dp)
            .align(Alignment.CenterHorizontally),
        textAlign = TextAlign.Center)
    IconButton(onClick = { openSettings() }, modifier = Modifier
        .padding(4.dp)
        .align(Alignment.CenterHorizontally)
        .size(64.dp)) {
        Icon(Icons.Default.Settings, contentDescription = "Open Voice Input Settings", modifier = Modifier.size(32.dp))
    }
}

abstract class RecognizerView {
    protected abstract val context: Context get
    protected abstract val lifecycleScope: LifecycleCoroutineScope get

    abstract fun setContent(content: @Composable () -> Unit)

    abstract fun onCancel()
    abstract fun sendResult(result: String)
    abstract fun requestPermission()

    @Composable
    abstract fun window(onClose: () -> Unit, content: @Composable ColumnScope.() -> Unit)

    protected val recognizer = object : AudioRecognizer() {
        override val context: Context
            get() = this@RecognizerView.context
        override val lifecycleScope: LifecycleCoroutineScope
            get() = this@RecognizerView.lifecycleScope

        override fun cancelled() {
            onCancel()
        }

        override fun finished(result: String) {
            sendResult(result)
        }

        override fun loading() {
            setContent {
                this@RecognizerView.window(onClose = { cancelRecognizer() }) {
                    RecognizeLoadingCircle()
                }
            }
        }

        override fun needPermission() {
            requestPermission()
        }

        override fun permissionRejected() {
            setContent {
                this@RecognizerView.window(onClose = { cancelRecognizer() }) {
                    RecognizeMicError(openSettings = { openPermissionSettings() })
                }
            }
        }

        override fun recordingStarted() {
            // wait until updateMagnitude is called
            setContent {
                this@RecognizerView.window(onClose = { cancelRecognizer() }) {
                    RecognizeLoadingCircle()
                }
            }
        }

        override fun updateMagnitude(magnitude: Float, state: MagnitudeState) {
            setContent {
                this@RecognizerView.window(onClose = { cancelRecognizer() }) {
                    InnerRecognize(onFinish = { finishRecognizer() }, magnitude = magnitude, state = state)
                }
            }
        }

        override fun processing() {
            setContent {
                this@RecognizerView.window(onClose = { cancelRecognizer() }) {
                    RecognizeLoadingCircle()
                }
            }
        }
    }

    fun init() {
        recognizer.create()
    }

    fun permissionResultGranted() {
        recognizer.permissionResultGranted()
    }

    fun permissionResultRejected() {
        recognizer.permissionResultRejected()
    }
}
