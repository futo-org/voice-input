package org.futo.voiceinput.ml

import android.content.Context
import android.os.Build
import org.futo.voiceinput.ModelData
import org.tensorflow.lite.DataType
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer
import java.io.File
import java.io.IOException
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

@Throws(IOException::class)
private fun Context.tryOpenDownloadedModel(pathStr: String): MappedByteBuffer {
    val path = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        File(this.filesDir, pathStr).toPath()
    } else {
        TODO("VERSION.SDK_INT < O")
    }

    val channel = FileChannel.open(path)

    val mappedByteBuffer = channel.map(
        FileChannel.MapMode.READ_ONLY,
        0, channel.size()
    ).load()

    return mappedByteBuffer
}

class WhisperModel(context: Context, model: ModelData) {
    val encoderModel: WhisperEncoderXatn
    val decoderModel: WhisperDecoder
    val tokenizer: WhisperTokenizer

    val decodeStartToken: Int
    val decodeEndToken: Int
    val translateToken: Int
    val noCaptionsToken: Int

    val startOfLanguages: Int
    val endOfLanguages: Int

    init {
        if(model.is_builtin_asset) {
            encoderModel = WhisperEncoderXatn(context, model.encoder_xatn_file)
            decoderModel = WhisperDecoder(context, model.decoder_file)
            tokenizer = WhisperTokenizer(context, model.vocab_raw_asset!!)
        } else {
            encoderModel = WhisperEncoderXatn(context.tryOpenDownloadedModel(model.encoder_xatn_file))
            decoderModel = WhisperDecoder(context.tryOpenDownloadedModel(model.decoder_file))
            tokenizer = WhisperTokenizer(File(context.filesDir, model.vocab_file))
        }

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

    fun run(
        mel: FloatArray,
        onPartialDecode: (String) -> Unit
    ): String {
        // TODO: Fall back to English model if English is detected

        val audioFeatures = TensorBuffer.createFixedSize(intArrayOf(1, 80, 3000), DataType.FLOAT32)
        audioFeatures.loadArray(mel)

        val xAtn = runEncoderAndGetXatn(audioFeatures)

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
            logits[translateToken] -= 1024.0f;
            logits[noCaptionsToken] -= 1024.0f;

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