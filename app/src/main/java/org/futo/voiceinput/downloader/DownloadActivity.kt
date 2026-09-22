package org.futo.voiceinput.downloader

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment.Companion.CenterVertically
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.internal.closeQuietly
import org.futo.voiceinput.CrashLoggingApplication
import org.futo.voiceinput.R
import org.futo.voiceinput.fileNeedsDownloading
import org.futo.voiceinput.settings.ScreenTitle
import org.futo.voiceinput.settings.SettingsRootList
import org.futo.voiceinput.theme.Typography
import org.futo.voiceinput.theme.UixThemeAuto
import java.io.File
import java.io.IOException


data class ModelInfo(
    val name: String,
    val url: String,
    val size: MutableState<Long?> = mutableStateOf(null),
    val progress: MutableState<Float> = mutableStateOf(0.0f),
    val error: MutableState<Boolean> = mutableStateOf(false),
    val finished: MutableState<Boolean> = mutableStateOf(false),
)

val EXAMPLE_MODELS = listOf(
    ModelInfo(
        name = "tiny-encoder-xatn.tflite",
        url = "example.com",
        size = mutableStateOf(56L * 1024L * 1024L),
        progress = mutableStateOf(0.5f),
        error = mutableStateOf(true)
    ),
    ModelInfo(
        name = "tiny-decoder.tflite",
        url = "example.com",
        size = mutableStateOf(73L * 1024L * 1024L),
        progress = mutableStateOf(0.3f),
        error = mutableStateOf(false)
    ),
)

@Composable
fun ModelItem(model: ModelInfo, showProgress: Boolean) {
    Column(modifier = Modifier.padding(16.dp, 8.dp)) {
        val color = if (model.error.value) {
            MaterialTheme.colorScheme.errorContainer
        } else {
            MaterialTheme.colorScheme.primaryContainer
        }
        Surface(modifier = Modifier, color = color, shape = RoundedCornerShape(4.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
            ) {
                if (model.error.value) {
                    Icon(
                        Icons.Default.Warning, contentDescription = "Failed", modifier = Modifier
                            .align(CenterVertically)
                            .padding(4.dp)
                    )
                }

                val size = model.size.value?.let {
                    "%.1f".format(it.toFloat() / 1000000.0f)
                } ?: "?"

                Column {
                    Text(model.name, style = Typography.Body.RegularMl)
                    Text(
                        "$size MB",
                        style = Typography.Body.RegularMl,
                        color = MaterialTheme.colorScheme.outline
                    )
                    if (showProgress && !model.error.value) {
                        LinearProgressIndicator(
                            progress = { model.progress.value },
                            modifier = Modifier.fillMaxWidth().padding(0.dp, 8.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            trackColor = ProgressIndicatorDefaults.linearTrackColor,
                            strokeCap = ProgressIndicatorDefaults.LinearStrokeCap,
                        )
                    }
                }

            }
        }
    }
}

@Composable
@Preview(showBackground = true)
fun DownloadPromptScreen(
    onContinue: () -> Unit = {},
    onCancel: () -> Unit = {},
    models: List<ModelInfo> = EXAMPLE_MODELS
) {
    SettingsRootList {
        ScreenTitle(stringResource(R.string.download_required))

        Text(
            stringResource(R.string.download_required_body),
            modifier = Modifier.padding(16.dp, 0.dp),
            style = Typography.Body.RegularMl
        )

        Spacer(modifier = Modifier.height(8.dp))

        models.forEach { ModelItem(it, showProgress = false) }

        Spacer(modifier = Modifier.height(8.dp))

        Row {
            Button(
                onClick = onCancel, colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondary,
                    contentColor = MaterialTheme.colorScheme.onSecondary
                ), modifier = Modifier
                    .padding(8.dp)
                    .weight(1.0f)
            ) {
                Text(stringResource(R.string.cancel))
            }
            Button(
                onClick = onContinue, modifier = Modifier
                    .padding(8.dp)
                    .weight(1.5f)
            ) {
                Text(stringResource(R.string.continue_))
            }
        }
    }
}

