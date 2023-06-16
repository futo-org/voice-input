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

fun Context.modelNeedsDownloading(model: String): Boolean {
    return !File(this.filesDir, "${model}.tflite").exists()
}

fun Context.startModelDownloadActivity(model: String) {
    val intent = Intent(this, DownloadActivity::class.java)
    intent.putStringArrayListExtra("models", arrayListOf(MULTILINGUAL_MODEL_NAME))

    startActivity(intent)
}

val MULTILINGUAL_MODEL_NAME = "tiny-multi"

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")
val ENABLE_SOUND = booleanPreferencesKey("enable_sounds")
val ENABLE_ENGLISH = booleanPreferencesKey("enable_english")
val ENABLE_MULTILINGUAL = booleanPreferencesKey("enable_multilingual")