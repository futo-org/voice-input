package org.futo.voiceinput

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.SensorPrivacyManager
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.MicrophoneDirection
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.lifecycle.LifecycleCoroutineScope
import com.konovalov.vad.Vad
import com.konovalov.vad.config.FrameSize
import com.konovalov.vad.config.Mode
import com.konovalov.vad.config.Model
import com.konovalov.vad.config.SampleRate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import org.futo.voiceinput.ggml.DecodingMode
import org.futo.voiceinput.ml.RunState
import org.futo.voiceinput.ml.WhisperModelWrapper
import org.futo.voiceinput.settings.BEAM_SEARCH
import org.futo.voiceinput.settings.DISALLOW_SYMBOLS
import org.futo.voiceinput.settings.ENABLE_MULTILINGUAL
import org.futo.voiceinput.settings.ENGLISH_MODEL_INDEX
import org.futo.voiceinput.settings.IS_VAD_ENABLED
import org.futo.voiceinput.settings.LANGUAGE_TOGGLES
import org.futo.voiceinput.settings.MULTILINGUAL_MODEL_INDEX
import org.futo.voiceinput.settings.PERSONAL_DICTIONARY
import org.futo.voiceinput.settings.USE_LANGUAGE_SPECIFIC_MODELS
import org.futo.voiceinput.settings.getSetting
import java.io.IOException
import java.nio.FloatBuffer
import java.nio.ShortBuffer
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

enum class MagnitudeState {
    NOT_TALKED_YET,
    MIC_MAY_BE_BLOCKED,
    TALKING,
    ENDING_SOON_VAD,
    ENDING_SOON_30S
}

abstract class AudioRecognizer {
    private var isRecording = false
    private var recorder: AudioRecord? = null

    fun isCurrentlyRecording(): Boolean {
        return isRecording
    }

    private var model: WhisperModelWrapper? = null

    private val floatSamples: FloatBuffer = FloatBuffer.allocate(16000 * 30)
    private var recorderJob: Job? = null
    private var modelJob: Job? = null
    private var loadModelJob: Job? = null


    protected abstract val context: Context
    protected abstract val lifecycleScope: LifecycleCoroutineScope

    protected abstract fun cancelled()
    protected abstract fun finished(result: String)
    protected abstract fun languageDetected(result: String)
    protected abstract fun partialResult(result: String)
    protected abstract fun decodingStatus(status: RunState)

    protected abstract fun loading()
    protected abstract fun needPermission()
    protected abstract fun permissionRejected()

    protected abstract fun recordingStarted()
    protected abstract fun updateMagnitude(magnitude: Float, state: MagnitudeState)

    protected abstract fun processing()

    fun finishRecognizerIfRecording() {
        if (isRecording) {
            finishRecognizer()
        }
    }

    protected fun finishRecognizer() {
        println("Finish called")
        onFinishRecording()
    }

    fun cancelRecognizer() {
        println("Cancelling recognition")
        reset()

        cancelled()
    }

