package org.futo.voiceinput.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.futo.voiceinput.BuildConfig
import org.futo.voiceinput.theme.presets.VoiceInputTheme

suspend fun <T> Context.getSetting(key: Preferences.Key<T>, default: T): T {
    val valueFlow: Flow<T> =
        this.dataStore.data.map { preferences -> preferences[key] ?: default }.take(1)

    return valueFlow.first()
}

fun <T> Context.getSettingFlow(key: Preferences.Key<T>, default: T): Flow<T> {
    return dataStore.data.map { preferences -> preferences[key] ?: default }.take(1)
}

suspend fun <T> Context.setSetting(key: Preferences.Key<T>, value: T) {
    this.dataStore.edit { preferences ->
        preferences[key] = value
    }
}


fun <T> Context.getSettingBlocking(key: Preferences.Key<T>, default: T): T {
    val context = this

    return runBlocking {
        context.getSetting(key, default)
    }
}

fun <T> Context.setSettingBlocking(key: Preferences.Key<T>, value: T) {
    val context = this
    runBlocking {
        context.setSetting(key, value)
    }
}

fun <T> LifecycleOwner.deferGetSetting(key: Preferences.Key<T>, default: T, onObtained: (T) -> Unit): Job {
    val context = (this as Context)
    return lifecycleScope.launch {
        withContext(Dispatchers.Default) {
            val value = context.getSetting(key, default)

            withContext(Dispatchers.Main) {
                onObtained(value)
            }
        }
    }
}

fun <T> LifecycleOwner.deferSetSetting(key: Preferences.Key<T>, value: T): Job {
    val context = (this as Context)
    return lifecycleScope.launch {
        withContext(Dispatchers.Default) {
            context.setSetting(key, value)
        }
    }
}

data class SettingsKey<T>(
    val key: Preferences.Key<T>,
    val default: T
)

suspend fun <T> Context.getSetting(key: SettingsKey<T>): T {
    return getSetting(key.key, key.default)
}

fun <T> Context.getSettingFlow(key: SettingsKey<T>): Flow<T> {
    return getSettingFlow(key.key, key.default)
}

suspend fun <T> Context.setSetting(key: SettingsKey<T>, value: T) {
    return setSetting(key.key, value)
}

fun <T> LifecycleOwner.deferGetSetting(key: SettingsKey<T>, onObtained: (T) -> Unit): Job {
    return deferGetSetting(key.key, key.default, onObtained)
}

fun <T> LifecycleOwner.deferSetSetting(key: SettingsKey<T>, value: T): Job {
    return deferSetSetting(key.key, value)
}


val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")
val ENABLE_SOUND = SettingsKey(booleanPreferencesKey("enable_sounds"), true)
val ENABLE_ANIMATIONS = SettingsKey(booleanPreferencesKey("enable_animations"), true)
val VERBOSE_PROGRESS = SettingsKey(booleanPreferencesKey("verbose_progress"), false)
val ENABLE_MULTILINGUAL = SettingsKey(booleanPreferencesKey("enable_multilingual"), false)
val DISALLOW_SYMBOLS = SettingsKey(booleanPreferencesKey("disallow_symbols"), true)

val ENGLISH_MODEL_INDEX = SettingsKey(intPreferencesKey("english_model_index"), 0)

val MULTILINGUAL_MODEL_INDEX = SettingsKey(intPreferencesKey("multilingual_model_index"), 1)

val LANGUAGE_TOGGLES = SettingsKey(stringSetPreferencesKey("enabled_languages"), setOf("en"))

val IS_ALREADY_PAID = SettingsKey(booleanPreferencesKey("already_paid"), false)
val IS_PAYMENT_PENDING = SettingsKey(booleanPreferencesKey("payment_pending"), false)
val HAS_SEEN_PAID_NOTICE = SettingsKey(booleanPreferencesKey("seen_paid_notice"), false)
val FORCE_SHOW_NOTICE = SettingsKey(booleanPreferencesKey("force_show_notice"), false)

// UNIX timestamp in seconds of when to next show the payment reminder
val NOTICE_REMINDER_TIME = SettingsKey(longPreferencesKey("notice_reminder_time"), 0L)

val LAST_UPDATE_CHECK_RESULT = SettingsKey(stringPreferencesKey("last_update_check_result_${BuildConfig.FLAVOR}"), "")

val EXT_LICENSE_KEY = SettingsKey(stringPreferencesKey("license_key"), "")
val EXT_PENDING_PURCHASE_ID = SettingsKey(stringPreferencesKey("purchase_id"), "")
val EXT_PENDING_PURCHASE_LAST_CHECK = SettingsKey(longPreferencesKey("purchase_status_last_check"), 0)

val IS_VAD_ENABLED = SettingsKey(booleanPreferencesKey("enable_vad"), true)
val USE_LANGUAGE_SPECIFIC_MODELS = SettingsKey(booleanPreferencesKey("USE_LANGUAGE_SPECIFIC_MODELS"), true)

val ALLOW_UNDERTRAINED_LANGUAGES = SettingsKey(booleanPreferencesKey("allow_undertrained_languages"), false)
val MANUALLY_SELECT_LANGUAGE = SettingsKey(booleanPreferencesKey("manually_select_language"), false)

val PERSONAL_DICTIONARY = SettingsKey(stringPreferencesKey("personal_dict"), "")

val THEME_KEY = SettingsKey(
    key = stringPreferencesKey("activeThemeOption"),
    default = VoiceInputTheme.key
)