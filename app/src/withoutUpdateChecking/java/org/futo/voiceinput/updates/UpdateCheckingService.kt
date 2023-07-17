package org.futo.voiceinput.updates

// Updates are handled by Google Play, do not do any checking
import android.content.Context
import android.app.job.JobParameters
import android.app.job.JobService

class UpdateCheckingService : JobService() {
    override fun onStartJob(params: JobParameters?): Boolean {
        return false
    }

    override fun onStopJob(params: JobParameters?): Boolean {
        return false
    }
}