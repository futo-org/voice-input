package org.futo.voiceinput.settings

import android.Manifest
import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import android.speech.RecognizerIntent
import android.view.inputmethod.InputMethodManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.futo.voiceinput.Status
import kotlin.time.Duration.Companion.milliseconds


@Composable
fun useIsInputMethodEnabled(i: Int): MutableState<Status> {
    val enabled = remember { mutableStateOf(Status.Unknown) }

    val context = LocalContext.current
    LaunchedEffect(i) {
        val packageName = context.packageName
        val imm =
            context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager

        var found = false
        for (imi in imm.enabledInputMethodList) {
            if (packageName == imi.packageName) {
                found = true
            }
        }

        enabled.value = Status.from(found)
    }

    return enabled
}

@Composable
fun useIsMicrophonePermitted(i: Int): MutableState<Status> {
    val permitted = rememberSaveable { mutableStateOf(Status.Unknown) }

    val context = LocalContext.current
    LaunchedEffect(i) {
        permitted.value = Status.from(
            context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        )
    }

    return permitted
}


@Composable
fun useDefaultIME(any: Any): MutableState<String> {
    val defaultIME = remember { mutableStateOf("?") }

    val context = LocalContext.current
    LaunchedEffect(any) {
        val value =
            Settings.Secure.getString(context.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
        defaultIME.value = value
    }

    return defaultIME
}


enum class KeyboardVoiceInputConfigState {
    Unknown,
    KeyboardOutdated,
    NotSet,
    Set
}

@Composable
fun useIsConfiguredInFUTOKeyboard(defaultIme: String, any: Any, doSwitch: Boolean): MutableState<KeyboardVoiceInputConfigState> {
    val currentState = remember { mutableStateOf(KeyboardVoiceInputConfigState.Unknown) }
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        if (it.resultCode == 67 || it.resultCode == 66 || it.resultCode == 65) {
            currentState.value = KeyboardVoiceInputConfigState.Set
        } else if(it.resultCode == 68 || it.resultCode == Activity.RESULT_CANCELED) {
            currentState.value = KeyboardVoiceInputConfigState.NotSet
        } else {
            currentState.value = KeyboardVoiceInputConfigState.KeyboardOutdated
        }
    }

    val context = LocalContext.current
    LaunchedEffect(defaultIme, any, doSwitch) {
        val pkg = defaultIme.split('/')[0]
        val intent = Intent("org.futo.inputmethod.latin.action.VoiceInputSwitch")
        intent.setPackage(pkg)
        intent.putExtra("targetPackage", context.packageName)

        val mode = when {
            doSwitch && context.packageName.startsWith("org.futo") -> "switch"
            doSwitch -> "popup"
            else -> "check"
        }

        intent.putExtra("mode", mode)

        try {
            launcher.launch(intent)
        } catch(e: Exception) {
            // gets reached if activity does not exist (means that the keyboard's outdated for sure)
            currentState.value = KeyboardVoiceInputConfigState.KeyboardOutdated
        }
    }

    // If for some reason we never get a result or something like that, since the visibility depends
    // on this not being Unknown, just assume the keyboard is outdated after a period of two seconds
    LaunchedEffect(any) {
        delay(2000L.milliseconds)
        if(currentState.value == KeyboardVoiceInputConfigState.Unknown) {
            currentState.value = KeyboardVoiceInputConfigState.KeyboardOutdated
        }
    }

    return currentState
}

@Composable
fun useNumberOfDaysInstalled(): MutableState<Int> {
    if (LocalInspectionMode.current) {
        return remember { mutableStateOf(55) }
    }

    val dayCount = remember { mutableStateOf(-1) }

    val context = LocalContext.current
    LaunchedEffect(Unit) {
        val packageManager = context.packageManager
        val packageInfo = packageManager.getPackageInfo(context.packageName, 0)

        val firstInstallTime = packageInfo.firstInstallTime

        val currentTime = System.currentTimeMillis()

        val diff = (currentTime - firstInstallTime) / (1000 * 60 * 60 * 24)
        dayCount.value = diff.toInt()
    }

    return dayCount
}



data class DataStoreItem<T>(val value: T, val setValue: (T) -> Job)

@Composable
fun <T> useDataStore(key: Preferences.Key<T>, default: T): DataStoreItem<T> {
    val value = useDataStoreValueNullable(key, default) ?: default

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val setValue = { newValue: T ->
        coroutineScope.launch {
            context.dataStore.edit { preferences ->
                preferences[key] = newValue
            }
        }
    }

    return DataStoreItem(value, setValue)
}

@Composable
fun <T> useDataStore(setting: SettingsKey<T>): DataStoreItem<T> {
    return useDataStore(key = setting.key, default = setting.default)
}

@Composable
fun <T> useDataStoreValueNullable(key: Preferences.Key<T>, default: T): T {
    val context = LocalContext.current

    val valueFlow: Flow<T> = remember {
        context.dataStore.data.map { preferences ->
            preferences[key] ?: default
        }
    }

    val initial = remember(key) {
        context.getSetting(key, default)
    }

    return valueFlow.collectAsState(initial = initial).value
}



enum class DefaultVoiceInputIntentKind {
    NO_DEFAULT,
    FUTO_VOICE_INPUT,
    OTHER
}

data class DefaultVoiceInputIntent(
    val kind: DefaultVoiceInputIntentKind,
    val name: ComponentName?
)

@Composable
fun useDefaultVoiceInputIntent(i: Int): MutableState<DefaultVoiceInputIntent> {
    val default = remember { mutableStateOf(DefaultVoiceInputIntent(
        kind = DefaultVoiceInputIntentKind.NO_DEFAULT,
        name = null
    )) }

    val context = LocalContext.current
    LaunchedEffect(i) {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        val packageManager = context.packageManager
        val activity = intent.resolveActivity(packageManager)

        val kind = if(activity.className == "com.android.internal.app.ResolverActivity") {
            DefaultVoiceInputIntentKind.NO_DEFAULT
        } else if(activity.className == "org.futo.voiceinput.RecognizeActivity") {
            DefaultVoiceInputIntentKind.FUTO_VOICE_INPUT
        } else {
            DefaultVoiceInputIntentKind.OTHER
        }

        default.value = DefaultVoiceInputIntent(kind, activity)
    }

    return default
}