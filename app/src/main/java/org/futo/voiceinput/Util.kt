package org.futo.voiceinput

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.semantics
import org.futo.voiceinput.downloader.DownloadActivity
import org.futo.voiceinput.settings.DISALLOW_SYMBOLS
import org.futo.voiceinput.settings.ENABLE_30S_LIMIT
import org.futo.voiceinput.settings.ENABLE_ANIMATIONS
import org.futo.voiceinput.settings.ENGLISH_MODEL_INDEX
import org.futo.voiceinput.settings.IS_VAD_ENABLED
import org.futo.voiceinput.settings.LANGUAGE_TOGGLES
import org.futo.voiceinput.settings.MANUALLY_SELECT_LANGUAGE
import org.futo.voiceinput.settings.MULTILINGUAL_MODEL_INDEX
import org.futo.voiceinput.settings.PERSONAL_DICTIONARY
import org.futo.voiceinput.settings.USE_LANGUAGE_SPECIFIC_MODELS
import org.futo.voiceinput.settings.VERBOSE_PROGRESS
import org.futo.voiceinput.settings.getSetting
import org.futo.voiceinput.shared.BUILTIN_ENGLISH_MODEL
import org.futo.voiceinput.shared.ENGLISH_MODELS
import org.futo.voiceinput.shared.MULTILINGUAL_MODELS
import org.futo.voiceinput.shared.RecognizerViewSettings
import org.futo.voiceinput.shared.RecordingSettings
import org.futo.voiceinput.shared.types.Language
import org.futo.voiceinput.shared.types.ModelLoader
import org.futo.voiceinput.shared.types.getLanguageFromWhisperString
import org.futo.voiceinput.shared.whisper.DecodingConfiguration
import org.futo.voiceinput.shared.whisper.MultiModelRunConfiguration
import java.io.File

enum class Status {
    Unknown,
    False,
    True;

    companion object {
        fun from(found: Boolean): Status {
            return if (found) { True } else { False }
        }
    }
}

fun Array<DoubleArray>.transpose(): Array<DoubleArray> {
    return Array(this[0].size) { i ->
        DoubleArray(this.size) { j ->
            this[j][i]
        }
    }
}

fun Array<DoubleArray>.shape(): IntArray {
    return arrayOf(size, this[0].size).toIntArray()
}

fun DoubleArray.toFloatArray(): FloatArray {
    return this.map { it.toFloat() }.toFloatArray()
}

fun FloatArray.toDoubleArray(): DoubleArray {
    return this.map { it.toDouble() }.toDoubleArray()
}

fun Context.fileNeedsDownloading(file: String): Boolean {
    return !File(this.filesDir, file).exists()
}

fun Context.modelNeedsDownloading(model: ModelLoader): Boolean =
    model.getRequiredDownloadList(this).isNotEmpty()

fun Context.startModelDownloadActivity(models: List<ModelLoader>) {
    @Suppress("NAME_SHADOWING") val models = models.filter { this.modelNeedsDownloading(it) }
    if(models.isEmpty()) return

    val intent = Intent(this, DownloadActivity::class.java)
    intent.putStringArrayListExtra("models", ArrayList(models.flatMap { it.getRequiredDownloadList(this) }))

    if(this !is Activity) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    startActivity(intent)
}

fun <T> Context.startAppActivity(activity: Class<T>, clearTop: Boolean = false) {
    val intent = Intent(this, activity)

    if(this !is Activity) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    if(clearTop) {
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
    }

    startActivity(intent)
}

fun Context.openURI(uri: String, newTask: Boolean = false) {
    try {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uri))
        if (newTask) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        startActivity(intent)
    } catch(e: ActivityNotFoundException) {
        Toast.makeText(this, e.localizedMessage, Toast.LENGTH_SHORT).show()
    }
}

