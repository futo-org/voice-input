package org.futo.voiceinput

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
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
import kotlinx.coroutines.runBlocking
import org.futo.voiceinput.downloader.DownloadActivity
import org.futo.voiceinput.settings.CURRENT_MIGRATION_LEVEL
import org.futo.voiceinput.settings.DISALLOW_SYMBOLS
import org.futo.voiceinput.settings.ENABLE_30S_LIMIT
import org.futo.voiceinput.settings.ENABLE_ANIMATIONS
import org.futo.voiceinput.settings.ENABLE_SOUND
import org.futo.voiceinput.settings.IS_VAD_ENABLED
import org.futo.voiceinput.settings.LANGUAGE_TOGGLES
import org.futo.voiceinput.settings.LEGACY_ENGLISH_MODEL_INDEX
import org.futo.voiceinput.settings.LEGACY_MULTILINGUAL_MODEL_INDEX
import org.futo.voiceinput.settings.LEGACY_USE_LANGUAGE_SPECIFIC_MODELS
import org.futo.voiceinput.settings.MANUALLY_SELECT_LANGUAGE
import org.futo.voiceinput.settings.MIGRATED_ENGLISH_MODEL
import org.futo.voiceinput.settings.MODEL_OPTIONS
import org.futo.voiceinput.settings.PERSONAL_DICTIONARY
import org.futo.voiceinput.settings.STREAMING_TIER
import org.futo.voiceinput.settings.VERBOSE_PROGRESS
import org.futo.voiceinput.settings.getSetting
import org.futo.voiceinput.settings.setSetting
import org.futo.voiceinput.shared.DefaultModelsByLanguage
import org.futo.voiceinput.shared.RecognizerViewSettings
import org.futo.voiceinput.shared.RecordingSettings
import org.futo.voiceinput.shared.lookupModel
import org.futo.voiceinput.shared.lookupModelLegacy
import org.futo.voiceinput.shared.types.Language
import org.futo.voiceinput.shared.types.LegacyId
import org.futo.voiceinput.shared.types.ModelLoader
import org.futo.voiceinput.shared.types.getLanguageFromWhisperString
import org.futo.voiceinput.shared.types.toWhisperString
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

