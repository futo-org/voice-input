package org.futo.voiceinput

import android.app.Application
import android.content.ComponentCallbacks2
import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.acra.config.dialog
import org.acra.config.httpSender
import org.acra.data.StringFormat
import org.acra.ktx.initAcra
import org.acra.sender.HttpSender
import org.futo.voiceinput.settings.DataStoreHelper
import org.futo.voiceinput.shared.whisper.ModelManager

class CrashLoggingApplication : Application(), ComponentCallbacks2 {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val modelManager = ModelManager(this)
    companion object {
        private var libraryInitialized = false

        @JvmStatic
        fun getModelManager(context: Context): ModelManager? {
            if(!libraryInitialized) {
                System.loadLibrary("voiceinput")
                libraryInitialized = true
            }
            return (context.applicationContext as? CrashLoggingApplication)?.modelManager
        }
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)

        when (level) {
            TRIM_MEMORY_BACKGROUND -> {
                applicationScope.launch { modelManager.cleanUp() }
            }

            TRIM_MEMORY_UI_HIDDEN -> {

            }
        }
    }

    override fun attachBaseContext(base: Context?) {
        super.attachBaseContext(base)

        if(BuildConfig.ENABLE_ACRA) {
            initAcra {
                reportFormat = StringFormat.JSON

                dialog {
                    text = getString(R.string.crashed_text)
                    title = getString(R.string.crashed_title)
                    positiveButtonText = getString(R.string.crash_report_accept)
                    negativeButtonText = getString(R.string.crash_report_reject)
                    resTheme = android.R.style.Theme_DeviceDefault_Dialog
                }

                httpSender {
                    uri = BuildConfig.ACRA_URL
                    basicAuthLogin = BuildConfig.ACRA_USER
                    basicAuthPassword = BuildConfig.ACRA_PASSWORD
                    httpMethod = HttpSender.Method.POST
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        DataStoreHelper.init(this)
    }
}