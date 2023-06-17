package org.futo.voiceinput.downloader

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
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
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.internal.headersContentLength
import org.futo.voiceinput.fileNeedsDownloading
import org.futo.voiceinput.modelNeedsDownloading
import org.futo.voiceinput.ui.theme.WhisperVoiceInputTheme
import java.io.File
import java.io.IOException


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
    progress = 0.5f,
    error = true
))

@Composable
fun ModelItem(model: ModelInfo = EXAMPLE_MODELS[0], showProgress: Boolean = false) {
    Column(modifier = Modifier.padding(4.dp)) {
        val rowModifier = if(model.error) {
            Modifier.background(MaterialTheme.colorScheme.errorContainer)
        } else {
            Modifier
        }

        Row(modifier = rowModifier.padding(4.dp)) {
            val size = if(model.size != null) {
                "%.1f".format(model.size!!.toFloat() / 1000000.0f)
            } else {
                "?"
            }
            Text(
                "${model.name}: $size MB",
            )
            if(model.error) {
                Text(" [Error]")
            }
        }

        if (showProgress) {
            LinearProgressIndicator(progress = model.progress, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
@Preview
fun DownloadPrompt(onContinue: () -> Unit = {}, onCancel: () -> Unit = {}, models: List<ModelInfo> = EXAMPLE_MODELS) {
    Column(modifier = Modifier.padding(8.dp)) {
        Text("To continue, one or more speech recognition model resources need to be downloaded. This may incur data fees if you're using mobile data instead of Wi-Fi")

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

        modelsToDownload.forEach {
            val request = Request.Builder().method("GET", null).url(it.url).build()

            httpClient.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    it.error = true
                    updateContent()
                }

                override fun onResponse(call: Call, response: Response) {
                    response.body?.source()?.let { source ->
                        val fileName = it.name + ".download"
                        val file = File.createTempFile(fileName, null, this@DownloadActivity.cacheDir)
                        val os = file.outputStream()

                        val buffer = ByteArray(128 * 1024)
                        var downloaded = 0
                        while (true) {
                            val read = source.read(buffer)
                            if (read == -1) { break }

                            os.write(buffer.sliceArray(0 until read))

                            downloaded += read

                            it.progress = downloaded.toFloat() / it.size!!.toFloat()

                            lifecycleScope.launch {
                                withContext(Dispatchers.Main) {
                                    updateContent()
                                }
                            }
                        }

                        it.finished = true
                        os.flush()
                        os.close()

                        assert(file.renameTo(File(this@DownloadActivity.filesDir, it.name)))

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
            val request = Request.Builder().method("HEAD", null).header("accept-encoding", "identity").url(it.url).build()

            httpClient.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    it.error = true
                    updateContent()
                }

                override fun onResponse(call: Call, response: Response) {
                    try {
                        it.size = response.headers["content-length"]!!.toLong()
                    } catch(e: Exception) {
                        println("url failed ${it.url}")
                        println(response.headers)
                        e.printStackTrace()
                        it.error = true
                    }

                    if(response.code != 200) {
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

        val models = intent.getStringArrayListExtra("models")
            ?: throw IllegalStateException("intent extra `models` must be specified for DownloadActivity")

        modelsToDownload = models.filter { this.fileNeedsDownloading(it) }.map {
            ModelInfo(
                name = it,
                url = "https://april.sapples.net/futo/${it}",
                size = null,
                progress = 0.0f
            )
        }

        if(modelsToDownload.isEmpty()) { cancel() }

        isDownloading = false
        updateContent()

        obtainModelSizes()
    }
}