fun Context.startModelDownloadActivity(models: List<ModelLoader>, forUpgrade: Boolean = true) {
    val downloadList = models.map { it.getRequiredDownloadList(this, forUpgrade) }.filter { it.isNotEmpty() }
    if(downloadList.isEmpty()) return

    val intent = Intent(this, DownloadActivity::class.java)
    intent.putStringArrayListExtra("models", ArrayList(downloadList.flatten()))

    val cleanupList = models.flatMap { it.getCleanupListAfterUpgrade(this) }
    intent.putStringArrayListExtra("cleanup", ArrayList(cleanupList))

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

fun Context.loadCurrentModelList() = loadRecognizerSettings().modelRunConfiguration.let {
    listOf(it.primaryModel) + it.languageSpecificModels.values
}

// upgrade older settings to newer (note: it also runs on fresh install since default migration lvl is 0)
fun Context.performMigrationsIfNecessary() {
    val options = getSetting(MODEL_OPTIONS).toMutableSet()
    var multiModelIdx = getSetting(LEGACY_MULTILINGUAL_MODEL_INDEX)
    var englishModelIdx = getSetting(LEGACY_ENGLISH_MODEL_INDEX)

    val migrationLvl = getSetting(MIGRATED_ENGLISH_MODEL)

    if(migrationLvl >= CURRENT_MIGRATION_LEVEL) {
        // perform sanity check only
        var passesSanityCheck = true

        if(options.count { it.startsWith("multi:") } != 1) {
            Log.e("MigrationUtils", "Model Options do not pass sanity check due to multiple multi:'s! $options")
            passesSanityCheck = false
            options.clear()
        }

        Language.entries.forEach {
            val lang = it.toWhisperString()
            if(options.count { it.startsWith("${lang}:") } > 1) {
                Log.e("MigrationUtils", "Model Options do not pass sanity check due to multiple $lang:'s! $options")
                passesSanityCheck = false
                options.clear()
            }
        }

        if(passesSanityCheck) return
    }

    runBlocking { setSetting(MIGRATED_ENGLISH_MODEL, CURRENT_MIGRATION_LEVEL) }

    val oldDefaultEnglishModelIdx = 0
    if(englishModelIdx == oldDefaultEnglishModelIdx && lookupModelLegacy(false, oldDefaultEnglishModelIdx)?.exists(this) != true) {
        englishModelIdx = LegacyId.EnglishAsr4allM.idx!!
    }

    if(englishModelIdx == LEGACY_ENGLISH_MODEL_INDEX.default) englishModelIdx = lookupModel(DefaultModelsByLanguage[Language.English]!!)!!.legacyId.idx!!
    if(multiModelIdx == LEGACY_MULTILINGUAL_MODEL_INDEX.default) multiModelIdx = LegacyId.MultiWhisperBase.idx!!

    // Perform migration to new string set system
    if(options.isNotEmpty()) {
        Log.e("MigrationUtils", "expected an empty MODEL_OPTIONS during first migration! got $options")
        return
    }

    options.add("multi:" + lookupModelLegacy(true, multiModelIdx)!!.id)

    if(getSetting(LEGACY_USE_LANGUAGE_SPECIFIC_MODELS)) {
        options.add(Language.English.toWhisperString() + ":" + lookupModelLegacy(false, englishModelIdx)!!.id)
    } else {
        options.add(Language.English.toWhisperString() + ":")
    }

    DefaultModelsByLanguage.forEach {
        val lang = it.key.toWhisperString()
        if(!options.any { it.startsWith("$lang:") }) {
            options.add("$lang:${it.value}")
        }
    }

    runBlocking { setSetting(MODEL_OPTIONS, options) }
}

/// language == null is guaranteed to be the multilingual model
fun Iterable<String>.parseModelOptions() = mapNotNull { elem ->
    elem.split(':', limit = 2).let {
        if(it.size == 2) {
            val lang = getLanguageFromWhisperString(it[0])
            val model = lookupModel(it[1])
            if((lang != null || it[0] == "multi") && model != null) lang to model else null
        } else null
    }
}

fun Context.loadRecognizerSettings(inlinePartialResult: Boolean = false): RecognizerViewSettings {
    performMigrationsIfNecessary()

    val modelOptions = getSetting(MODEL_OPTIONS).parseModelOptions()
    val languages = getSetting(LANGUAGE_TOGGLES).mapNotNull { getLanguageFromWhisperString(it) }.toMutableSet()

    val relevantModels = modelOptions.filter { it.first in languages || it.first == null }.toMutableList()

    // means each language has an override and we need to remove the multi option
    if(relevantModels.size > languages.size) {
        relevantModels.removeIf { it.first == null }
    }

    val languageSpecificModels = mutableMapOf<Language, ModelLoader>()

    if(relevantModels.size == 0) {
        Log.e("SettingsUtils", "Relevant models are empty!! options=${modelOptions}, languages=${languages} optionsRaw=${getSetting(MODEL_OPTIONS)}")
    }
    val primaryModel = relevantModels.find { it.first == null }?.second ?: relevantModels[0].second
    if(relevantModels.size > 1) {
        relevantModels.forEach {
            if(it.first != null && it.second != primaryModel) {
                languageSpecificModels[it.first!!] = it.second
            }
        }
    }


    val needLanguageSelector = languages.size > 1 && (
        getSetting(MANUALLY_SELECT_LANGUAGE)
                || !primaryModel.caps.supportsLanguageDetection
    )

    // remove unsupported languages
    languages.filter { it !in primaryModel.caps.supportedLanguages && it !in languageSpecificModels }.forEach {
        languages.remove(it)
    }


    return RecognizerViewSettings(
        shouldShowVerboseFeedback = getSetting(VERBOSE_PROGRESS),
        shouldShowInlinePartialResult = inlinePartialResult,
        shouldAnimateBubble = getSetting(ENABLE_ANIMATIONS),
        shouldPlaySounds = getSetting(ENABLE_SOUND),
        shouldShowLanguageSelector = needLanguageSelector,
        modelRunConfiguration = MultiModelRunConfiguration(
            primaryModel = primaryModel,
            languageSpecificModels = languageSpecificModels,
        ),
        decodingConfiguration = DecodingConfiguration(
            glossary = getSetting(PERSONAL_DICTIONARY).split("\n", ", ", ",").map { it.trim() }.filter { it.isNotBlank() },
            languages = languages,
            suppressSymbols = getSetting(DISALLOW_SYMBOLS),
            tier = getSetting(STREAMING_TIER),
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
