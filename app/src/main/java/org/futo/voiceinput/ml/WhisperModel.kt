package org.futo.voiceinput.ml

import android.content.Context
import android.util.Log
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.newSingleThreadContext
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import org.futo.voiceinput.AudioFeatureExtraction
import org.futo.voiceinput.ModelData
import org.futo.voiceinput.PromptingStyle
import org.futo.voiceinput.ggml.BailLanguageException
import org.futo.voiceinput.ggml.DecodingMode
import org.futo.voiceinput.ggml.WhisperGGML
import org.futo.voiceinput.toDoubleArray
import org.tensorflow.lite.DataType
import org.tensorflow.lite.support.common.FileUtil
import org.tensorflow.lite.support.model.Model
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer
import java.io.File
import java.io.IOException
import java.math.BigInteger
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import java.security.MessageDigest


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

private fun ByteArray.toSHA256String(): String {
    return BigInteger(1, this).toString(16).padStart(64, '0')
}

enum class RunState {
    ExtractingFeatures,
    ProcessingEncoder,
    StartedDecoding,
    SwitchingModel,
    OOMError
}

data class LoadedModels(
    val encoderModel: WhisperEncoderXatn,
    val decoderModel: WhisperDecoder,
    val tokenizer: WhisperTokenizer,
    val taint: String
)

fun initModelsWithOptions(context: Context, model: ModelData, encoderOptions: Model.Options, decoderOptions: Model.Options): LoadedModels {
    return if(model.legacy.is_builtin_asset) {
        val encoderModel = WhisperEncoderXatn(context, model.legacy.encoder_xatn_file, encoderOptions)
        val decoderModel = WhisperDecoder(context, model.legacy.decoder_file, decoderOptions)
        val tokenizer = WhisperTokenizer(context, model.legacy.vocab_raw_asset!!)

        LoadedModels(encoderModel, decoderModel, tokenizer, "")
    } else {
        val encoderFile = context.tryOpenDownloadedModel(model.legacy.encoder_xatn_file)
        val decoderFile = context.tryOpenDownloadedModel(model.legacy.decoder_file)
        val vocabFile = File(context.filesDir, model.legacy.vocab_file)


        var taint = ""
        try {
            val digest = MessageDigest.getInstance("SHA-256")

            digest.update(encoderFile)
            val encoderDigest = digest.digest().toSHA256String()

            digest.update(decoderFile)
            val decoderDigest = digest.digest().toSHA256String()

            digest.update(vocabFile.readBytes())
            val vocabDigest = digest.digest().toSHA256String()

            if (encoderDigest != model.legacy.digests.encoder_digest) {
                taint += "Encoder digest mismatch for model ${model.name} - expected ${model.legacy.digests.encoder_digest}, got $encoderDigest\n"
            }

            if (decoderDigest != model.legacy.digests.decoder_digest) {
                taint += "Decoder digest mismatch for model ${model.name} - expected ${model.legacy.digests.decoder_digest}, got $decoderDigest\n"
            }

            if (vocabDigest != model.legacy.digests.vocab_digest) {
                taint += "Vocab digest mismatch for model ${model.name} - expected ${model.legacy.digests.vocab_digest}, got $vocabDigest\n"
            }
        } catch (e: Exception) {
            Log.e("WhisperModel", "Failed to verify digests due to exception $e")
            e.printStackTrace()

            taint += "Failed to verify digests due to exception $e"
        }

        val encoderModel = WhisperEncoderXatn(encoderFile, encoderOptions)
        val decoderModel = WhisperDecoder(decoderFile, decoderOptions)
        val tokenizer = WhisperTokenizer(vocabFile)

        LoadedModels(encoderModel, decoderModel, tokenizer, taint)
    }
}

class TaintedModelException(message: String, cause: Exception) : Exception(message, cause)

class DecodingEnglishException : Throwable()


class WhisperModel(context: Context, private val model: ModelData, private val suppressNonSpeech: Boolean, languages: Set<String>? = null) {
    private val encoderModel: WhisperEncoderXatn
    private val decoderModel: WhisperDecoder
    private val tokenizer: WhisperTokenizer
    private val taint: String

    private val bannedTokens: IntArray
    private val decodeStartToken: Int
    private val decodeEndToken: Int
    private val translateToken: Int
    private val noCaptionsToken: Int

