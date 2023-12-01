package org.futo.voiceinput.ml

import android.content.Context
import org.tensorflow.lite.support.model.Model
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer
import java.nio.MappedByteBuffer

/**
 * This model doesn't have metadata, so no javadoc can be generated.  */
class WhisperEncoderXatn {
    private val model: Model

    constructor(context: Context, modelPath: String = "tiny-en-encoder-xatn.tflite", options: Model.Options = Model.Options.Builder().build()) {
        model = Model.createModel(context, modelPath, options)
    }

    constructor(modelBuffer: MappedByteBuffer, options: Model.Options = Model.Options.Builder().build()) {
        model = Model.createModel(modelBuffer, "", options)
    }


    fun process(audioFeatures: TensorBuffer, outputs: Outputs): Outputs {
        model.run(arrayOf<Any>(audioFeatures.buffer), outputs.buffer)
        return outputs
    }

    fun close() {
        model.close()
    }

    fun getXatnShape(): IntArray {
        return model.getOutputTensorShape(0)
    }

    data class Outputs(val crossAttention: TensorBuffer) {
        internal val buffer: Map<Int, Any>
            get() {
                val outputs: MutableMap<Int, Any> = HashMap()
                outputs[0] = crossAttention.buffer
                return outputs
            }
    }
}