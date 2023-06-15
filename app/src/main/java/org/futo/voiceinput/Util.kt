package org.futo.voiceinput

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.preferencesDataStore

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


val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")
val ENABLE_SOUND = booleanPreferencesKey("enable_sounds")