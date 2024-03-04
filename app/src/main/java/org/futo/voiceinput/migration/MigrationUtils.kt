package org.futo.voiceinput.migration

import android.content.Context
import kotlinx.coroutines.runBlocking
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.futo.voiceinput.ENGLISH_MODELS
import org.futo.voiceinput.MULTILINGUAL_MODELS
import org.futo.voiceinput.downloader.ModelInfo
import org.futo.voiceinput.fileNeedsDownloading
import org.futo.voiceinput.getLanguageModelMap
import org.futo.voiceinput.settings.MODELS_MIGRATED
import org.futo.voiceinput.settings.setSetting
import java.io.File
import java.io.IOException

fun getModelsToDownload(context: Context): List<ModelInfo> {
    val map = runBlocking { context.getLanguageModelMap() }

    val models = map.values.distinct()

    val modelsToDownload = models.filter { context.fileNeedsDownloading(it.ggml.ggml_file) && !it.ggml.is_builtin_asset }.map {
        ModelInfo(
            name = it.ggml.ggml_file,
            url = "https://voiceinput.futo.org/VoiceInput/${it.ggml.ggml_file}",
            size = null,
            progress = 0.0f
        )
    }

    return modelsToDownload
}

fun downloadModels(context: Context, modelsToDownload: List<ModelInfo>, httpClient: OkHttpClient, updateContent: () -> Unit, onFinish: () -> Unit) {
    modelsToDownload.forEach {
        val request = Request.Builder().method("GET", null).url(it.url).build()

        httpClient.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                it.error = true
                updateContent()
            }

            override fun onResponse(call: Call, response: Response) {
                val source = response.body?.source()
                if(source != null) {
                    try {
                        it.size = response.headers["content-length"]!!.toLong()
                    } catch (e: Exception) {
                        println("url failed ${it.url}")
                        println(response.headers)
                        e.printStackTrace()
                    }

                    val fileName = it.name + ".download"
                    val file = File.createTempFile(fileName, null, context.cacheDir)
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

                        if (it.size != null) {
                            it.progress = downloaded.toFloat() / it.size!!.toFloat()
                        }

                        updateContent()
                    }

                    it.finished = true
                    it.progress = 1.0f
                    os.flush()
                    os.close()

                    assert(file.renameTo(File(context.filesDir, it.name)))

                    if (modelsToDownload.all { a -> a.finished }) {
                        onFinish()
                    }
                    updateContent()
                } else {
                    it.error = true
                    updateContent()
                }
            }
        })
    }
}

fun deleteLegacyModels(context: Context) {
    val filesDir = context.filesDir

    val oldFiles = (ENGLISH_MODELS.map {
        if(!it.legacy.is_builtin_asset) {
            listOf(
                File(filesDir, it.legacy.encoder_xatn_file),
                File(filesDir, it.legacy.decoder_file),
                File(filesDir, it.legacy.vocab_file)
            )
        } else { listOf() }
    } + MULTILINGUAL_MODELS.map {
        if(!it.legacy.is_builtin_asset) {
            listOf(
                File(filesDir, it.legacy.encoder_xatn_file),
                File(filesDir, it.legacy.decoder_file),
                File(filesDir, it.legacy.vocab_file)
            )
        } else { listOf() }
    }).flatten().filter { it.exists() }.distinct()

    oldFiles.forEach { it.delete() }

    if(oldFiles.isNotEmpty()) {
        runBlocking {
            context.setSetting(MODELS_MIGRATED, true)
        }
    }
}