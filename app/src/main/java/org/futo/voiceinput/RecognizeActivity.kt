package org.futo.voiceinput

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.SensorPrivacyManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.MicrophoneDirection
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.speech.RecognizerIntent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import androidx.core.app.ActivityCompat
import androidx.core.math.MathUtils.clamp
import androidx.lifecycle.lifecycleScope
import com.google.android.material.math.MathUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.futo.voiceinput.ml.Whisper
import org.futo.voiceinput.ui.theme.WhisperVoiceInputTheme
import org.tensorflow.lite.DataType
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer
import java.nio.FloatBuffer
import kotlin.math.pow
import kotlin.math.sqrt


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
fun InnerRecognize(onFinish: () -> Unit, magnitude: Float = 0.5f, hasTalked: Boolean = false, isMicBlocked: Boolean = false) {
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

    val text = if(isMicBlocked) {
        "No audio detected, is your microphone blocked?"
    } else {
        if(hasTalked) {
            "Listening..."
        } else {
            "Try saying something"
        }
    }
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
        InnerRecognize(onFinish = { }, isMicBlocked = true)
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

    private var isRecording = false
    private lateinit var recorder: AudioRecord

    private lateinit var model: Whisper

    private val floatSamples: FloatBuffer = FloatBuffer.allocate(16000 * 30)
    private var recorderJob: Job? = null
    private var modelJob: Job? = null

    private fun onFinish() {
        onFinishRecording()
    }

    private fun onCancel() {
        recorderJob?.cancel()
        modelJob?.cancel()
        isRecording = false
        recorder.stop()
        
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

        WhisperTokenizer.init(this)

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

    private fun sendResult(result: String) {
        val returnIntent = Intent()

        val results = listOf(result)
        returnIntent.putStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS, ArrayList(results))
        setResult(RESULT_OK, returnIntent)
        finish()
    }

    private fun startRecording(){
        setContent {
            RecognizeWindow(onClose = { onCancel() }) {
                InnerRecognize(onFinish = { onFinish() }, magnitude = 0.0f)
            }
        }

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

            val canMicBeBlocked = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                (applicationContext.getSystemService(SensorPrivacyManager::class.java) as SensorPrivacyManager).supportsSensorToggle(
                    SensorPrivacyManager.Sensors.MICROPHONE
                )
            } else {
                false
            }

            recorderJob = lifecycleScope.launch {
                withContext(Dispatchers.Default) {
                    var hasTalked = false
                    var anyNoiseAtAll = false
                    var isMicBlocked = false


                    while(isRecording && recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING){
                        val samples = FloatArray(1600)

                        val nRead = recorder.read(samples, 0, 1600, AudioRecord.READ_BLOCKING)

                        if(nRead <= 0) break
                        if(!isRecording || recorder.recordingState != AudioRecord.RECORDSTATE_RECORDING) break

                        if(floatSamples.remaining() < 1600) {
                            withContext(Dispatchers.Main){ onFinish() }
                            break
                        }
                        floatSamples.put(samples)

                        val rms = sqrt(samples.sumOf { (it * it).toDouble() } / samples.size).toFloat()
                        if(rms > 0.01) hasTalked = true

                        if(rms > 0.0001){
                            anyNoiseAtAll = true
                            isMicBlocked = false
                        }

                        // Check if mic is blocked
                        if(!anyNoiseAtAll && canMicBeBlocked && (floatSamples.position() > 2*16000)){
                            isMicBlocked = true
                        }

                        val magnitude = (1.0f - 0.1f.pow(24.0f * rms))

                        // TODO: This seems like it might not be the most efficient way
                        withContext(Dispatchers.Main) {
                            setContent {
                                RecognizeWindow(onClose = { onCancel() }) {
                                    InnerRecognize(onFinish = { onFinish() }, magnitude = magnitude, hasTalked = hasTalked, isMicBlocked = isMicBlocked)
                                }
                            }
                        }

                        // Skip ahead as much as possible, in case we are behind (taking more than
                        // 100ms to process 100ms)
                        while(true){
                            val nRead2 = recorder.read(samples, 0, 1600, AudioRecord.READ_NON_BLOCKING)
                            if(nRead2 > 0) {
                                if(floatSamples.remaining() < nRead2){
                                    withContext(Dispatchers.Main){ onFinish() }
                                    break
                                }
                                floatSamples.put(samples.sliceArray(0 until nRead2))
                            } else {
                                break
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

    private fun runModel(){
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
        recorderJob?.cancel()

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

        modelJob = lifecycleScope.launch {
            withContext(Dispatchers.Default) {
                runModel()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
    }
}