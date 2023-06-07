package org.futo.voiceinput

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.SensorPrivacyManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.MicrophoneDirection
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.lifecycle.LifecycleCoroutineScope
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.futo.voiceinput.ml.Whisper
import org.tensorflow.lite.DataType
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer
import java.nio.FloatBuffer
import kotlin.math.pow
import kotlin.math.sqrt

enum class MagnitudeState {
    NOT_TALKED_YET,
    MIC_MAY_BE_BLOCKED,
    TALKING
}

abstract class AudioRecognizer {
    private var isRecording = false
    private var recorder: AudioRecord? = null

    private var model: Whisper? = null

    private val floatSamples: FloatBuffer = FloatBuffer.allocate(16000 * 30)
    private var recorderJob: Job? = null
    private var modelJob: Job? = null

    protected abstract val context: Context get
    protected abstract val lifecycleScope: LifecycleCoroutineScope get

    protected abstract fun cancelled()
    protected abstract fun finished(result: String)

    protected abstract fun loading()
    protected abstract fun needPermission()
    protected abstract fun permissionRejected()

    protected abstract fun recordingStarted()
    protected abstract fun updateMagnitude(magnitude: Float, state: MagnitudeState)

    protected abstract fun processing()


    protected fun finishRecognizer() {
        onFinishRecording()
    }

    protected fun cancelRecognizer() {
        recorderJob?.cancel()
        modelJob?.cancel()
        isRecording = false
        recorder?.stop()

        cancelled()
    }


    protected fun openPermissionSettings() {
        val packageName = context.packageName
        val myAppSettings = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse(
                "package:$packageName"
            )
        )
        myAppSettings.addCategory(Intent.CATEGORY_DEFAULT)
        myAppSettings.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        context.startActivity(myAppSettings)

        cancelRecognizer()
    }

    fun create() {
        loading()

        if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            // request permission and call startRecording once granted, or exit activity if denied
            needPermission()
        }else{
            startRecording()
        }

        // TODO: Use service or something to avoid recreating model each time
        // TODO: Move this to a coroutine, it should finish by the time the recording is done
        if(model == null) {
            println("Creating model instance")
            WhisperTokenizer.init(context)
            model = Whisper.newInstance(context)
        }
    }

    fun permissionResultGranted() {
        startRecording()
    }

    fun permissionResultRejected() {
        permissionRejected()
    }

    protected fun startRecording(){
        try {
            recorder = AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                16000,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_FLOAT, // could use ENCODING_PCM_FLOAT
                16000 * 2 * 5
            )

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                recorder!!.setPreferredMicrophoneDirection(MicrophoneDirection.MIC_DIRECTION_TOWARDS_USER)
            }

            recorder!!.startRecording()

            isRecording = true

            val canMicBeBlocked = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                (context.getSystemService(SensorPrivacyManager::class.java) as SensorPrivacyManager).supportsSensorToggle(
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


                    while(isRecording && recorder!!.recordingState == AudioRecord.RECORDSTATE_RECORDING){
                        val samples = FloatArray(1600)

                        val nRead = recorder!!.read(samples, 0, 1600, AudioRecord.READ_BLOCKING)

                        if(nRead <= 0) break
                        if(!isRecording || recorder!!.recordingState != AudioRecord.RECORDSTATE_RECORDING) break

                        if(floatSamples.remaining() < 1600) {
                            withContext(Dispatchers.Main){ finishRecognizer() }
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

                        val state = if(hasTalked) {
                            MagnitudeState.TALKING
                        } else if(isMicBlocked) {
                            MagnitudeState.MIC_MAY_BE_BLOCKED
                        } else {
                            MagnitudeState.NOT_TALKED_YET
                        }

                        withContext(Dispatchers.Main) {
                            updateMagnitude(magnitude, state)
                        }

                        // Skip ahead as much as possible, in case we are behind (taking more than
                        // 100ms to process 100ms)
                        while(true){
                            val nRead2 = recorder!!.read(samples, 0, 1600, AudioRecord.READ_NON_BLOCKING)
                            if(nRead2 > 0) {
                                if(floatSamples.remaining() < nRead2){
                                    withContext(Dispatchers.Main){ finishRecognizer() }
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

            // TODO: play a boop sound

            recordingStarted()
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
        // TODO: Iterative decoding?
        val outputs: Whisper.Outputs = model!!.process(inputFeature0)
        val outputFeature0 = outputs.outputFeature0AsTensorBuffer

        val text = WhisperTokenizer.convertTokensToString(outputFeature0)

        lifecycleScope.launch {
            withContext(Dispatchers.Main) {
                finished(text)
            }
        }
    }

    private fun onFinishRecording() {
        recorderJob?.cancel()

        if(!isRecording) {
            throw IllegalStateException("Should not call onFinishRecording when not recording")
        }

        isRecording = false
        recorder?.stop()

        processing()

        modelJob = lifecycleScope.launch {
            withContext(Dispatchers.Default) {
                runModel()
            }
        }
    }
}