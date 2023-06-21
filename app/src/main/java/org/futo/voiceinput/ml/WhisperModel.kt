package org.futo.voiceinput.ml

import android.content.Context
import android.os.Build
import org.futo.voiceinput.AudioFeatureExtraction
import org.futo.voiceinput.ModelData
import org.futo.voiceinput.toDoubleArray
import org.tensorflow.lite.DataType
import org.tensorflow.lite.support.model.Model
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer
import java.io.File
import java.io.IOException
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel


@Throws(IOException::class)
private fun Context.tryOpenDownloadedModel(pathStr: String): MappedByteBuffer {
    val fis = File(this.filesDir, pathStr).inputStream()
    val channel = fis.channel

    return channel.map(
        FileChannel.MapMode.READ_ONLY,
        0, channel.size()
    ).load()
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
    val tokenizer: WhisperTokenizer
)

fun initModelsWithOptions(context: Context, model: ModelData, encoderOptions: Model.Options, decoderOptions: Model.Options): LoadedModels {
    return if(model.is_builtin_asset) {
        val encoderModel = WhisperEncoderXatn(context, model.encoder_xatn_file, encoderOptions)
        val decoderModel = WhisperDecoder(context, model.decoder_file, decoderOptions)
        val tokenizer = WhisperTokenizer(context, model.vocab_raw_asset!!)

        LoadedModels(encoderModel, decoderModel, tokenizer)
    } else {
        val encoderModel = WhisperEncoderXatn(context.tryOpenDownloadedModel(model.encoder_xatn_file), encoderOptions)
        val decoderModel = WhisperDecoder(context.tryOpenDownloadedModel(model.decoder_file), decoderOptions)
        val tokenizer = WhisperTokenizer(File(context.filesDir, model.vocab_file))

        LoadedModels(encoderModel, decoderModel, tokenizer)
    }
}

class WhisperModel(context: Context, model: ModelData) {
    private val encoderModel: WhisperEncoderXatn
    private val decoderModel: WhisperDecoder
    private val tokenizer: WhisperTokenizer

    private val decodeStartToken: Int
    private val decodeEndToken: Int
    private val translateToken: Int
    private val noCaptionsToken: Int

    private val startOfLanguages: Int
    private val endOfLanguages: Int

    companion object {
        val extractor = AudioFeatureExtraction(
            chunkLength = 30,
            featureSize = 80,
            hopLength = 160,
            nFFT = 400,
            paddingValue = 0.0,
            samplingRate = 16000
        )
    }

    init {
        val cpuOption = Model.Options.Builder().setDevice(Model.Device.CPU).build()

        val nnApiOption = if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            Model.Options.Builder().setDevice(Model.Device.NNAPI).build()
        } else {
            cpuOption
        }

        val (encoderModel, decoderModel, tokenizer) = try {
            initModelsWithOptions(context, model, nnApiOption, cpuOption)
        } catch (e: Exception) {
            e.printStackTrace()
            initModelsWithOptions(context, model, cpuOption, cpuOption)
        }

        this.encoderModel = encoderModel
        this.decoderModel = decoderModel
        this.tokenizer = tokenizer


        decodeStartToken = stringToToken("<|startoftranscript|>")!!
        decodeEndToken = stringToToken("<|endoftext|>")!!
        translateToken = stringToToken("<|translate|>")!!
        noCaptionsToken = stringToToken("<|nocaptions|>")!!

        startOfLanguages = stringToToken("<|en|>")!!
        endOfLanguages = stringToToken("<|su|>")!!
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
        return encoderModel.process(audioFeatures).crossAttention
    }

    private fun runDecoder(
        xAtn: TensorBuffer,
        seqLen: TensorBuffer,
        cache: TensorBuffer,
        inputId: TensorBuffer
    ): WhisperDecoder.Outputs {
        return decoderModel.process(crossAttention = xAtn, seqLen = seqLen, cache = cache, inputIds = inputId)
    }

    // TODO: Fall back to English model if English is detected
    fun run(
        samples: FloatArray,
        onStatusUpdate: (RunState) -> Unit,
        onPartialDecode: (String) -> Unit
    ): String {
        onStatusUpdate(RunState.ExtractingFeatures)
        val mel = extractor.melSpectrogram(samples.toDoubleArray())

        onStatusUpdate(RunState.ProcessingEncoder)
        val audioFeatures = TensorBuffer.createFixedSize(intArrayOf(1, 80, 3000), DataType.FLOAT32)
        audioFeatures.loadArray(mel)

        val xAtn = runEncoderAndGetXatn(audioFeatures)

        onStatusUpdate(RunState.StartedDecoding)
        val seqLenTensor = TensorBuffer.createFixedSize(intArrayOf(1), DataType.FLOAT32)
        val cacheTensor = TensorBuffer.createFixedSize(intArrayOf(8, 6, 256, 64), DataType.FLOAT32)
        val inputIdTensor = TensorBuffer.createFixedSize(intArrayOf(1, 1), DataType.FLOAT32)

        cacheTensor.loadArray(FloatArray(8 * 6 * 256 * 64) { 0f } )

        var fullString = ""
        var previousToken = decodeStartToken
        for (seqLen in 0 until 256) {
            val seqLenArray = FloatArray(1)
            seqLenArray[0] = seqLen.toFloat()

            val inputIdsArray = FloatArray(1)
            inputIdsArray[0] = previousToken.toFloat()

            seqLenTensor.loadArray(seqLenArray)
            inputIdTensor.loadArray(inputIdsArray)

            val decoderOutputs = runDecoder(xAtn, seqLenTensor, cacheTensor, inputIdTensor)
            cacheTensor.loadBuffer(decoderOutputs.nextCache.buffer.duplicate())

            val logits = decoderOutputs.logits.floatArray

            // Forcibly kill undesired tokens
            logits[translateToken] -= 1024.0f
            logits[noCaptionsToken] -= 1024.0f

            val selectedToken = logits.withIndex().maxByOrNull { it.value }?.index!!
            if(selectedToken == decodeEndToken) { break; }

            if((selectedToken >= startOfLanguages) && (selectedToken <= endOfLanguages)){
                println("Language detected: ${tokenToString(selectedToken)!!}")
            }

            fullString += tokenToString(selectedToken)!!.run {
                if (this.startsWith("<|")) {
                    ""
                } else {
                    this
                }
            }

            previousToken = selectedToken

            onPartialDecode(makeStringUnicode(fullString))
        }

        return makeStringUnicode(fullString)
    }
}