package org.futo.voiceinput

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.take
import org.futo.voiceinput.downloader.DownloadActivity
import org.futo.voiceinput.ui.theme.Typography
import java.io.File

@Composable
fun Screen(title: String, content: @Composable () -> Unit) {
    Column(modifier = Modifier
        .padding(16.dp)
        .fillMaxSize()) {
        Text(title, style = Typography.titleLarge)


        Column(modifier = Modifier
            .padding(8.dp)
            .fillMaxSize()) {
            content()
        }
    }
}

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

    suspend fun set(context: Context, newValue: T) {
        context.dataStore.edit {
            it[key] = newValue
        }
    }

    suspend fun get(context: Context): T {
        val valueFlow: Flow<T> =
            context.dataStore.data.map { preferences -> preferences[key] ?: default }.take(1)

        return valueFlow.first()
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

data class ModelDigests(
    val encoder_digest: String,
    val decoder_digest: String,
    val vocab_digest: String
)


enum class PromptingStyle {
    // <|startoftranscript|><|notimestamps|> Text goes here.<|endoftext|>
    SingleLanguageOnly,

    // <|startoftranscript|><|en|><|transcribe|><|notimestamps|> Text goes here.<|endoftext|>
    LanguageTokenAndAction,
}


data class ModelData(
    val name: String,

    val is_builtin_asset: Boolean,
    val encoder_xatn_file: String,
    val decoder_file: String,

    val vocab_file: String,
    val vocab_raw_asset: Int? = null,

    val digests: ModelDigests,

    val promptingStyle: PromptingStyle
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
    @Suppress("NAME_SHADOWING") val models = models.filter { this.modelNeedsDownloading(it) }
    if(models.isEmpty()) return

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

val ENGLISH_MODELS = listOf(
    // TODO: The names are not localized
    ModelData(
        name = "English-39 (default)",

        is_builtin_asset = true,
        encoder_xatn_file = "tiny-en-encoder-xatn.tflite",
        decoder_file = "tiny-en-decoder.tflite",

        vocab_file = "tinyenvocab.json",
        vocab_raw_asset = R.raw.tinyenvocab,

        digests = ModelDigests("", "", ""),

        promptingStyle = PromptingStyle.SingleLanguageOnly
    ),
    ModelData(
        name = "English-74 (slower, more accurate)",

        is_builtin_asset = false,
        encoder_xatn_file = "base.en-encoder-xatn.tflite",
        decoder_file = "base.en-decoder.tflite",

        vocab_file = "base.en-vocab.json",

        digests = ModelDigests(
            encoder_digest = "c94bcafb3cd95c193ca5ada5b94e517f1645dbf72e72986f55c6c8729d04da23",
            decoder_digest = "d6979b4a06416ff1d3e38a238997e4051684c60aa2fcab6ae7d7dbafab75494f",
            vocab_digest = "48d9307faad7c1c1a708fbfa7f4b57cb6d5936ceee4cdf354e2b7d8cdf0cf24b"
        ),

        promptingStyle = PromptingStyle.SingleLanguageOnly
    )
)

val MULTILINGUAL_MODELS = listOf(
    ModelData(
        name = "Multilingual-39 (less accurate)",

        is_builtin_asset = false,
        encoder_xatn_file = "tiny-multi-encoder-xatn.tflite",
        decoder_file = "tiny-multi-decoder.tflite",

        vocab_file = "tiny-multi-vocab.json",

        digests = ModelDigests(
            encoder_digest = "1240660ec64f549052cd469aef7ee6ff30ecd9bf45dafcf330f2a77d20f081ee",
            decoder_digest = "1c19e4d891a9bb976023bdacefa475a9cefb522c3c1d722f2b98a1e3e08d4a2c",
            vocab_digest = "bd5b181b5ea2b5ea58b1f4ef8c48c7636e66443c212a5d9fb4dfe5bae15d6055"
        ),

        promptingStyle = PromptingStyle.LanguageTokenAndAction
    ),
    ModelData(
        name = "Multilingual-74 (default)",

        is_builtin_asset = false,
        encoder_xatn_file = "base-encoder-xatn.tflite",
        decoder_file = "base-decoder.tflite",

        vocab_file = "base-vocab.json",

        digests = ModelDigests(
            encoder_digest = "8832248eefbc1b7a297ac12358357001c613da4183099966fbb6950079d252f8",
            decoder_digest = "3369ab7e0ec7ebf828cef7a5740a6d32e1e90502737b89812e383c76041f878b",
            vocab_digest = "bd5b181b5ea2b5ea58b1f4ef8c48c7636e66443c212a5d9fb4dfe5bae15d6055"
        ),

        promptingStyle = PromptingStyle.LanguageTokenAndAction
    ),
    ModelData(
        name = "Multilingual-244 (slow)",

        is_builtin_asset = false,
        encoder_xatn_file = "small-encoder-xatn.tflite",
        decoder_file = "small-decoder.tflite",

        vocab_file = "small-vocab.json",

        digests = ModelDigests(
            encoder_digest = "03e141a363cbb983799dbf589e53298324bc1dc906eb8fabc8a412d40338f0d9",
            decoder_digest = "1dbdeac1c0fabede5aa57424b7e1e8061f34c6f646fa1031e8aead20a25f4e41",
            vocab_digest = "bd5b181b5ea2b5ea58b1f4ef8c48c7636e66443c212a5d9fb4dfe5bae15d6055"
        ),

        promptingStyle = PromptingStyle.LanguageTokenAndAction
    ),
)

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")
val ENABLE_SOUND = booleanPreferencesKey("enable_sounds")
val VERBOSE_PROGRESS = booleanPreferencesKey("verbose_progress")
val ENABLE_ENGLISH = booleanPreferencesKey("enable_english")
val ENABLE_MULTILINGUAL = booleanPreferencesKey("enable_multilingual")
val DISALLOW_SYMBOLS = booleanPreferencesKey("disallow_symbols")

val ENGLISH_MODEL_INDEX = intPreferencesKey("english_model_index")
val ENGLISH_MODEL_INDEX_DEFAULT = 0

val MULTILINGUAL_MODEL_INDEX = intPreferencesKey("multilingual_model_index")
val MULTILINGUAL_MODEL_INDEX_DEFAULT = 1

val LANGUAGE_TOGGLES = stringSetPreferencesKey("enabled_languages")

val IS_ALREADY_PAID = booleanPreferencesKey("already_paid")
val IS_PAYMENT_PENDING = booleanPreferencesKey("payment_pending")
val HAS_SEEN_PAID_NOTICE = booleanPreferencesKey("seen_paid_notice")
val FORCE_SHOW_NOTICE = booleanPreferencesKey("force_show_notice")

// UNIX timestamp in seconds of when to next show the payment reminder
val NOTICE_REMINDER_TIME = longPreferencesKey("notice_reminder_time")

val LAST_UPDATE_CHECK_RESULT = stringPreferencesKey("last_update_check_result_${BuildConfig.FLAVOR}")

val EXT_LICENSE_KEY = stringPreferencesKey("license_key")
val EXT_PENDING_PURCHASE_ID = stringPreferencesKey("purchase_id")
val EXT_PENDING_PURCHASE_LAST_CHECK = longPreferencesKey("purchase_status_last_check")

val IS_VAD_ENABLED = booleanPreferencesKey("enable_vad")
val USE_LANGUAGE_SPECIFIC_MODELS = booleanPreferencesKey("USE_LANGUAGE_SPECIFIC_MODELS")

val ALLOW_UNDERTRAINED_LANGUAGES = booleanPreferencesKey("allow_undertrained_languages")
val MANUALLY_SELECT_LANGUAGE = booleanPreferencesKey("manually_select_language")