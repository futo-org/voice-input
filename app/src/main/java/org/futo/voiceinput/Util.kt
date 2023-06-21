package org.futo.voiceinput

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
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

fun Context.startModelDownloadActivity(model: ModelData) {
    if(!this.modelNeedsDownloading(model)) return

    val intent = Intent(this, DownloadActivity::class.java)
    intent.putStringArrayListExtra("models", arrayListOf(
        model.encoder_xatn_file,
        model.decoder_file,
        model.vocab_file
    ))

    if(this !is Activity) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    startActivity(intent)
}

val TINY_ENGLISH_MODEL_DATA = ModelData(
    name = "tiny-en",

    is_builtin_asset = true,
    encoder_xatn_file = "tiny-en-encoder-xatn.tflite",
    decoder_file = "tiny-en-decoder.tflite",

    vocab_file = "tinyenvocab.json",
    vocab_raw_asset = R.raw.tinyenvocab
)

val TINY_MULTILINGUAL_MODEL_DATA = ModelData(
    name = "tiny-multilingual",

    is_builtin_asset = false,
    encoder_xatn_file = "tiny-multi-encoder-xatn.tflite",
    decoder_file = "tiny-multi-decoder.tflite",

    vocab_file = "tiny-multi-vocab.json",
)

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")
val ENABLE_SOUND = booleanPreferencesKey("enable_sounds")
val VERBOSE_PROGRESS = booleanPreferencesKey("verbose_progress")
val ENABLE_ENGLISH = booleanPreferencesKey("enable_english")
val ENABLE_MULTILINGUAL = booleanPreferencesKey("enable_multilingual")
val DISALLOW_SYMBOLS = booleanPreferencesKey("disallow_symbols")