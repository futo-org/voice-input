package org.futo.voiceinput.ml

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.newSingleThreadContext
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import org.futo.voiceinput.ModelData
import org.futo.voiceinput.ggml.BailLanguageException
import org.futo.voiceinput.ggml.DecodingMode
import org.futo.voiceinput.ggml.WhisperGGML
import org.futo.voiceinput.migration.MigrationActivity
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel


/**
 * This is necessary to synchronize so two threads don't try to use the same tensor at once,
 * free a model while it's in use, etc.
 */
@OptIn(DelicateCoroutinesApi::class)
private val inferenceContext = newSingleThreadContext("InferenceContext")


@Throws(IOException::class)
private fun Context.tryOpenDownloadedModel(pathStr: String): MappedByteBuffer {
    return File(this.filesDir, pathStr).inputStream().use { fis ->
        fis.channel.use { channel ->
            channel.map(
                FileChannel.MapMode.READ_ONLY,
                0, channel.size()
            ).load()
        }
    }
}

enum class RunState {
    ExtractingFeatures,
    ProcessingEncoder,
    StartedDecoding,
    SwitchingModel,
    OOMError
}

@Throws(IOException::class)
private fun loadMappedFile(context: Context, filePath: String): MappedByteBuffer =
    context.assets.openFd(filePath).use { fileDescriptor ->
        FileInputStream(fileDescriptor.fileDescriptor).use { inputStream ->
            inputStream.channel.map(
                FileChannel.MapMode.READ_ONLY,
                fileDescriptor.startOffset,
                fileDescriptor.declaredLength
            )
        }
    }

@Throws(IOException::class)
fun loadGGMLModel(context: Context, model: ModelData, onPartialDecode: (String) -> Unit): WhisperGGML {
    val modelBuffer = if(model.ggml.is_builtin_asset) {
        loadMappedFile(context, model.ggml.ggml_file)
    } else {
        context.tryOpenDownloadedModel(model.ggml.ggml_file)
    }

    return WhisperGGML(modelBuffer, onPartialDecode)
}

private fun openMigrationIfModelIsLegacy(context: Context, model: ModelData) {
    if(listOf(
        model.legacy.encoder_xatn_file,
        model.legacy.decoder_file,
        model.legacy.vocab_file
    ).all { File(context.filesDir, it).exists() }) {
        // We are in the legacy model workflow, which is no longer supported
        // Immediately open the migration menu
        val intent = Intent(context, MigrationActivity::class.java)

        if(context !is Activity) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        context.startActivity(intent)
    }
}

class WhisperModelWrapper(
    val context: Context,
    primaryModel: ModelData,
    fallbackEnglishModel: ModelData?,
    private val suppressNonSpeech: Boolean,
    private val languages: Set<String>,

    private val onStatusUpdate: (RunState) -> Unit,
    private val onPartialDecode: (String) -> Unit,
) {
    private var primaryModelGGML: WhisperGGML? = null
    private var fallbackModelGGML: WhisperGGML? = null

    init {
        if(primaryModel == fallbackEnglishModel) {
            throw IllegalArgumentException("Fallback model must be unique from the primary model")
        }

        try {
            primaryModelGGML = loadGGMLModel(context, primaryModel, onPartialDecode)
        } catch(e: Exception) {
            runBlocking {
                primaryModelGGML?.close()
            }

            Log.e("WhisperModel", "Exception during loading primary ggml model: ${e.stackTraceToString()}")
            openMigrationIfModelIsLegacy(context, primaryModel)
            throw e
        }

        fallbackEnglishModel?.let { fallbackEnglishModel ->
            try {
                fallbackModelGGML = loadGGMLModel(context, fallbackEnglishModel, onPartialDecode)
            } catch(e: Exception) {
                runBlocking {
                    fallbackModelGGML?.close()
                }

                Log.e("WhisperModel", "Exception during loading fallback ggml model: ${e.stackTraceToString()}")
                openMigrationIfModelIsLegacy(context, fallbackEnglishModel)
                throw e
            }
        }
    }

    private var modelJob: Job? = null
    suspend fun run(
        samples: FloatArray,
        glossary: String,
        forceLanguage: String?,
        decodingMode: DecodingMode
    ): String {
        yield()

        // TODO: This only works well for English, it may cause weird behavior with other languages
        // (maybe need to translate "Glossary" per language, or language-neutral way of expressing)
        val glossaryCleaned = glossary.trim().replace("\n", ", ").replace("  ", " ")
        val prompt = if(glossary.isBlank()) "" else "(Glossary: ${glossaryCleaned})"

        val languagesOrLanguage = forceLanguage?.let { arrayOf(it) } ?: languages.toTypedArray()

        val bailLanguages = if(fallbackModelGGML != null) {
            arrayOf("en")
        } else {
            arrayOf()
        }

        if(primaryModelGGML != null) {
            // TODO: Early exiting from native code if cancelled
            return try {
                yield()
                onStatusUpdate(RunState.ProcessingEncoder)
                primaryModelGGML!!.infer(
                    samples,
                    prompt,
                    languagesOrLanguage,
                    bailLanguages,
                    decodingMode,
                    suppressNonSpeech
                )
            }catch(e: BailLanguageException) {
                yield()
                onStatusUpdate(RunState.SwitchingModel)
                assert(e.language == "en")

                if(fallbackModelGGML != null) {
                    fallbackModelGGML!!.infer(samples, prompt, languagesOrLanguage, arrayOf(), decodingMode, suppressNonSpeech)
                } else {
                    throw IllegalStateException("Fallback model null")
                }
            }
        } else {
            throw IllegalStateException("No models are loaded!")
        }
    }

    suspend fun close() = withContext(inferenceContext) {
        primaryModelGGML?.close()
        fallbackModelGGML?.close()
    }
}