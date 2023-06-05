package org.futo.voiceinput

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.MicrophoneDirection
import android.net.Uri
import android.os.AsyncTask
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.speech.RecognizerIntent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.futo.voiceinput.ml.Whisper
import org.futo.voiceinput.ui.theme.WhisperVoiceInputTheme
import org.tensorflow.lite.DataType
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer
import java.nio.ByteBuffer
import java.nio.FloatBuffer
import java.util.Timer
import java.util.TimerTask
import kotlin.math.ln
import kotlin.math.log
import kotlin.math.log10
import kotlin.math.sqrt


@Composable
fun InnerRecognize(onFinish: () -> Unit, magnitude: Float = 100.5f, hasTalked: Boolean = false) {
    IconButton(
        onClick = onFinish, modifier = Modifier
            .fillMaxWidth()
            .height(80.dp)
            .padding(16.dp)
    ) {
        val size by animateFloatAsState(targetValue = magnitude, animationSpec = tween(durationMillis = 100, easing = LinearEasing))
        Canvas( modifier = Modifier.fillMaxSize() ) {
            drawCircle(color = Color.White, radius = size, alpha = 0.1f)
        }
        Icon(
            painter = painterResource(R.drawable.mic_2_),
            contentDescription = "Stop Recording",
            modifier = Modifier.size(48.dp)
        )

    }

    val text = if(hasTalked) { "Listening..." } else { "Try saying something" }
    Text(
        text,
        modifier = Modifier.fillMaxWidth(),
        textAlign = TextAlign.Center
    )
}

