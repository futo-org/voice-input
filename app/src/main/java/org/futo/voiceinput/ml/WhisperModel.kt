package org.futo.voiceinput.ml

import android.content.Context
import android.os.Build
import android.util.Log
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.newSingleThreadContext
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import org.futo.voiceinput.AudioFeatureExtraction
import org.futo.voiceinput.ModelData
import org.futo.voiceinput.toDoubleArray
import org.tensorflow.lite.DataType
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
    val fis = File(this.filesDir, pathStr).inputStream()
    val channel = fis.channel

    return channel.map(
        FileChannel.MapMode.READ_ONLY,
        0, channel.size()
    ).load()
}

private fun ByteArray.toSHA256String(): String {
    return BigInteger(1, this).toString(16).padStart(64, '0')
}

enum class RunState {
    ExtractingFeatures,
    ProcessingEncoder,
    StartedDecoding,
    SwitchingModel
}

data class LoadedModels(
    val encoderModel: WhisperEncoderXatn,
    val decoderModel: WhisperDecoder,
    val tokenizer: WhisperTokenizer,
    val taint: String
)

fun initModelsWithOptions(context: Context, model: ModelData, encoderOptions: Model.Options, decoderOptions: Model.Options): LoadedModels {
    return if(model.is_builtin_asset) {
        val encoderModel = WhisperEncoderXatn(context, model.encoder_xatn_file, encoderOptions)
        val decoderModel = WhisperDecoder(context, model.decoder_file, decoderOptions)
        val tokenizer = WhisperTokenizer(context, model.vocab_raw_asset!!)

        LoadedModels(encoderModel, decoderModel, tokenizer, "")
    } else {
        val encoderFile = context.tryOpenDownloadedModel(model.encoder_xatn_file)
        val decoderFile = context.tryOpenDownloadedModel(model.decoder_file)
        val vocabFile = File(context.filesDir, model.vocab_file)


        var taint = ""
        try {
            val digest = MessageDigest.getInstance("SHA-256")

            digest.update(encoderFile)
            val encoderDigest = digest.digest().toSHA256String()

            digest.update(decoderFile)
            val decoderDigest = digest.digest().toSHA256String()

            digest.update(vocabFile.readBytes())
            val vocabDigest = digest.digest().toSHA256String()

            if (encoderDigest != model.digests.encoder_digest) {
                taint += "Encoder digest mismatch for model ${model.name} - expected ${model.digests.encoder_digest}, got $encoderDigest\n"
            }

            if (decoderDigest != model.digests.decoder_digest) {
                taint += "Decoder digest mismatch for model ${model.name} - expected ${model.digests.decoder_digest}, got $decoderDigest\n"
            }

            if (vocabDigest != model.digests.vocab_digest) {
                taint += "Vocab digest mismatch for model ${model.name} - expected ${model.digests.vocab_digest}, got $vocabDigest\n"
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

        if(languages != null) {
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

    private fun runEncoderAndGetXatn(audioFeatures: TensorBuffer): TensorBuffer {
        if(taint.isNotBlank()) {
            Log.w("WhisperModel", "Running encoder on tainted instance. Taint = $taint")
        }

        return try {
            encoderModel.process(audioFeatures).crossAttention
        } catch(e: Exception) {
            if(taint.isNotBlank()) {
                throw TaintedModelException("Encoder, $taint. ${e.message}", e)
            } else {
                throw e
            }
        }
    }

    private fun runDecoder(
        xAtn: TensorBuffer,
        seqLen: TensorBuffer,
        cache: TensorBuffer,
        inputId: TensorBuffer
    ): WhisperDecoder.Outputs {
        if(taint.isNotBlank()) {
            Log.w("WhisperModel", "Running decoder on tainted instance. Taint = $taint")
        }

        return try {
             decoderModel.process(
                crossAttention = xAtn,
                seqLen = seqLen,
                cache = cache,
                inputIds = inputId
            )
        } catch(e: Exception) {
            if(taint.isNotBlank()) {
                throw TaintedModelException("Decoder, $taint. ${e.message}", e)
            } else {
                throw e
            }
        }
    }

    // TODO: Ideally this should be shared between model instances as well.
    private val cacheTensor =
        TensorBuffer.createFixedSize(decoderModel.getCacheTensorShape(), DataType.FLOAT32)

    init {
        val shape = cacheTensor.shape
        val size = shape[0] * shape[1] * shape[2] * shape[3]
        cacheTensor.loadArray(FloatArray(size) { 0f } )
    }

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
        val xAtn = runEncoderAndGetXatn(audioFeatures)

        yield()
        onStatusUpdate(RunState.StartedDecoding)

        val seqLenArray = FloatArray(1)
        val inputIdsArray = FloatArray(1)

        var fullString = ""
        var previousToken = decodeStartToken

        Log.i("WhisperModel", "Running decoder for model ${model.name}")

        for (seqLen in 0 until 256) {
            seqLenArray[0] = seqLen.toFloat()
            inputIdsArray[0] = previousToken.toFloat()

            yield()
            seqLenTensor.loadArray(seqLenArray)
            yield()
            inputIdTensor.loadArray(inputIdsArray)

            yield()
            val decoderOutputs = runDecoder(xAtn, seqLenTensor, cacheTensor, inputIdTensor)

            yield()
            cacheTensor.loadBuffer(decoderOutputs.nextCache.buffer.duplicate())

            val logits = decoderOutputs.logits.floatArray

            for(i in bannedTokens) logits[i] -= 1024.0f

            var selectedToken = logits.withIndex().maxByOrNull { it.value }?.index!!
            if(selectedToken == decodeEndToken) break

            val tokenAsString = tokenToString(selectedToken) ?: break

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
}


class WhisperModelWrapper(
    val context: Context,
    primaryModel: ModelData,
    fallbackEnglishModel: ModelData?,
    private val suppressNonSpeech: Boolean,
    languages: Set<String>? = null
) {
    private val primary: WhisperModel = WhisperModel(context, primaryModel, suppressNonSpeech, languages)
    private val fallback: WhisperModel? = fallbackEnglishModel?.let { WhisperModel(context, it, suppressNonSpeech) }

    init {
        if(primaryModel == fallbackEnglishModel) {
            throw IllegalArgumentException("Fallback model must be unique from the primary model")
        }
    }

    suspend fun run(
        samples: FloatArray,
        onStatusUpdate: (RunState) -> Unit,
        onPartialDecode: (String) -> Unit,
        forceLanguage: String?
    ): String {
        yield()
        onStatusUpdate(RunState.ExtractingFeatures)
        val mel = WhisperModel.extractor.melSpectrogram(samples.toDoubleArray())

        return try {
            yield()
            primary.run(mel, onStatusUpdate, onPartialDecode, fallback != null, forceLanguage)
        } catch(e: DecodingEnglishException) {
            yield()
            fallback!!.run(
                mel,
                {
                    if(it != RunState.ProcessingEncoder) {
                        onStatusUpdate(it)
                    }
                },
                onPartialDecode,
                false,
                null
            )
        }
    }
}