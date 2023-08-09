package org.futo.voiceinput.updates

import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.ComponentName
import android.content.Context
import android.os.Build
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.internal.closeQuietly
import org.futo.voiceinput.BuildConfig
import org.futo.voiceinput.LAST_UPDATE_CHECK_RESULT
import org.futo.voiceinput.ValueFromSettings
import org.futo.voiceinput.dataStore
import java.lang.Exception

const val UPDATE_URL = "https://voiceinput.futo.org/VoiceInput/voice_input_version_${BuildConfig.FLAVOR}"

suspend fun checkForUpdate(): UpdateResult? {
    return withContext(Dispatchers.IO) {
        val httpClient = OkHttpClient()

        val request = Request.Builder().method("GET", null).url(UPDATE_URL).build()

        try {
            val response = httpClient.newCall(request).execute()

            val body = response.body

            val result = if (body != null) {
                val data = body.string().lines()
                body.closeQuietly()

                val latestVersion = data[0].toInt()
                val latestVersionUrl = data[1]
                val latestVersionString = data[2]
                if(latestVersionUrl.startsWith("https://")){
                    // TODO: Do some more checking with the URL to make sure it can be trusted

                    UpdateResult(
                        nextVersion = latestVersion,
                        apkUrl = latestVersionUrl,
                        nextVersionString = latestVersionString
                    )
                } else {
                    null
                }
            } else {
                null
            }

            response.closeQuietly()

            result
        } catch (e: Exception) {
            null
        }
    }
}

suspend fun checkForUpdateAndSaveToPreferences(context: Context): Boolean {
    val updateResult = checkForUpdate()
    if(updateResult != null) {
        withContext(Dispatchers.IO) {
            context.dataStore.edit {
                it[LAST_UPDATE_CHECK_RESULT] = Json.encodeToString(updateResult)
            }
        }
        return true
    }

    return false
}

suspend fun retrieveSavedLastUpdateCheckResult(context: Context): UpdateResult? {
    return UpdateResult.fromString(ValueFromSettings(LAST_UPDATE_CHECK_RESULT, "").get(context))
}

const val JOB_ID: Int = 15782789
fun scheduleUpdateCheckingJob(context: Context) {
    val jobScheduler = context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler

    if(jobScheduler.getPendingJob(JOB_ID) != null) {
        println("Job already scheduled, no need to do anything")
        return
    }

    var jobInfoBuilder = JobInfo.Builder(JOB_ID, ComponentName(context, UpdateCheckingService::class.java))
        .setPeriodic(1000 * 60 * 60 * 24 * 2) // every two days
        .setRequiredNetworkType(JobInfo.NETWORK_TYPE_UNMETERED) // on unmetered Wi-Fi
        .setPersisted(true) // persist after reboots

    // Update checking has minimum priority
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        jobInfoBuilder = jobInfoBuilder.setPriority(JobInfo.PRIORITY_MIN)
    }

    jobScheduler.schedule(jobInfoBuilder.build())
}