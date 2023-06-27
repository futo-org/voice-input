package org.futo.voiceinput

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.take
import org.futo.voiceinput.downloader.DownloadActivity
import java.io.File

class ValueFromSettings<T>(val key: Preferences.Key<T>, val default: T) {
    private var _value = default

    val value: T
        get() { return _value }

    suspend fun load(context: Context, onResult: ((T) -> Unit)? = null) {
        val valueFlow: Flow<T> = context.dataStore.data.map { preferences -> preferences[key] ?: default }.take(1)

        valueFlow.collect {
            _value = it

            if(onResult != null) {
                onResult(it)
            }
        }
    }
}

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

data class ModelData(
    val name: String,

    val is_builtin_asset: Boolean,
    val encoder_xatn_file: String,
    val decoder_file: String,

    val vocab_file: String,
    val vocab_raw_asset: Int? = null
)

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

fun Context.modelNeedsDownloading(model: ModelData): Boolean {
    if(model.is_builtin_asset) return false

    return this.fileNeedsDownloading(model.encoder_xatn_file)
            || this.fileNeedsDownloading(model.decoder_file)
            || this.fileNeedsDownloading(model.vocab_file)
}

fun Context.startModelDownloadActivity(models: List<ModelData>) {
    if(!models.any { this.modelNeedsDownloading(it) }) return

    val intent = Intent(this, DownloadActivity::class.java)
    intent.putStringArrayListExtra("models", ArrayList(models.map { model ->
        arrayListOf(
            model.encoder_xatn_file,
            model.decoder_file,
            model.vocab_file
        )
    }.flatten()))

    if(this !is Activity) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    startActivity(intent)
}

// Numbers from Appendix E. Figure 11. https://cdn.openai.com/papers/whisper.pdf
// Trained hours are for transcribing, translation hours are not included.
data class LanguageEntry(val id: String, val name: String, val trainedHourCount: Int)
val LANGUAGE_LIST = listOf(
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


val TINY_ENGLISH_MODEL_DATA = ModelData(
    name = "tiny-en",

    is_builtin_asset = true,
    encoder_xatn_file = "tiny-en-encoder-xatn.tflite",
    decoder_file = "tiny-en-decoder.tflite",

    vocab_file = "tinyenvocab.json",
    vocab_raw_asset = R.raw.tinyenvocab
)

val MULTILINGUAL_MODEL_DATA = ModelData(
    name = "base",

    is_builtin_asset = false,
    encoder_xatn_file = "base-encoder-xatn.tflite",
    decoder_file = "base-decoder.tflite",

    vocab_file = "base-vocab.json",
)

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")
val ENABLE_SOUND = booleanPreferencesKey("enable_sounds")
val VERBOSE_PROGRESS = booleanPreferencesKey("verbose_progress")
val ENABLE_ENGLISH = booleanPreferencesKey("enable_english")
val ENABLE_MULTILINGUAL = booleanPreferencesKey("enable_multilingual")
val DISALLOW_SYMBOLS = booleanPreferencesKey("disallow_symbols")

val LANGUAGE_TOGGLES = stringSetPreferencesKey("enabled_languages")