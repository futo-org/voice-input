package org.futo.voiceinput.migration

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import org.futo.voiceinput.isUsingTfliteLegacy
import java.util.concurrent.CountDownLatch


class ModelMigrationJob : JobService() {
    private var job: Job? = null
    override fun onStartJob(params: JobParameters?): Boolean {
        Log.d("MigrationJob", "Starting migration job")
        job = CoroutineScope(Dispatchers.IO).launch {
            val httpClient = OkHttpClient()
            val modelsToDownload = getModelsToDownload(applicationContext)

            val latch = CountDownLatch(1)
            downloadModels(applicationContext, modelsToDownload, httpClient, updateContent = {
                if(modelsToDownload.all { it.error || it.finished }) {
                    latch.countDown()
                }
            }, onFinish = { })

            latch.await()

            if(modelsToDownload.all { it.finished }) {
                Log.d("MigrationJob", "All downloads finished successfully, deleting old models")
                deleteLegacyModels(applicationContext)
            } else {
                Log.d("MigrationJob", "One or more files failed to download")
            }

            jobFinished(params, false)
        }
        return true
    }

    override fun onStopJob(params: JobParameters?): Boolean {
        job?.cancel()

        return false
    }
}


const val MIGRATION_JOB_ID: Int = 15782788
fun scheduleModelMigrationJob(context: Context) {
    if(!context.isUsingTfliteLegacy()){
        Log.d("MigrationJob", "Avoiding scheduling due to no legacy models")
        return
    }

    val jobScheduler = context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler

    if(jobScheduler.getPendingJob(MIGRATION_JOB_ID) != null) {
        Log.d("MigrationJob", "Avoiding scheduling due to already being scheduled")
        return
    }

    var jobInfoBuilder = JobInfo.Builder(MIGRATION_JOB_ID, ComponentName(context, ModelMigrationJob::class.java))
        .setRequiredNetworkType(JobInfo.NETWORK_TYPE_UNMETERED) // on unmetered Wi-Fi
        .setPersisted(false) // persist after reboots


    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        jobInfoBuilder = jobInfoBuilder.setEstimatedNetworkBytes(100000000L, 0L)
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        jobInfoBuilder = jobInfoBuilder.setPriority(JobInfo.PRIORITY_DEFAULT)
    }

    jobScheduler.schedule(jobInfoBuilder.build())
    Log.d("MigrationJob", "Scheduled migration job")
}