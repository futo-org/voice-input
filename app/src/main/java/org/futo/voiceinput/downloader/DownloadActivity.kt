package org.futo.voiceinput.downloader

import android.Manifest
import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.futo.voiceinput.settings.SettingsViewModel
import org.futo.voiceinput.settings.SetupOrMain
import org.futo.voiceinput.ui.theme.WhisperVoiceInputTheme
import java.io.IOException
import java.lang.Exception
import java.net.URL
import java.util.concurrent.TimeUnit


data class ModelInfo(
    val name: String,
    val url: String,
    var size: Long?,
    var progress: Float = 0.0f,
    var error: Boolean = false,
    var finished: Boolean = false
)

val EXAMPLE_MODELS = listOf(ModelInfo(
    name = "tiny-multilingual",
    url = "example.com",
    size = 56L*1024L*1024L,
    progress = 0.5f
))

@Composable
fun ModelItem(model: ModelInfo = EXAMPLE_MODELS[0], showProgress: Boolean = false) {
    Column(modifier = Modifier.padding(4.dp)) {
        Text(
            "${model.name}: ${
                if (model.size != null) {
                    model.size!! / 1000000L
                } else {
                    "..."
                }
            }MB"
        )

        if (showProgress) {
            LinearProgressIndicator(progress = model.progress, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
@Preview
fun DownloadPrompt(onContinue: () -> Unit = {}, onCancel: () -> Unit = {}, models: List<ModelInfo> = EXAMPLE_MODELS) {
    Column(modifier = Modifier.padding(8.dp)) {
        Text("To continue, one or more speech recognition models need to be downloaded. This may incur data fees if you're using mobile data instead of Wi-Fi")

        Column(modifier = Modifier.padding(8.dp)) {
            models.forEach {
                ModelItem(it, showProgress = false)
            }
        }

        Row {
            Button(onClick = onContinue, modifier = Modifier.padding(8.dp).weight(1.0f)) {
                Text("Continue")
            }
            Button(onClick = onCancel, colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.secondary,
                contentColor = MaterialTheme.colorScheme.onSecondary
            ), modifier = Modifier.padding(8.dp).weight(1.0f)) {
                Text("Cancel")
            }
        }
    }
}

@Composable
@Preview
fun DownloadScreen(models: List<ModelInfo> = EXAMPLE_MODELS) {
    Column(modifier = Modifier.padding(8.dp)) {
        Text("Downloading the models...")

        Column(modifier = Modifier.padding(8.dp)) {
            models.forEach {
                ModelItem(it, showProgress = true)
            }
        }
    }
}


class DownloadActivity : ComponentActivity() {
    private lateinit var modelsToDownload: List<ModelInfo>
    val httpClient = OkHttpClient()
    var isDownloading = false

    private fun updateContent() {
        setContent {
            WhisperVoiceInputTheme {
                // A surface container using the 'background' color from the theme
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    if(isDownloading) {
                        DownloadScreen(models = modelsToDownload)
                    } else {
                        DownloadPrompt(
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
        isDownloading = true
        updateContent()
        // start download

        modelsToDownload.forEach {
            val request = Request.Builder().method("GET", null).url(it.url).build()

            httpClient.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    it.error = true
                    updateContent()
                }

                override fun onResponse(call: Call, response: Response) {
                    response.body?.source()?.let { source ->
                        val buffer = ByteArray(128 * 1024)
                        var downloaded = 0
                        while (true) {
                            val read = source.read(buffer)
                            if (read == -1) { break }

                            downloaded += read

                            it.progress = downloaded.toFloat() / it.size!!.toFloat()

                            lifecycleScope.launch {
                                withContext(Dispatchers.Main) {
                                    updateContent()
                                }
                            }
                        }

                        it.finished = true

                        if(modelsToDownload.all { a -> a.finished}) {
                            finish_()
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

    private fun finish_() {
        val returnIntent = Intent()
        setResult(RESULT_OK, returnIntent)
        finish()
    }

    private fun obtainModelSizes() {
        modelsToDownload.forEach {
            val request = Request.Builder().method("HEAD", null).url(it.url).build()

            httpClient.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    it.error = true
                    updateContent()
                }

                override fun onResponse(call: Call, response: Response) {
                    try {
                        it.size = response.headers.get("content-length")!!.toLong()
                    } catch(e: Exception) {
                        it.error = true
                    }
                    updateContent()
                }
            })
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val models = intent.getStringArrayListExtra("models")
            ?: throw IllegalStateException("intent extra `models` must be specified for DownloadActivity")

        if(models.isEmpty()) {
            return cancel()
        }

        modelsToDownload = models.map {
            ModelInfo(
                name = it,
                url = "https://april.sapples.net/futo/${it}.tflite",
                size = null,
                progress = 0.0f
            )
        }

        isDownloading = false
        updateContent()

        obtainModelSizes()
    }
}
