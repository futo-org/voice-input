package org.futo.voiceinput

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION
import android.media.AudioAttributes.USAGE_ASSISTANCE_SONIFICATION
import android.media.SoundPool
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.math.MathUtils.clamp
import androidx.lifecycle.LifecycleCoroutineScope
import com.google.android.material.math.MathUtils
import kotlinx.coroutines.launch
import org.futo.voiceinput.ml.RunState
import org.futo.voiceinput.settings.ENABLE_ANIMATIONS
import org.futo.voiceinput.settings.ENABLE_SOUND
import org.futo.voiceinput.settings.LANGUAGE_TOGGLES
import org.futo.voiceinput.settings.MANUALLY_SELECT_LANGUAGE
import org.futo.voiceinput.settings.VERBOSE_PROGRESS
import org.futo.voiceinput.settings.getSetting
import org.futo.voiceinput.settings.useDataStoreValueNullable
import org.futo.voiceinput.theme.Typography

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
                if (time > (startTime + 100)) break
            }
        }
    }

    val color = MaterialTheme.colorScheme.primaryContainer

    Canvas(modifier = Modifier.fillMaxSize()) {
        val drawRadius = size.height * (0.8f + radius * 2.0f)
        drawCircle(color = color, radius = drawRadius)
    }
}

@Composable
fun InnerRecognize(
    onFinish: () -> Unit,
    magnitude: Float = 0.5f,
    state: MagnitudeState = MagnitudeState.MIC_MAY_BE_BLOCKED
) {
    val shouldUseCircle = useDataStoreValueNullable(ENABLE_ANIMATIONS.key, default = ENABLE_ANIMATIONS.default)
    IconButton(
        onClick = onFinish,
        modifier = Modifier
            .fillMaxWidth()
            .height(80.dp)
            .padding(16.dp)
    ) {
        AnimatedRecognizeCircle(magnitude = if(shouldUseCircle == true) { magnitude } else { 0.0f })

        Icon(
            painter = painterResource(R.drawable.mic_2_),
            contentDescription = stringResource(R.string.stop_recording),
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.onPrimaryContainer
        )

    }

    val text = when (state) {
        MagnitudeState.NOT_TALKED_YET -> stringResource(R.string.try_saying_something)
        MagnitudeState.MIC_MAY_BE_BLOCKED -> stringResource(R.string.no_audio_detected_is_your_microphone_blocked)
        MagnitudeState.TALKING -> stringResource(R.string.listening)
        MagnitudeState.ENDING_SOON_30S -> stringResource(R.string.ending_soon_30s)
        MagnitudeState.ENDING_SOON_VAD -> stringResource(R.string.ending_soon_vad)
    }

    Text(
        text,
        modifier = Modifier.fillMaxWidth(),
        textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.onSurface
    )
}