    fun reset() {
        recorder?.stop()
        recorderJob?.cancel()
        modelJob?.cancel()
        isRecording = false

        floatSamples.clear()

        unfocusAudio()

        lifecycleScope.launch {
            modelJob?.join()
            model?.close()
            model = null
        }
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

    private var focusRequest: AudioFocusRequest? = null
    private fun focusAudio() {
        unfocusAudio()
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                focusRequest =
                    AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
                        .build()
                audioManager.requestAudioFocus(focusRequest!!)
            }
        }catch(e: Exception) {
            e.printStackTrace()
        }
    }
    private fun unfocusAudio() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                if (focusRequest != null) {
                    audioManager.abandonAudioFocusRequest(focusRequest!!)
                }
                focusRequest = null
            }
        }catch(e: Exception) {
            e.printStackTrace()
        }
    }

    private suspend fun tryLoadModelOrCancel(primaryModel: ModelData, secondaryModelP: ModelData?) {
        val secondaryModel = if(context.getSetting(USE_LANGUAGE_SPECIFIC_MODELS)) { secondaryModelP } else { null }
        try {
            model = WhisperModelWrapper(
                context,
                primaryModel,
                secondaryModel,
                context.getSetting(DISALLOW_SYMBOLS),
                context.getSetting(LANGUAGE_TOGGLES),
                onStatusUpdate = {
                    decodingStatus(it)
                },
                onPartialDecode = {
                    lifecycleScope.launch {
                        withContext(Dispatchers.Main) {
                            partialResult(it)
                        }
                    }
                }
            )
        } catch (e: IOException) {
            context.startModelDownloadActivity(
                listOf(primaryModel).let {
                    if (secondaryModel != null) it + secondaryModel
                    else it
                }
            )
            cancelRecognizer()
        }
    }

    private suspend fun loadModelInner() {
        try {
            val englishModelIdx = context.getSetting(ENGLISH_MODEL_INDEX)
            val multilingualModelIdx = context.getSetting(MULTILINGUAL_MODEL_INDEX)
            val languages = context.getSetting(LANGUAGE_TOGGLES)
            val isMultilingual = context.getSetting(ENABLE_MULTILINGUAL)

            if (forcedLanguage != null) {
                tryLoadModelOrCancel(
                    if (forcedLanguage == "en") {
                        ENGLISH_MODELS[englishModelIdx]
                    } else {
                        MULTILINGUAL_MODELS[multilingualModelIdx]
                    },

                    null
                )
            } else {
                if (isMultilingual) {
                    tryLoadModelOrCancel(
                        MULTILINGUAL_MODELS[multilingualModelIdx],
                        if (languages.contains("en")) {
                            ENGLISH_MODELS[englishModelIdx]
                        } else {
                            null
                        }
                    )
                } else {
                    tryLoadModelOrCancel(
                        ENGLISH_MODELS[englishModelIdx],
                        null
                    )
                }
            }
        } catch(e: OutOfMemoryError) {
            decodingStatus(RunState.OOMError)

            for(i in 0 until 2) {
                System.gc()
                System.runFinalization()
                delay(500L)
            }

            return loadModelInner()
        }
    }

    private fun loadModel() {
        if (model == null) {
            loadModelJob = lifecycleScope.launch {
                withContext(Dispatchers.Default) {
                    loadModelInner()
                }
            }
        }
    }

    private var forcedLanguage: String? = null
    fun forceLanguage(language: String?) {
        forcedLanguage = language
    }

    fun create() {
        loading()

        if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            needPermission()
        } else {
            startRecording()
        }
    }

    fun permissionResultGranted() {
        startRecording()
    }

    fun permissionResultRejected() {
        permissionRejected()
    }

    private fun startRecording() {
        if (isRecording) {
            throw IllegalStateException("Start recording when already recording")
        }

        try {
            recorder = AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                16000,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_FLOAT,
                16000 * 2 * 5
            )

            if(recorder!!.state == AudioRecord.STATE_UNINITIALIZED) {
                recorder!!.release()
                recorder = null

                println("Failed to initialize AudioRecord, retrying")
                return startRecording()
            }

            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    recorder!!.setPreferredMicrophoneDirection(MicrophoneDirection.MIC_DIRECTION_TOWARDS_USER)
                }
            } catch(e: Exception) {
                println("Failed to set preferred mic direction")
                e.printStackTrace()
            }

            recorder!!.startRecording()

            focusAudio()
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

                    val vad = Vad.builder()
                        .setModel(Model.WEB_RTC_GMM)
                        .setMode(Mode.VERY_AGGRESSIVE)
                        .setFrameSize(FrameSize.FRAME_SIZE_480)
                        .setSampleRate(SampleRate.SAMPLE_RATE_16K)
                        .setSpeechDurationMs(150)
                        .setSilenceDurationMs(300)
                        .build()

                    val shouldUseVad = context.getSetting(IS_VAD_ENABLED)
                    
                    val vadSampleBuffer = ShortBuffer.allocate(480)
                    var numConsecutiveNonSpeech = 0
                    var numConsecutiveSpeech = 0

                    val samples = FloatArray(1600)

                    while(isRecording && recorder!!.recordingState == AudioRecord.RECORDSTATE_RECORDING){
                        yield()
                        val nRead = recorder!!.read(samples, 0, 1600, AudioRecord.READ_BLOCKING)

                        if(nRead <= 0) break
                        yield()

                        if(!isRecording || recorder!!.recordingState != AudioRecord.RECORDSTATE_RECORDING) break

                        if(floatSamples.remaining() < 1600) {
                            withContext(Dispatchers.Main){ finishRecognizer() }
                            break
                        }

                        // Run VAD
                        if(shouldUseVad) {
                            var remainingSamples = nRead
                            var offset = 0
                            while(remainingSamples > 0) {
                                if(!vadSampleBuffer.hasRemaining()) {
                                    val isSpeech = vad.isSpeech(vadSampleBuffer.array())
                                    vadSampleBuffer.clear()
                                    vadSampleBuffer.rewind()

                                    if(!isSpeech) {
                                        numConsecutiveNonSpeech++
                                        numConsecutiveSpeech = 0
                                    } else {
                                        numConsecutiveNonSpeech = 0
                                        numConsecutiveSpeech++
                                    }
                                }

                                val samplesToRead = min(min(remainingSamples, 480), vadSampleBuffer.remaining())
                                for(i in 0 until samplesToRead) {
                                    vadSampleBuffer.put((samples[offset] * 32768.0).toInt().toShort())
                                    offset += 1
                                    remainingSamples -= 1
                                }
                            }
                        }

                        floatSamples.put(samples.sliceArray(0 until nRead))

                        // Don't set hasTalked if the start sound may still be playing, otherwise on some
                        // devices the rms just explodes and `hasTalked` is always true
                        val startSoundPassed = (floatSamples.position() > 16000*0.6)
                        if(!startSoundPassed){
                            numConsecutiveSpeech = 0
                            numConsecutiveNonSpeech = 0
                        }

                        val rms = sqrt(samples.sumOf { (it * it).toDouble() } / samples.size).toFloat()

                        if(startSoundPassed && ((rms > 0.01) || (numConsecutiveSpeech > 8))) hasTalked = true

                        if(rms > 0.0001){
                            anyNoiseAtAll = true
                            isMicBlocked = false
                        }

                        // Check if mic is blocked
                        if(!anyNoiseAtAll && canMicBeBlocked && (floatSamples.position() > 2*16000)){
                            isMicBlocked = true
                        }

                        // End if VAD hasn't detected speech in a while
                        if(shouldUseVad && hasTalked && (numConsecutiveNonSpeech > 66)) {
                            withContext(Dispatchers.Main){ finishRecognizer() }
                            break
                        }

                        val magnitude = (1.0f - 0.1f.pow(24.0f * rms))

                        val state = if (floatSamples.remaining() < (16000 * 5)) {
                            MagnitudeState.ENDING_SOON_30S
                        } else if(hasTalked && shouldUseVad && (numConsecutiveNonSpeech > 33)) {
                            MagnitudeState.ENDING_SOON_VAD
                        } else if(hasTalked) {
                            MagnitudeState.TALKING
                        } else if(isMicBlocked) {
                            MagnitudeState.MIC_MAY_BE_BLOCKED
                        } else {
                            MagnitudeState.NOT_TALKED_YET
                        }

                        yield()
                        withContext(Dispatchers.Main) {
                            yield()
                            if(isRecording) {
                                updateMagnitude(magnitude, state)
                            }
                        }

                        // Skip ahead as much as possible, in case we are behind (taking more than
                        // 100ms to process 100ms)
                        while(true){
                            yield()
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

            // We can only load model now, because the model loading may fail and need to cancel
            // everything we just did.
            // TODO: We could check if the model exists before doing all this work
            loadModel()

            recordingStarted()
        } catch(e: SecurityException){
            // It's possible we may have lost permission, so let's just ask for permission again
            needPermission()
        }
    }

    private suspend fun runModel(){
        if(loadModelJob != null && loadModelJob!!.isActive) {
            println("Model was not finished loading...")
            loadModelJob!!.join()
        }else if(model == null) {
            println("Model was null by the time runModel was called...")
            loadModel()
            loadModelJob!!.join()
        }

        val floatArray = floatSamples.array().sliceArray(0 until floatSamples.position())

        val words = context.getSetting(PERSONAL_DICTIONARY)
        val decodingMode = if(context.getSetting(BEAM_SEARCH)){ DecodingMode.BeamSearch5 } else { DecodingMode.Greedy }

        yield()
        val text = try {
            model!!.run(floatArray, words, forcedLanguage, decodingMode)
        } catch(e: OutOfMemoryError) {
            decodingStatus(RunState.OOMError)
            model!!.close()
            model = null
            loadModelJob = null

            for(i in 0 until 2) {
                System.gc()
                System.runFinalization()
                delay(500L)
            }

            loadModel()

            return runModel()
        }

        model!!.close()
        model = null

        lifecycleScope.launch {
            withContext(Dispatchers.Main) {
                finished(text)
            }
        }
    }

    private fun onFinishRecording() {
        if(!isRecording) {
            throw IllegalStateException("Should not call onFinishRecording when not recording")
        }

        isRecording = false

        recorderJob?.cancel()
        recorder?.stop()
        unfocusAudio()

        processing()

        modelJob = lifecycleScope.launch {
            withContext(Dispatchers.Default) {
                runModel()
            }
        }
    }
}