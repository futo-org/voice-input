package org.futo.voiceinput

import android.content.Context
import android.content.ContextParams
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionService
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleCoroutineScope
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.coroutineScope
import org.futo.voiceinput.ml.RunState

class WhisperRecognizerService : RecognitionService(), LifecycleOwner {
    private val mLifecycleRegistry = LifecycleRegistry(this)

    override val lifecycle: Lifecycle
        get() = mLifecycleRegistry

    override fun onCreate() {
        super.onCreate()
        mLifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }

    override fun onDestroy() {
        super.onDestroy()
        currentRecognizer?.reset()
        mLifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
    }

    private var currentRecognizer: AudioRecognizer? = null

    private fun cancelCurrent() {
        currentRecognizer?.cancelRecognizer()
        currentRecognizer = null
    }

    override fun onStartListening(intent: Intent?, callback: Callback?) {
        if (callback == null) return
        cancelCurrent()

        val attributionContext = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            createContext(
                ContextParams.Builder()
                    .setNextAttributionSource(callback.callingAttributionSource)
                    .build()
            )
        } else {
            this
        }

        val biasingStrings: List<String> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent?.getStringArrayListExtra(RecognizerIntent.EXTRA_BIASING_STRINGS) ?: emptyList()
        } else {
            intent?.getStringArrayListExtra("android.speech.extra.BIASING_STRINGS") ?: emptyList()
        }

        currentRecognizer = object : AudioRecognizer() {
            override val context: Context
                get() = this@WhisperRecognizerService
            override val recordingContext: Context
                get() = attributionContext
            override val lifecycleScope: LifecycleCoroutineScope
                get() = this@WhisperRecognizerService.lifecycle.coroutineScope

            private var hasStartedSpeech = false

            override fun cancelled() {
                callback.error(SpeechRecognizer.ERROR_CLIENT)
                currentRecognizer = null
            }

            override fun finished(result: String) {
                val bundle = Bundle().apply {
                    putStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION, arrayListOf(result))
                    putFloatArray(SpeechRecognizer.CONFIDENCE_SCORES, floatArrayOf(1.0f))
                }
                callback.results(bundle)
                currentRecognizer = null
            }

            override fun languageDetected(result: String) {}

            override fun partialResult(result: String) {
                val bundle = Bundle().apply {
                    putStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION, arrayListOf(result))
                }
                callback.partialResults(bundle)
            }

            override fun decodingStatus(status: RunState) {}

            override fun loading() {}

            override fun needPermission() {
                callback.error(SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS)
                currentRecognizer = null
            }

            override fun permissionRejected() {
                callback.error(SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS)
                currentRecognizer = null
            }

            override fun recordingStarted() {
                callback.readyForSpeech(Bundle())
            }

            override fun updateMagnitude(magnitude: Float, state: MagnitudeState) {
                callback.rmsChanged(magnitude * 10.0f)
                if (!hasStartedSpeech && state == MagnitudeState.TALKING) {
                    hasStartedSpeech = true
                    callback.beginningOfSpeech()
                }
            }

            override fun processing() {
                callback.endOfSpeech()
            }
        }.also {
            it.extraBiasingWords = biasingStrings
            it.create()
        }
    }

    override fun onStopListening(callback: Callback?) {
        currentRecognizer?.finishRecognizerIfRecording()
    }

    override fun onCancel(callback: Callback?) {
        cancelCurrent()
    }
}
