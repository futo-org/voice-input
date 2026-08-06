package org.futo.voiceinput.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.preferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.futo.voiceinput.BuildConfig
import org.futo.voiceinput.theme.presets.DevThemeYellow
import org.futo.voiceinput.theme.presets.VoiceInputTheme

class DataStoreHelper {
    @OptIn(DelicateCoroutinesApi::class)
    companion object {
        private var initialized: Boolean = false
        private var currentPreferences: Preferences = preferencesOf()

        @JvmStatic
        fun init(context: Context) {
            if(initialized) return
            initialized = true

            runBlocking {
                context.dataStore.data.first().let {
                    currentPreferences = it
                }
            }

            GlobalScope.launch {
                context.dataStore.data.collect {
                    currentPreferences = it
                }
            }
        }

        @JvmStatic
        fun<T> getSettingOrNull(key: Preferences.Key<T>): T? = currentPreferences[key]

        @JvmStatic
        fun<T> getSetting(key: Preferences.Key<T>, default: T): T = getSettingOrNull(key) ?: default

        @JvmStatic
        fun<T> getSettingOrNull(setting: SettingsKey<T>): T? = getSettingOrNull(setting.key)

        @JvmStatic
        fun<T> getSetting(setting: SettingsKey<T>): T = getSettingOrNull(setting.key) ?: setting.default
    }
}

data class SettingsKey<T>(
    val key: Preferences.Key<T>,
    val default: T
)

fun <T> Context.getSetting(key: Preferences.Key<T>, default: T): T {
    return DataStoreHelper.getSetting(key, default)
}

fun <T> Context.getSettingFlow(key: Preferences.Key<T>, default: T): Flow<T> {
    return dataStore.data.map { preferences -> preferences[key] ?: default }.distinctUntilChanged()
}

fun <T> Context.getSetting(key: SettingsKey<T>): T {
    return getSetting(key.key, key.default)
}

fun <T> Context.getSettingFlow(key: SettingsKey<T>): Flow<T> {
    return getSettingFlow(key.key, key.default)
}

suspend fun <T> Context.setSetting(key: Preferences.Key<T>, value: T) {
    this.dataStore.edit { preferences ->
        preferences[key] = value
    }
}

suspend fun <T> Context.setSetting(key: SettingsKey<T>, value: T) {
    return setSetting(key.key, value)
}


val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")
val ENABLE_SOUND = SettingsKey(booleanPreferencesKey("enable_sounds"), true)
val ENABLE_ANIMATIONS = SettingsKey(booleanPreferencesKey("enable_animations"), true)
val VERBOSE_PROGRESS = SettingsKey(booleanPreferencesKey("verbose_progress"), false)
val ENABLE_MULTILINGUAL = SettingsKey(booleanPreferencesKey("enable_multilingual"), false)
val DISALLOW_SYMBOLS = SettingsKey(booleanPreferencesKey("disallow_symbols"), true)
val ENABLE_30S_LIMIT = SettingsKey(booleanPreferencesKey("enable_30s_limit"), false)

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
    default = if(BuildConfig.FLAVOR == "dev" || BuildConfig.FLAVOR == "devSameId") { DevThemeYellow.key } else { VoiceInputTheme.key }
)

val BEAM_SEARCH = SettingsKey(key = booleanPreferencesKey("use_beam_search"), default = true)