// Numbers from Appendix E. Figure 11. https://cdn.openai.com/papers/whisper.pdf
// Trained hours are for transcribing, translation hours are not included.
data class LanguageEntry(val id: String, val name: String, val trainedHourCount: Int)
val LANGUAGE_LIST = listOf(
    // TODO: These are not localized
    LanguageEntry("en", "English", 438218),
    LanguageEntry("zh", "Chinese", 23446),
    LanguageEntry("de", "German", 13344),
    LanguageEntry("es", "Spanish", 11100),
    LanguageEntry("ru", "Russian", 9761),
    LanguageEntry("fr", "French", 9752),
    LanguageEntry("pt", "Portuguese", 8573),
    LanguageEntry("ko", "Korean", 7993),
    LanguageEntry("ja", "Japanese", 7054),
    LanguageEntry("tr", "Turkish", 4333),
    LanguageEntry("pl", "Polish", 4278),
    LanguageEntry("it", "Italian", 2585),
    LanguageEntry("sv", "Swedish", 2119),
    LanguageEntry("nl", "Dutch", 2077),
    LanguageEntry("ca", "Catalan", 1883),
    LanguageEntry("fi", "Finnish", 1066),
    LanguageEntry("id", "Indonesian", 1014),
    LanguageEntry("ar", "Arabic", 739),
    LanguageEntry("uk", "Ukrainian", 697),
    LanguageEntry("vi", "Vietnamese", 691),
    LanguageEntry("he", "Hebrew", 688),
    LanguageEntry("el", "Greek", 529),
    LanguageEntry("da", "Danish", 473),
    LanguageEntry("ms", "Malay", 382),
    LanguageEntry("hu", "Hungarian", 379),
    LanguageEntry("ro", "Romanian", 356),
    LanguageEntry("no", "Norwegian", 266),
    LanguageEntry("th", "Thai", 226),
    LanguageEntry("cs", "Czech", 192),
    LanguageEntry("ta", "Tamil", 134),
    LanguageEntry("ur", "Urdu", 104),
    LanguageEntry("hr", "Croatian", 91),
    LanguageEntry("sk", "Slovak", 90),
    LanguageEntry("bg", "Bulgarian", 86),
    LanguageEntry("tl", "Tagalog", 75),
    LanguageEntry("cy", "Welsh", 73),
    LanguageEntry("lt", "Lithuanian", 67),
    LanguageEntry("lv", "Latvian", 65),
    LanguageEntry("az", "Azerbaijani", 47),
    LanguageEntry("et", "Estonian", 41),
    LanguageEntry("sl", "Slovenian", 41),
    LanguageEntry("sr", "Serbian", 28),
    LanguageEntry("fa", "Persian", 24),
    LanguageEntry("eu", "Basque", 21),
    LanguageEntry("is", "Icelandic", 16),
    LanguageEntry("mk", "Macedonian", 16),
    LanguageEntry("hy", "Armenian", 13),
    LanguageEntry("kk", "Kazakh", 12),
    LanguageEntry("hi", "Hindi", 12),
    LanguageEntry("bs", "Bosnian", 11),
    LanguageEntry("gl", "Galician", 9),
    LanguageEntry("sq", "Albanian", 6),
    LanguageEntry("si", "Sinhala", 5),
    LanguageEntry("sw", "Swahili", 5),
    LanguageEntry("te", "Telugu", 4),
    LanguageEntry("af", "Afrikaans", 4),
    LanguageEntry("kn", "Kannada", 4),
    LanguageEntry("be", "Belarusian", 2),
    LanguageEntry("km", "Khmer", 1),
    LanguageEntry("bn", "Bengali", 1),
    LanguageEntry("mt", "Maltese", 1),
    LanguageEntry("ht", "Haitian Creole", 1),
    LanguageEntry("pa", "Punjabi", 1),
    LanguageEntry("mr", "Marathi", 1),
    LanguageEntry("ne", "Nepali", 1),
    LanguageEntry("ka", "Georgian", 1),
    LanguageEntry("ml", "Malayalam", 1),

    // Languages below trained on fewer than 0.5 hours of data
    LanguageEntry("yi", "Yiddish", 0),
    LanguageEntry("uz", "Uzbek", 0),
    LanguageEntry("gu", "Gujarati", 0),
    LanguageEntry("tg", "Tajik", 0),
    LanguageEntry("mg", "Malagasy", 0),
    LanguageEntry("my", "Burmese", 0),
    LanguageEntry("su", "Sundanese", 0),
    LanguageEntry("lo", "Lao", 0)
)

