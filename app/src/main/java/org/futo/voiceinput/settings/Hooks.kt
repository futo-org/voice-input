package org.futo.voiceinput.settings

import android.Manifest
import android.content.pm.PackageManager
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import androidx.activity.ComponentActivity
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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.futo.voiceinput.Status
import org.futo.voiceinput.dataStore


@Composable
fun useIsInputMethodEnabled(i: Int): MutableState<Status> {
    val enabled = remember { mutableStateOf(Status.Unknown) }

    val context = LocalContext.current
    LaunchedEffect(i) {
        val packageName = context.packageName
        val imm = context.getSystemService(ComponentActivity.INPUT_METHOD_SERVICE) as InputMethodManager

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
fun useDefaultIME(i: Int): MutableState<String> {
    val defaultIME = remember { mutableStateOf("?") }

    val context = LocalContext.current
    LaunchedEffect(i) {
        val value = Settings.Secure.getString(context.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
        defaultIME.value = value
        println("The default IME is $value")
    }

    return defaultIME
}


@Composable
fun useNumberOfDaysInstalled() : MutableState<Int> {
    if(LocalInspectionMode.current) {
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
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val enableSoundFlow: Flow<T> = remember {
        context.dataStore.data.map {
                preferences -> preferences[key] ?: default
        }
    }

    val value = enableSoundFlow.collectAsState(initial = default).value!!

    val setValue = { newValue: T ->
        coroutineScope.launch {
            context.dataStore.edit { preferences ->
                preferences[key] = newValue
            }
        }
    }

    return DataStoreItem(value, setValue)
}