    private val startOfLanguages: Int
    private val englishLanguage: Int
    private val endOfLanguages: Int

    companion object {
        private val audioFeatures =
            TensorBuffer.createFixedSize(intArrayOf(1, 80, 3000), DataType.FLOAT32)
        private val seqLenTensor = TensorBuffer.createFixedSize(intArrayOf(1), DataType.FLOAT32)
        private val inputIdTensor = TensorBuffer.createFixedSize(intArrayOf(1, 1), DataType.FLOAT32)

        private val seqLenArray = FloatArray(1)
        private val inputIdsArray = FloatArray(1)

        val extractor = AudioFeatureExtraction(
            chunkLength = 30,
            featureSize = 80,
            hopLength = 160,
            nFFT = 400,
            paddingValue = 0.0,
            samplingRate = 16000
        )

        private val emptyResults: Set<String>
        init {
            val emptyResults = mutableListOf(
                "you",
                "(bell dings)",
                "(blank audio)",
                "(beep)",
                "(bell)",
                "(music)",
                "(music playing)"
            )

            emptyResults += emptyResults.map { it.replace("(", "[").replace(")", "]") }
            emptyResults += emptyResults.map { it.replace(" ", "_") }

            this.emptyResults = emptyResults.toHashSet()
        }
    }

    init {
        val cpuOption = Model.Options.Builder().setDevice(Model.Device.CPU).build()

        val (encoderModel, decoderModel, tokenizer, taint) = try {
            initModelsWithOptions(context, model, cpuOption, cpuOption)
        } catch (e: Exception) {
            e.printStackTrace()
            initModelsWithOptions(context, model, cpuOption, cpuOption)
        }

        this.encoderModel = encoderModel
        this.decoderModel = decoderModel
        this.tokenizer = tokenizer
        this.taint = taint

        decodeStartToken = stringToToken("<|startoftranscript|>")!!
        decodeEndToken = stringToToken("<|endoftext|>")!!
        translateToken = stringToToken("<|translate|>")!!
        noCaptionsToken = stringToToken("<|nocaptions|>")!!

        startOfLanguages = stringToToken("<|en|>")!!
        englishLanguage = stringToToken("<|en|>")!!
        endOfLanguages = stringToToken("<|su|>")!!

        // Based on https://github.com/openai/whisper/blob/248b6cb124225dd263bb9bd32d060b6517e067f8/whisper/tokenizer.py#L236
        val symbols = "#()*+/:;<=>@[\\]^_`{|}~「」『』".chunked(1) + listOf("<<", ">>", "<<<", ">>>", "--", "---", "-(", "-[", "('", "(\"", "((", "))", "(((", ")))", "[[", "]]", "{{", "}}", "♪♪", "♪♪♪")

        val symbolsWithSpace = symbols.map { " $it" } + listOf(" -", " '")

        val miscellaneous = "♩♪♫♬♭♮♯".toSet()

        val isBannedChar = { token: String ->
            if(suppressNonSpeech) {
                val normalizedToken = makeStringUnicode(token)
                symbols.contains(normalizedToken) || symbolsWithSpace.contains(normalizedToken)
                        || normalizedToken.toSet().intersect(miscellaneous).isNotEmpty()
            } else {
                false
            }
        }

        var bannedTokens = tokenizer.tokenToId.filterKeys { isBannedChar(it) }.values.toIntArray()
        bannedTokens += listOf(translateToken, noCaptionsToken)

        if(model.legacy.promptingStyle == PromptingStyle.LanguageTokenAndAction && languages != null) {
            val permittedLanguages = languages.map {
                stringToToken("<|$it|>")!!
            }.toHashSet()

            // Ban other languages
            bannedTokens += tokenizer.tokenToId.filterValues {
                (it >= startOfLanguages) && (it <= endOfLanguages) && (!permittedLanguages.contains(it))
            }.values.toIntArray()
        }

        this.bannedTokens = bannedTokens
    }

    private fun stringToToken(string: String): Int? {
        return tokenizer.stringToToken(string)
    }

    private fun tokenToString(token: Int): String? {
        return tokenizer.tokenToString(token)
    }

    private fun makeStringUnicode(string: String): String {
        return tokenizer.makeStringUnicode(string).trim()
    }