private fun Context.getLanguageModelMap(): Map<Language, ModelLoader> {
    val modelIdx = getSetting(MULTILINGUAL_MODEL_INDEX)
    val englishModelIdx = getSetting(ENGLISH_MODEL_INDEX)
    val useLanguageSpecificModels = getSetting(USE_LANGUAGE_SPECIFIC_MODELS)
    val manuallySelectLanguage = getSetting(MANUALLY_SELECT_LANGUAGE)
    val languages = getSetting(LANGUAGE_TOGGLES)

    val map = hashMapOf<Language, ModelLoader>()
    languages.forEach {
        getLanguageFromWhisperString(it)?.let {
            map[it] = if (it == Language.English) {
                ENGLISH_MODELS[englishModelIdx]
            } else {
                MULTILINGUAL_MODELS[modelIdx]
            }
        }
    }

    //if(languages.size > 1 && !manuallySelectLanguage) {
    //    map["unk"] = MULTILINGUAL_MODELS[modelIdx]
    //}

    //if(!manuallySelectLanguage && !useLanguageSpecificModels)

    return map
}

fun Context.loadRecognizerSettings(inlinePartialResult: Boolean = false): RecognizerViewSettings {
    val multiModelIdx = getSetting(MULTILINGUAL_MODEL_INDEX)
    val englishModelIdx = getSetting(ENGLISH_MODEL_INDEX)
    val useLanguageSpecificModels = getSetting(USE_LANGUAGE_SPECIFIC_MODELS)
    val languages = getSetting(LANGUAGE_TOGGLES).mapNotNull { getLanguageFromWhisperString(it) }.toSet()


    val englishModel = ENGLISH_MODELS[englishModelIdx]
    val multiModel = MULTILINGUAL_MODELS[multiModelIdx]
    val primaryModel = if(languages == setOf(Language.English)) {
        englishModel
    } else {
        multiModel
    }

    val languageSpecificModels = mutableMapOf<Language, ModelLoader>()
    if(useLanguageSpecificModels && primaryModel == multiModel) {
        languageSpecificModels[Language.English] = englishModel
    }

        return RecognizerViewSettings(
        shouldShowVerboseFeedback = getSetting(VERBOSE_PROGRESS),
        shouldShowInlinePartialResult = inlinePartialResult,
        shouldAnimateBubble = getSetting(ENABLE_ANIMATIONS),
        modelRunConfiguration = MultiModelRunConfiguration(
            primaryModel = primaryModel,
            languageSpecificModels = languageSpecificModels,
        ),
        decodingConfiguration = DecodingConfiguration(
            glossary = getSetting(PERSONAL_DICTIONARY).split("\n").map { it.trim() },
            languages = languages,
            suppressSymbols = getSetting(DISALLOW_SYMBOLS),
        ),
        recordingConfiguration = RecordingSettings(
            preferBluetoothMic = false,
            requestAudioFocus = true,
            canExpandSpace = !getSetting(ENABLE_30S_LIMIT),
            useVADAutoStop = getSetting(IS_VAD_ENABLED),
        )
    )
}

fun Modifier.recognizerSurfaceClickable(disabled: Boolean, onPauseVAD: (Boolean) -> Unit, onFinish: () -> Unit): Modifier = composed {
    val interactionSource = remember { MutableInteractionSource() }
    val ripple = rememberRipple(bounded = false)

    this.pointerInput(disabled) {
        detectTapGestures(onPress = { offset ->
            if(!disabled) {
                val press = PressInteraction.Press(offset)

                interactionSource.emit(press)
                onPauseVAD(true)
                val didRelease = this.tryAwaitRelease()
                onPauseVAD(false)

                if (didRelease) {
                    interactionSource.emit(PressInteraction.Release(press))
                    onFinish()
                } else {
                    interactionSource.emit(PressInteraction.Cancel(press))
                }
            }
        })
    }.indication(interactionSource, ripple).semantics(mergeDescendants = true) { }
}