@Composable
@Preview(showBackground = true)
fun DownloadScreen(models: List<ModelInfo> = EXAMPLE_MODELS) {
    SettingsRootList {
        ScreenTitle(stringResource(R.string.download_progress))
        if (models.any { it.error.value }) {
            Text(
                stringResource(R.string.download_failed),
                modifier = Modifier.padding(16.dp, 0.dp),
                style = Typography.Body.RegularMl
            )
        } else {
            Text(
                stringResource(R.string.download_in_progress),
                modifier = Modifier.padding(16.dp, 0.dp),
                style = Typography.Body.RegularMl
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        models.forEach { ModelItem(it, showProgress = true) }
    }
}

class DownloadActivity : ComponentActivity() {
    companion object {
        val downloadsCompleted = mutableIntStateOf(0)
    }
    private val modelsToDownload: MutableList<ModelInfo> = mutableStateListOf()
    private val cleanupList: MutableList<String> = mutableListOf()
    private val httpClient = OkHttpClient()
    private val isDownloading = mutableStateOf(false)

    private fun updateContent() {
        setContent {
            UixThemeAuto {
                // A surface container using the 'background' color from the theme
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    if (isDownloading.value) {
                        DownloadScreen(models = modelsToDownload)
                    } else {
                        DownloadPromptScreen(
                            onContinue = { startDownload() },
                            onCancel = { cancel() },
                            models = modelsToDownload
                        )
                    }
                }
            }
        }
    }

    private fun startDownload() {
        isDownloading.value = true

        modelsToDownload.forEach {
            val request = Request.Builder().method("GET", null).url(it.url).build()

            httpClient.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    it.error.value = true
                }

                override fun onResponse(call: Call, response: Response) {
                    if(response.code != 200) {
                        println("url failed ${response.code}: ${it.url}")
                        it.error.value = true
                        return
                    }

                    response.body?.source()?.let { source ->
                        val fileName = it.name + ".download"
                        val file =
                            File.createTempFile(fileName, null, this@DownloadActivity.cacheDir)
                        val os = file.outputStream()

                        val buffer = ByteArray(128 * 1024)
                        var downloaded = 0
                        while (true) {
                            val read = source.read(buffer)
                            if (read == -1) {
                                break
                            }

                            os.write(buffer.sliceArray(0 until read))

                            downloaded += read

                            it.progress.value = it.size.value?.let {
                                downloaded.toFloat() / it.toFloat()
                            } ?: 0.0f
                        }

                        it.finished.value = true
                        it.progress.value = 1.0f
                        os.flush()
                        os.close()

                        source.closeQuietly()

                        if(it.error.value) return@let
                        if(!file.renameTo(File(this@DownloadActivity.filesDir, it.name))) {
                            throw IllegalStateException()
                        }

                        if (modelsToDownload.all { a -> a.finished.value }) {
                            downloadsFinished()
                        }
                    }
                }
            })
        }
    }

    private fun cancel() {
        val returnIntent = Intent()
        setResult(RESULT_CANCELED, returnIntent)
        finish()
    }

    private fun downloadsFinished() {
        cleanupList.forEach {
            val file = File(this@DownloadActivity.filesDir, it)
            if(file.isFile) file.delete()
        }

        // Unload old stale models
        @OptIn(DelicateCoroutinesApi::class)
        GlobalScope.launch {
            CrashLoggingApplication.getModelManager(this@DownloadActivity)?.cleanUp()
        }

        downloadsCompleted.intValue += 1

        val returnIntent = Intent()
        setResult(RESULT_OK, returnIntent)
        finish()
    }

    private fun obtainModelSizes() {
        modelsToDownload.forEach {
            val request =
                Request.Builder().method("HEAD", null).header("accept-encoding", "identity")
                    .url(it.url).build()

            httpClient.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    it.error.value = true
                }

                override fun onResponse(call: Call, response: Response) {
                    try {
                        it.size.value = response.headers["content-length"]!!.toLong()
                    } catch (e: Exception) {
                        println("url failed ${it.url}")
                        println(response.headers)
                        e.printStackTrace()
                        it.error.value = true
                    }

                    if (response.code != 200) {
                        println("Bad response code ${response.code}")
                        it.error.value = true
                    }

                    response.closeQuietly()
                }
            })
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val models = intent.getStringArrayListExtra("models")
            ?: throw IllegalStateException("intent extra `models` must be specified for DownloadActivity")

        modelsToDownload.clear()

        updateContent()

        modelsToDownload.addAll(models.filter { this.fileNeedsDownloading(it) }.map {
            ModelInfo(
                name = it,
                url = "https://dl.voiceinput.futo.org/VoiceInput/${it}",
            )
        })

        if (modelsToDownload.isEmpty()) {
            cancel()
        }

        cleanupList.clear()
        intent.getStringArrayListExtra("cleanup")?.forEach { cleanupList.add(it) }

        isDownloading.value = false

        obtainModelSizes()
    }
}
