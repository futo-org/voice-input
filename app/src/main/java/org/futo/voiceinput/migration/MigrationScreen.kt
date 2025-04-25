package org.futo.voiceinput.migration

import android.app.Activity
import android.app.job.JobScheduler
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.futo.voiceinput.R
import org.futo.voiceinput.downloader.DownloadScreen
import org.futo.voiceinput.downloader.EXAMPLE_MODELS
import org.futo.voiceinput.downloader.ModelInfo
import org.futo.voiceinput.downloader.ModelItem
import org.futo.voiceinput.isUsingTfliteLegacy
import org.futo.voiceinput.settings.IS_ALREADY_PAID
import org.futo.voiceinput.settings.MODELS_MIGRATED
import org.futo.voiceinput.settings.NavigationItem
import org.futo.voiceinput.settings.NavigationItemStyle
import org.futo.voiceinput.settings.ScreenTitle
import org.futo.voiceinput.settings.ScrollableList
import org.futo.voiceinput.settings.useDataStore
import org.futo.voiceinput.theme.Typography
import org.futo.voiceinput.theme.UixThemeAuto
import java.io.IOException


@Composable
fun NeedsMigration(): Boolean {
    val context = LocalContext.current
    val needsUpdate = context.isUsingTfliteLegacy()
    val wasMigrated = useDataStore(setting = MODELS_MIGRATED)
    return needsUpdate && !wasMigrated.value
}

@Composable
fun ConditionalModelUpdate() {
    val context = LocalContext.current
    if(NeedsMigration()) {
        NavigationItem(
            title = stringResource(R.string.model_update),
            style = NavigationItemStyle.Misc,
            subtitle = stringResource(R.string.update_requires_your_attention),
            navigate = {
                val intent = Intent(context, MigrationActivity::class.java)

                if(context !is Activity) {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }

                context.startActivity(intent)
            }
        )
    }
}

@Composable
fun MigrateModelScreen(files: List<ModelInfo>, proceed: () -> Unit, cancel: () -> Unit) {
    ScrollableList {
        ScreenTitle(stringResource(R.string.model_update))
        Text(
            stringResource(R.string.model_update_notice),
            modifier = Modifier.padding(16.dp, 6.dp),
            style = Typography.bodyMedium
        )

        Text(
            stringResource(R.string.model_update_features_1),
            modifier = Modifier.padding(16.dp, 6.dp),
            style = Typography.bodyMedium
        )


        Spacer(modifier = Modifier.height(8.dp))

        files.forEach {
            ModelItem(model = it, showProgress = false)
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            stringResource(R.string.model_update_question),
            modifier = Modifier.padding(16.dp, 6.dp),
            style = Typography.bodyMedium
        )

        Row {
            Button(
                onClick = cancel, colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondary,
                    contentColor = MaterialTheme.colorScheme.onSecondary
                ), modifier = Modifier
                    .padding(8.dp)
                    .weight(1.0f)
            ) {
                Text(stringResource(R.string.update_later))
            }
            Button(
                onClick = proceed, modifier = Modifier
                    .padding(8.dp)
                    .weight(1.5f)
            ) {
                Text(stringResource(R.string.update_now))
            }
        }
    }
}

@Composable
fun MigrateModelCompleted(onFinish: () -> Unit) {
    val isAlreadyPaid = useDataStore(IS_ALREADY_PAID)

    ScrollableList {
        ScreenTitle(stringResource(R.string.model_update))
        Text(
            stringResource(R.string.model_update_finish),
            modifier = Modifier.padding(16.dp, 6.dp),
            style = Typography.bodyMedium
        )

        if (isAlreadyPaid.value) {
            Text(
                stringResource(R.string.support_thank_you_notice),
                modifier = Modifier.padding(16.dp, 6.dp),
                style = Typography.bodyMedium,
            )
        }

        Button(
            onClick = onFinish, colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.secondary,
                contentColor = MaterialTheme.colorScheme.onSecondary
            ), modifier = Modifier
                .padding(8.dp)
                .fillMaxWidth(1.0f)
        ) {
            Text(stringResource(id = R.string.continue_))
        }
    }
}

