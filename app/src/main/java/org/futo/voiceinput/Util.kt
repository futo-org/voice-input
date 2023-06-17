package org.futo.voiceinput

import android.content.Context
import android.content.Intent
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import org.futo.voiceinput.downloader.DownloadActivity
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

data class ModelData(
    val name: String,

    val is_builtin_asset: Boolean,
    val encoder_xatn_file: String,
    val decoder_file: String,

    val vocab_file: String,
    val vocab_raw_asset: Int? = null
)

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
val ENABLE_ENGLISH = booleanPreferencesKey("enable_english")
val ENABLE_MULTILINGUAL = booleanPreferencesKey("enable_multilingual")