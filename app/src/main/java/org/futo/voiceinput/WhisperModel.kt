package org.futo.voiceinput

import android.content.Context
import org.tensorflow.lite.DataType
import org.tensorflow.lite.support.metadata.MetadataExtractor
import org.tensorflow.lite.support.model.Model
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer
import java.io.IOException
import java.nio.MappedByteBuffer

/**
 * This model doesn't have metadata, so no javadoc can be generated.  */
class WhisperModel {
    private val model: Model

    constructor(context: Context, modelPath: String = "whisper.tflite", options: Model.Options = Model.Options.Builder().build()) {
        model = Model.createModel(context, modelPath, options)
    }

    constructor(modelBuffer: MappedByteBuffer, modelPath: String = "whisper.tflite", options: Model.Options = Model.Options.Builder().build()) {
        model = Model.createModel(modelBuffer, modelPath, options)
    }

    fun process(inputFeature0: TensorBuffer): Outputs {
        val outputs = Outputs(model)
        model.run(arrayOf<Any>(inputFeature0.buffer), outputs.buffer)
        return outputs
    }

    fun close() {
        model.close()
    }

    inner class Outputs internal constructor(model: Model) {
        val outputFeature0AsTensorBuffer: TensorBuffer

        init {
            outputFeature0AsTensorBuffer =
                TensorBuffer.createFixedSize(model.getOutputTensorShape(0), DataType.FLOAT32)
        }

        internal val buffer: Map<Int, Any>
            internal get() {
                val outputs: MutableMap<Int, Any> = HashMap()
                outputs[0] = outputFeature0AsTensorBuffer.buffer
                return outputs
            }
    }
}