@Composable
fun MigrateModelNoInternet(onCancel: () -> Unit, onSettings: () -> Unit) {
    ScrollableList {
        ScreenTitle(stringResource(R.string.model_update))
        Text(
            stringResource(R.string.model_update_notice),
            modifier = Modifier.padding(16.dp, 6.dp),
            style = Typography.bodyMedium
        )

        Text(
            stringResource(R.string.model_update_features_1),
            modifier = Modifier.padding(16.dp, 6.dp),
            style = Typography.bodyMedium
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            stringResource(R.string.model_update_network_error_1),
            modifier = Modifier.padding(16.dp, 6.dp),
            style = Typography.bodyMedium
        )

        Text(
            stringResource(R.string.model_update_network_error_2),
            modifier = Modifier.padding(16.dp, 6.dp),
            style = Typography.bodyMedium
        )


        Spacer(modifier = Modifier.height(16.dp))

        Row {
            Button(
                onClick = onCancel, colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondary,
                    contentColor = MaterialTheme.colorScheme.onSecondary
                ), modifier = Modifier
                    .padding(8.dp)
                    .weight(1.0f)
            ) {
                Text(stringResource(R.string.update_later))
            }
            Button(
                onClick = onSettings, modifier = Modifier
                    .padding(8.dp)
                    .weight(1.5f)
            ) {
                Text(stringResource(R.string.check_permissions))
            }
        }
    }
}



@Preview(showBackground = true, showSystemUi = true)
@Composable
fun MigrateModelScreenPreview() {
    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        MigrateModelScreen(EXAMPLE_MODELS, {}, {})
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
fun MigrateModelNoInternetPreview() {
    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        MigrateModelNoInternet({}, {})
    }
}



class MigrationActivity : ComponentActivity() {
    private lateinit var modelsToDownload: List<ModelInfo>
    private val httpClient = OkHttpClient()
    private var isDownloading = false

    private fun openSettings() {
        val packageName = applicationContext.packageName
        val myAppSettings = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse(
                "package:$packageName"
            )
        )
        myAppSettings.addCategory(Intent.CATEGORY_DEFAULT)
        myAppSettings.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        applicationContext.startActivity(myAppSettings)

        cancel()
    }
    private fun updateContent() {
        setContent {
            UixThemeAuto {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    if (isDownloading) {
                        if(modelsToDownload.all { it.finished }) {
                            MigrateModelCompleted(onFinish = { finishSuccessfully() })
                        } else {
                            DownloadScreen(models = modelsToDownload)
                        }
                    } else {
                        if(modelsToDownload.any { it.error }) {
                            MigrateModelNoInternet(
                                onCancel = { cancel() },
                                onSettings = { openSettings() }
                            )
                        } else {
                            MigrateModelScreen(
                                files = modelsToDownload,
                                proceed = { startDownload() },
                                cancel = { cancel() }
                            )
                        }
                    }
                }
            }
        }
    }

    private fun startDownload() {
        isDownloading = true
        updateContent()

        downloadModels(applicationContext, modelsToDownload, httpClient,
            updateContent = { lifecycleScope.launch { withContext(Dispatchers.Main) { updateContent() } } },
            onFinish = { lifecycleScope.launch { withContext(Dispatchers.Main) { downloadsFinished() } } }
        )
    }

    private fun cancel() {
        val returnIntent = Intent()
        setResult(RESULT_CANCELED, returnIntent)
        finish()
    }

    private fun finishSuccessfully() {
        val returnIntent = Intent()
        setResult(RESULT_OK, returnIntent)
        finish()
    }

    private fun downloadsFinished() {
        deleteLegacyModels(applicationContext)
        updateContent()
    }

    private fun obtainModelSizes() {
        modelsToDownload.forEach {
            val request =
                Request.Builder().method("HEAD", null).header("accept-encoding", "identity")
                    .url(it.url).build()

            httpClient.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    it.error = true
                    updateContent()
                }

                override fun onResponse(call: Call, response: Response) {
                    try {
                        it.size = response.headers["content-length"]!!.toLong()
                    } catch (e: Exception) {
                        println("url failed ${it.url}")
                        println(response.headers)
                        e.printStackTrace()
                        it.error = true
                    }

                    if (response.code != 200) {
                        println("Bad response code ${response.code}")
                        it.error = true
                    }
                    updateContent()
                }
            })
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val jobScheduler = applicationContext.getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler
        jobScheduler.cancel(MIGRATION_JOB_ID)

        modelsToDownload = getModelsToDownload(applicationContext)

        if (modelsToDownload.isEmpty()) {
            downloadsFinished()
            finishSuccessfully()
        }

        isDownloading = false
        updateContent()

        obtainModelSizes()
    }
}