    private fun runEncoderAndGetXatn(): TensorBuffer {
        if(taint.isNotBlank()) {
            Log.w("WhisperModel", "Running encoder on tainted instance. Taint = $taint")
        }

        return try {
            encoderModel.process(audioFeatures, outputs=encoderOutputs).crossAttention
        } catch(e: Exception) {
            if(taint.isNotBlank()) {
                throw TaintedModelException("Encoder, $taint. ${e.message}", e)
            } else {
                throw e
            }
        }
    }

    private fun runDecoder(
        cache: TensorBuffer,
        decoderOutputs: WhisperDecoder.Outputs
    ): WhisperDecoder.Outputs {
        if(taint.isNotBlank()) {
            Log.w("WhisperModel", "Running decoder on tainted instance. Taint = $taint")
        }

        return try {
             decoderModel.process(
                 crossAttention = xAtnTensor,
                 seqLen = seqLenTensor,
                 cache = cache,
                 inputIds = inputIdTensor,
                 outputs = decoderOutputs
            )
        } catch(e: Exception) {
            if(taint.isNotBlank()) {
                throw TaintedModelException("Decoder, $taint. ${e.message}", e)
            } else {
                throw e
            }
        }
    }

    // TODO: Ideally these should be shared between model instances as well.
    private val logitsTensor =
        TensorBuffer.createFixedSize(decoderModel.getLogitsTensorShape(), DataType.FLOAT32)
    private val xAtnTensor =
        TensorBuffer.createFixedSize(encoderModel.getXatnShape(), DataType.FLOAT32)
    private val encoderOutputs = WhisperEncoderXatn.Outputs(xAtnTensor)


    private val cacheTensor0 =
        TensorBuffer.createFixedSize(decoderModel.getCacheTensorShape(), DataType.FLOAT32)
    private val cacheTensor1 =
        TensorBuffer.createFixedSize(decoderModel.getCacheTensorShape(), DataType.FLOAT32)

    private val decoderOutputs0 = WhisperDecoder.Outputs(
        logitsTensor, cacheTensor1
    )
    private val decoderOutputs1 = WhisperDecoder.Outputs(
        logitsTensor, cacheTensor0
    )


    suspend fun run(
        mel: FloatArray,
        onStatusUpdate: (RunState) -> Unit,
        onPartialDecode: (String) -> Unit,
        bailOnEnglish: Boolean,
        forceLanguage: String?
    ): String = withContext(inferenceContext) {
        yield()
        onStatusUpdate(RunState.ProcessingEncoder)

        yield()
        audioFeatures.loadArray(mel)

        Log.i("WhisperModel", "Running encoder for model ${model.name}")

        yield()
        runEncoderAndGetXatn()

        yield()
        onStatusUpdate(RunState.StartedDecoding)

        var fullString = ""
        var previousToken = decodeStartToken

        Log.i("WhisperModel", "Running decoder for model ${model.name}")

        // Empty the cache
        run {
            val shape = cacheTensor0.shape
            val size = shape[0] * shape[1] * shape[2] * shape[3]

            val arr = FloatArray(size) { 0f }
            cacheTensor0.loadArray(arr)
            cacheTensor1.loadArray(arr)
        }

        for (seqLen in 0 until 256) {
            seqLenArray[0] = seqLen.toFloat()
            inputIdsArray[0] = previousToken.toFloat()

            yield()
            seqLenTensor.loadArray(seqLenArray)
            yield()
            inputIdTensor.loadArray(inputIdsArray)

            yield()

            val cacheTensor = if(seqLen % 2 == 0) cacheTensor0 else cacheTensor1
            val decoderOutputs = if(seqLen % 2 == 0) decoderOutputs0 else decoderOutputs1

            runDecoder(cacheTensor, decoderOutputs)

            yield()

            cacheTensor.buffer.rewind()

            val logits = decoderOutputs.logits.floatArray

            for(i in bannedTokens) logits[i] = Float.NEGATIVE_INFINITY

            if(decoderOutputs.logits.floatArray.size > tokenizer.numTokens) {
                for (i in tokenizer.numTokens until decoderOutputs.logits.floatArray.size) logits[i] =
                    Float.NEGATIVE_INFINITY
            }

            var selectedToken = logits.withIndex().maxByOrNull { it.value }?.index!!
            if(selectedToken == decodeEndToken) break

            val tokenAsString = tokenToString(selectedToken) ?: run {
                Log.e("WhisperModel", "Encountered a token with no string conversion $selectedToken!")
                ""
            }

            if((selectedToken >= startOfLanguages) && (selectedToken <= endOfLanguages)){
                println("Language detected: $tokenAsString")

                if(forceLanguage != null) {
                    val permittedLanguage = stringToToken("<|$forceLanguage|>")!!
                    println("Overriding language with $forceLanguage")
                    selectedToken = permittedLanguage
                }else if((selectedToken == englishLanguage) && bailOnEnglish) {
                    yield()
                    onStatusUpdate(RunState.SwitchingModel)
                    throw DecodingEnglishException()
                }
            }

            fullString += tokenAsString.run {
                if (this.startsWith("<|")) {
                    ""
                } else {
                    this
                }
            }

            previousToken = selectedToken

            yield()
            if(fullString.isNotEmpty())
                onPartialDecode(makeStringUnicode(fullString))
        }


        val fullStringNormalized = makeStringUnicode(fullString).lowercase().trim()

        if(emptyResults.contains(fullStringNormalized)) {
            fullString = ""
        }

        yield()
        return@withContext makeStringUnicode(fullString)
    }