@Composable
fun SelectLanguage(languages: Set<String>, onSelected: (String) -> Unit) {
    val languageItems = LANGUAGE_LIST.filter { languages.contains(it.id) }

    LazyVerticalGrid(columns = GridCells.Adaptive(minSize = 140.dp), modifier = Modifier.fillMaxWidth()) {
        items(languageItems.size) {
            Button(onClick = {
                onSelected(languageItems[it].id)
            }, modifier = Modifier
                .fillMaxSize()
                .padding(4.dp), colors = ButtonDefaults.buttonColors(
                    containerColor = Color.Transparent,
                    contentColor = MaterialTheme.colorScheme.onSurface
                )) {
                Text(languageItems[it].name,  modifier = Modifier.padding(0.dp, 16.dp), color = MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}

@Composable
fun ColumnScope.RecognizeLoadingCircle(text: String = "Initializing...") {
    CircularProgressIndicator(
        modifier = Modifier.align(Alignment.CenterHorizontally),
        color = MaterialTheme.colorScheme.primary
    )
    Spacer(modifier = Modifier.height(8.dp))
    Text(text, modifier = Modifier.align(Alignment.CenterHorizontally))
}

@Composable
fun ColumnScope.PartialDecodingResult(text: String = "I am speaking [...]") {
    CircularProgressIndicator(
        modifier = Modifier.align(Alignment.CenterHorizontally),
        color = MaterialTheme.colorScheme.primary
    )
    Spacer(modifier = Modifier.height(6.dp))
    Surface(
        modifier = Modifier
            .padding(4.dp)
            .fillMaxWidth(),
        color = MaterialTheme.colorScheme.primaryContainer,
        shape = RoundedCornerShape(4.dp)
    ) {
        Text(
            text,
            modifier = Modifier
                .align(Alignment.Start)
                .padding(8.dp)
                .defaultMinSize(0.dp, 64.dp),
            textAlign = TextAlign.Start,
            style = Typography.bodyMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
    }
}

@Composable
fun ColumnScope.RecognizeMicError(openSettings: () -> Unit) {
    Text(
        stringResource(R.string.grant_microphone_permission_to_use_voice_input),
        modifier = Modifier
            .padding(8.dp, 2.dp)
            .align(Alignment.CenterHorizontally),
        textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.onSurface
    )
    IconButton(
        onClick = { openSettings() },
        modifier = Modifier
            .padding(4.dp)
            .align(Alignment.CenterHorizontally)
            .size(64.dp)
    ) {
        Icon(
            Icons.Default.Settings,
            contentDescription = stringResource(R.string.open_voice_input_settings),
            modifier = Modifier.size(32.dp),
            tint = MaterialTheme.colorScheme.onSurface
        )
    }
}

abstract class RecognizerView {
    private var shouldPlaySounds = ENABLE_SOUND.default
    private var shouldBeVerbose = VERBOSE_PROGRESS.default
    private var shouldRequestLanguage = MANUALLY_SELECT_LANGUAGE.default
    private var languages = LANGUAGE_TOGGLES.default

    suspend fun loadSettings() {
        shouldPlaySounds = context.getSetting(ENABLE_SOUND)
        shouldBeVerbose = context.getSetting(VERBOSE_PROGRESS)
        shouldRequestLanguage = context.getSetting(MANUALLY_SELECT_LANGUAGE)
        languages = context.getSetting(LANGUAGE_TOGGLES)
    }

    private val soundPool = SoundPool.Builder().setMaxStreams(2).setAudioAttributes(
        AudioAttributes.Builder()
            .setUsage(USAGE_ASSISTANCE_SONIFICATION)
            .setContentType(CONTENT_TYPE_SONIFICATION)
            .build()
    ).build()

    private var startSoundId: Int = -1
    private var cancelSoundId: Int = -1

    protected abstract val context: Context
    protected abstract val lifecycleScope: LifecycleCoroutineScope

    abstract fun setContent(content: @Composable () -> Unit)

    abstract fun onCancel()
    abstract fun sendResult(result: String)
    abstract fun sendPartialResult(result: String): Boolean
    abstract fun requestPermission()

    abstract fun decodingStarted()

    @Composable
    abstract fun Window(onClose: () -> Unit, content: @Composable ColumnScope.() -> Unit)

    private val recognizer = object : AudioRecognizer() {
        override val context: Context
            get() = this@RecognizerView.context
        override val lifecycleScope: LifecycleCoroutineScope
            get() = this@RecognizerView.lifecycleScope

        // Tries to play a sound. If it's not yet ready, plays it when it's ready
        private fun playSound(id: Int) {
            lifecycleScope.launch {
                if(context.getSetting(ENABLE_SOUND)) {
                    if (soundPool.play(id, 1.0f, 1.0f, 0, 0, 1.0f) == 0) {
                        soundPool.setOnLoadCompleteListener { soundPool, sampleId, status ->
                            if ((sampleId == id) && (status == 0)) {
                                soundPool.play(id, 1.0f, 1.0f, 0, 0, 1.0f)
                            }
                        }
                    }
                }
            }
        }

        override fun cancelled() {
            playSound(cancelSoundId)
            onCancel()
        }

        override fun finished(result: String) {
            sendResult(result)
        }

        override fun languageDetected(result: String) {

        }

        override fun partialResult(result: String) {
            if (!sendPartialResult(result)) {
                if (result.isNotBlank()) {
                    setContent {
                        this@RecognizerView.Window(onClose = { cancelRecognizer() }) {
                            PartialDecodingResult(text = result)
                        }
                    }
                }
            }
        }

        override fun decodingStatus(status: RunState) {
            val text = if (shouldBeVerbose) {
                when (status) {
                    RunState.ExtractingFeatures -> context.getString(R.string.extracting_features)
                    RunState.ProcessingEncoder -> context.getString(R.string.running_encoder)
                    RunState.StartedDecoding -> context.getString(R.string.decoding_started)
                    RunState.SwitchingModel -> context.getString(R.string.switching_to_english_model)
                    RunState.OOMError -> context.getString(R.string.out_of_memory_error)
                }
            } else {
                when (status) {
                    RunState.ExtractingFeatures -> context.getString(R.string.processing)
                    RunState.ProcessingEncoder -> context.getString(R.string.processing)
                    RunState.StartedDecoding -> context.getString(R.string.processing)
                    RunState.SwitchingModel -> context.getString(R.string.switching_to_english_model)
                    RunState.OOMError -> context.getString(R.string.out_of_memory_error)
                }
            }

            if(status == RunState.StartedDecoding) {
                this@RecognizerView.decodingStarted()
            }

            setContent {
                this@RecognizerView.Window(onClose = { cancelRecognizer() }) {
                    RecognizeLoadingCircle(text = text)
                }
            }
        }

        override fun loading() {
            setContent {
                this@RecognizerView.Window(onClose = { cancelRecognizer() }) {
                    RecognizeLoadingCircle(text = context.getString(R.string.initializing))
                }
            }
        }

        override fun needPermission() {
            requestPermission()
        }

        override fun permissionRejected() {
            setContent {
                this@RecognizerView.Window(onClose = { cancelRecognizer() }) {
                    RecognizeMicError(openSettings = { openPermissionSettings() })
                }
            }
        }

        override fun recordingStarted() {
            updateMagnitude(0.0f, MagnitudeState.NOT_TALKED_YET)

            playSound(startSoundId)
        }

        override fun updateMagnitude(magnitude: Float, state: MagnitudeState) {
            setContent {
                this@RecognizerView.Window(onClose = { cancelRecognizer() }) {
                    InnerRecognize(
                        onFinish = { finishRecognizerIfRecording() },
                        magnitude = magnitude,
                        state = state
                    )
                }
            }
        }

        override fun processing() {
            setContent {
                this@RecognizerView.Window(onClose = { cancelRecognizer() }) {
                    RecognizeLoadingCircle(text = stringResource(R.string.processing))
                }
            }
        }
    }

    fun isRecording(): Boolean {
        return recognizer.isCurrentlyRecording()
    }

    fun finishRecognizerIfRecording() {
        recognizer.finishRecognizerIfRecording()
    }

    fun reset() {
        recognizer.reset()
    }

    fun init() {
        startSoundId = soundPool.load(this.context, R.raw.start, 0)
        cancelSoundId = soundPool.load(this.context, R.raw.cancel, 0)

        lifecycleScope.launch {
            loadSettings()

            if(shouldRequestLanguage && (languages.size > 1)) {
                setContent {
                    this@RecognizerView.Window(onClose = { recognizer.cancelRecognizer() }) {
                        SelectLanguage(languages = languages, onSelected = {
                            recognizer.forceLanguage(it)

                            if(!recognizer.isCurrentlyRecording()) {
                                recognizer.create()
                            } else {
                                // NOTE: If forceLanguage was set to "en" and English-only model was loaded
                                // then we'll be stuck with English
                                recognizer.forceLanguage(null)
                            }
                        })
                    }
                }
            } else {
                recognizer.forceLanguage(null)
                recognizer.create()
            }
        }
    }

    fun permissionResultGranted() {
        recognizer.permissionResultGranted()
    }

    fun permissionResultRejected() {
        recognizer.permissionResultRejected()
    }
}
