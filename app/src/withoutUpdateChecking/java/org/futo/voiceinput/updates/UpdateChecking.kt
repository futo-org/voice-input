package org.futo.voiceinput.updates

// Updates are handled by Google Play, do not do any checking
import android.content.Context

suspend fun checkForUpdate(): UpdateResult? {
    return null
}

suspend fun checkForUpdateAndSaveToPreferences(context: Context): Boolean {
    return false
}

suspend fun retrieveSavedLastUpdateCheckResult(context: Context): UpdateResult? {
    return null
}

fun scheduleUpdateCheckingJob(context: Context) {

}