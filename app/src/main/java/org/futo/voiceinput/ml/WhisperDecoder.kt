package org.futo.voiceinput.ml

import android.content.Context
import org.tensorflow.lite.support.model.Model
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer
import java.nio.MappedByteBuffer

/**
 * This model doesn't have metadata, so no javadoc can be generated.  */
class WhisperDecoder {
    private val model: Model

    constructor(context: Context, modelPath: String = "tiny-en-decoder.tflite", options: Model.Options = Model.Options.Builder().build()) {
        model = Model.createModel(context, modelPath, options)
    }

    constructor(modelBuffer: MappedByteBuffer, options: Model.Options = Model.Options.Builder().build()) {
        model = Model.createModel(modelBuffer, "", options)
    }


    fun process(
        crossAttention: TensorBuffer, seqLen: TensorBuffer,
        cache: TensorBuffer, inputIds: TensorBuffer,
        outputs: Outputs
    ): Outputs {
        model.run(
            arrayOf<Any>(crossAttention.buffer, seqLen.buffer, cache.buffer, inputIds.buffer),
            outputs.buffer
        )
        return outputs
    }

    fun close() {
        model.close()
    }

    fun getLogitsTensorShape(): IntArray {
        return model.getOutputTensorShape(0)
    }

    fun getCacheTensorShape(): IntArray {
        return model.getOutputTensorShape(1)
    }

    data class Outputs(val logits: TensorBuffer, val nextCache: TensorBuffer) {
        internal val buffer: Map<Int, Any>
            get() {
                val outputs: MutableMap<Int, Any> = HashMap()
                outputs[0] = logits.buffer
                outputs[1] = nextCache.buffer
                return outputs
            }
    }
}