    fun close() {
        encoderModel.close()
        decoderModel.close()
    }
}


fun loadGGMLModel(context: Context, model: ModelData, onPartialDecode: (String) -> Unit): WhisperGGML {
    val modelBuffer = if(model.ggml.is_builtin_asset) {
        FileUtil.loadMappedFile(context, model.ggml.ggml_file)
    } else {
        context.tryOpenDownloadedModel(model.ggml.ggml_file)
    }

    return WhisperGGML(modelBuffer, onPartialDecode)
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
    private var primaryModelLegacy: WhisperModel? = null
    private var fallbackModelLegacy: WhisperModel? = null

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

            when(e) {
                is IOException, is IllegalArgumentException -> {
                    Log.e("WhisperModel", "Exception during loading primary ggml model: ${e.stackTraceToString()}")
                    primaryModelLegacy = WhisperModel(context, primaryModel, suppressNonSpeech, languages)
                }
                else -> throw e
            }
        }

        fallbackEnglishModel?.let { fallbackEnglishModel ->
            try {
                fallbackModelGGML = loadGGMLModel(context, fallbackEnglishModel, onPartialDecode)
            } catch(e: Exception) {
                runBlocking {
                    fallbackModelGGML?.close()
                }

                when(e) {
                    is IOException, is IllegalArgumentException -> {
                        Log.e("WhisperModel", "Exception during loading fallback ggml model: ${e.stackTraceToString()}")
                        fallbackModelLegacy = WhisperModel(context, fallbackEnglishModel, suppressNonSpeech, languages)
                    }
                    else -> throw e
                }
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

        val bailLanguages = if(fallbackModelGGML != null || fallbackModelLegacy != null) {
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
                    val mel = WhisperModel.extractor.melSpectrogram(samples.toDoubleArray())
                    fallbackModelLegacy!!.run(
                        mel,
                        {
                            if (it != RunState.ProcessingEncoder) {
                                onStatusUpdate(it)
                            }
                        },
                        onPartialDecode,
                        false,
                        null
                    )
                }
            }
        }else if(primaryModelLegacy != null) {
            onStatusUpdate(RunState.ExtractingFeatures)
            val mel = WhisperModel.extractor.melSpectrogram(samples.toDoubleArray())

            return try {
                yield()
                primaryModelLegacy!!.run(mel, onStatusUpdate, onPartialDecode, (fallbackModelLegacy != null) || (fallbackModelGGML != null), forceLanguage)
            } catch (e: DecodingEnglishException) {
                yield()
                if(fallbackModelGGML != null) {
                    fallbackModelGGML!!.infer(samples, prompt, languagesOrLanguage, arrayOf(), decodingMode, suppressNonSpeech)
                } else {
                    fallbackModelLegacy!!.run(
                        mel,
                        {
                            if (it != RunState.ProcessingEncoder) {
                                onStatusUpdate(it)
                            }
                        },
                        onPartialDecode,
                        false,
                        null
                    )
                }
            }
        } else {
            throw IllegalStateException("No models are loaded!")
        }
    }

    suspend fun close() = withContext(inferenceContext) {
        primaryModelGGML?.close()
        fallbackModelGGML?.close()
        primaryModelLegacy?.close()
        fallbackModelLegacy?.close()
    }
}