@Composable
fun RecognizeWindow(onClose: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    WhisperVoiceInputTheme {
        Surface(
            modifier = Modifier
                .width(256.dp)
                .wrapContentHeight(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column{
                IconButton( onClick = onClose, modifier = Modifier.align(Alignment.End) ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Cancel"
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
    private val PERMISSION_CODE = 904151;

    private var isRecording = false
    private lateinit var recorder: AudioRecord
    private var timeoutTimer = Timer()

    // somehow cache this so we don't load every time activity is started?
    private lateinit var model: Whisper

    private fun onFinish() {
        onFinishRecording()
    }

    private fun onCancel() {
        resetTimer()
        setResult(RESULT_CANCELED, null)
        finish()
    }

    private fun openPermissionSettings() {
        val myAppSettings = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse(
                "package:$packageName"
            )
        )
        myAppSettings.addCategory(Intent.CATEGORY_DEFAULT)
        myAppSettings.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        startActivity(myAppSettings)

        onCancel()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            RecognizeWindow(onClose = { onCancel() }) {
                RecognizeLoadingCircle()
            }
        }

        WhisperTokenizer.init(this);

        recorder = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            16000,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT, // could use ENCODING_PCM_FLOAT
            16000 * 2 * 30
        )

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            // request permission and call startRecording once granted, or exit activity if denied
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), PERMISSION_CODE)
        }else{
            startRecording()
        }

        // TODO: Use service or something to avoid recreating model each time
        model = Whisper.newInstance(this)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if(requestCode == PERMISSION_CODE){
            for (i in permissions.indices) {
                val permission = permissions[i]
                val grantResult = grantResults[i]
                if (permission == Manifest.permission.RECORD_AUDIO) {
                    if (grantResult == PackageManager.PERMISSION_GRANTED) {
                        startRecording()
                    } else {
                        // show dialog saying cannot do speech to text without mic permission?
                        setContent {
                            RecognizeWindow(onClose = { onCancel() }) {
                                RecognizeMicError(openSettings = { openPermissionSettings() })
                            }
                        }
                    }
                }
            }
        }
    }

    fun sendResult(result: String) {
        val returnIntent = Intent()

        val results = listOf(result);
        returnIntent.putStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS, ArrayList(results))
        setResult(RESULT_OK, returnIntent)
        finish()
    }

    private var timer = Timer()
    fun resetTimer() {
        timer.cancel()
        timer = Timer()
    }

    val floatSamples = FloatBuffer.allocate(16000 * 30)
    var magnitudeJob: Job? = null
    fun startRecording(){
        setContent {
            RecognizeWindow(onClose = { onCancel() }) {
                InnerRecognize(onFinish = { onFinish() }, magnitude = 0.0f)
            }
        }

        timer.schedule(object : TimerTask() {
            override fun run() = onFinish()
        }, 30_000L)

        // play a boop sound

        try {
            recorder = AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                16000,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_FLOAT, // could use ENCODING_PCM_FLOAT
                16000 * 2 * 5
            )

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                recorder.setPreferredMicrophoneDirection(MicrophoneDirection.MIC_DIRECTION_TOWARDS_USER)
            }

            recorder.startRecording()

            isRecording = true

            // TODO: When silence for a while, stop recording

            magnitudeJob = lifecycleScope.launch {
                withContext(Dispatchers.Default) {
                    var magSmooth = 0.0f;
                    var hasTalked = false;
                    while(isRecording && recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING){
                        val samples = FloatArray(1600)

                        // TODO: This can get left behind, try skipping forward to latest audio rather than 1600 by 1600
                        val nRead = recorder.read(samples, 0, 1600, AudioRecord.READ_BLOCKING)

                        if(nRead <= 0) break;
                        if(!isRecording || recorder.recordingState != AudioRecord.RECORDSTATE_RECORDING) break;

                        floatSamples.put(samples)

                        val rms = sqrt(samples.sumOf { (it * it).toDouble() } / samples.size).toFloat()
                        if(rms > 0.02) hasTalked = true

                        val magnitude = log10(256.0f * rms + 1.0f) * 180.0f + 72.0f;

                        magSmooth = magnitude

                        // TODO: This seems like it might not be the most efficient way
                        withContext(Dispatchers.Main) {
                            setContent {
                                RecognizeWindow(onClose = { onCancel() }) {
                                    InnerRecognize(onFinish = { onFinish() }, magnitude = magSmooth, hasTalked = hasTalked)
                                }
                            }
                        }
                    }
                }
            }
        } catch(e: SecurityException){
            // this should not be reached, as this function should never be called without
            // permission.
            e.printStackTrace()
        }
    }

    fun runModel(){
        val extractor =
            AudioFeatureExtraction()
        extractor.hop_length = 160
        extractor.n_fft = 512
        extractor.sampleRate = 16000.0
        extractor.n_mels = 80


        val mel = FloatArray(80 * 3000)
        val data = extractor.melSpectrogram(floatSamples.array())
        for (i in 0..79) {
            for (j in data[i].indices) {
                if((i * 3000 + j) >= (80 * 3000)) {
                    continue
                }
                mel[i * 3000 + j] = ((extractor.log10(
                    Math.max(
                        0.000000001,
                        data[i][j]
                    )
                ) + 4.0) / 4.0).toFloat()
            }
        }

        // Creates inputs for reference.
        val inputFeature0 = TensorBuffer.createFixedSize(intArrayOf(1, 80, 3000), DataType.FLOAT32)
        inputFeature0.loadArray(mel)

        // Runs model inference and gets result.
        val outputs: Whisper.Outputs = model.process(inputFeature0)
        val outputFeature0 = outputs.outputFeature0AsTensorBuffer

        val text = WhisperTokenizer.convertTokensToString(outputFeature0)

        sendResult(text)
    }

    private fun onFinishRecording() {
        resetTimer()
        magnitudeJob?.cancel()

        if(!isRecording) {
            throw IllegalStateException("Should not call onFinishRecording when not recording")
        }

        isRecording = false
        recorder.stop()

        setContent {
            RecognizeWindow(onClose = { onCancel() }) {
                RecognizeLoadingCircle()
            }
        }

        lifecycleScope.launch {
            withContext(Dispatchers.Default) {
                runModel()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        // popupWindow
    }
}