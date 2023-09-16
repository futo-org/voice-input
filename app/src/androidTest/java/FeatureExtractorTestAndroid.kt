
import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import org.futo.voiceinput.ml.WhisperModel
import org.futo.voiceinput.toDoubleArray
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs

private fun loadResourceFile(context: Context, file: String): ByteArray {
    val stream = context.resources.assets.open(file)
    val bytes = stream.readBytes()
    stream.close()
    return bytes
}

private fun ByteArray.littleEndianToFloatArray(): FloatArray {
    val numElements = this.size / 4
    val byteBuffer = ByteBuffer.wrap(this).order(ByteOrder.LITTLE_ENDIAN)
    val outArray = FloatArray(numElements)

    for(i in 0 until numElements) {
        outArray[i] = byteBuffer.float
    }

    return outArray
}

fun Double.isEqualApprox(other: Double): Boolean {
    return abs(this - other) < 1.0e-5
}

fun Array<Double>.isEqualApprox(other: Array<Double>): Boolean {
    if(this.size != other.size) return false
    for(i in indices) {
        if(!this[i].isEqualApprox(other[i])) return false
    }

    return true
}

class FeatureExtractorTestAndroid {
    // This needs to run on Android, because the native library cannot be loaded on standard Linux
    @Test fun featureExtractor_CompareResultWithHFTransformers() {
        val context: Context = InstrumentationRegistry.getInstrumentation().context

        val audio = loadResourceFile(context, "audio.floats.bin").littleEndianToFloatArray()

        val extractor = WhisperModel.extractor

        val extractedFeatures = extractor.melSpectrogram(audio.toDoubleArray())
        val targetFeatures = loadResourceFile(context, "features.floats.bin").littleEndianToFloatArray()

        assertTrue(extractedFeatures.toDoubleArray().toTypedArray().isEqualApprox(targetFeatures.toDoubleArray().toTypedArray()))
    }

    // Must finish without throwing an exception
    @Test fun featureExtractor_CanHandleEmpty() {
        val extractor = WhisperModel.extractor

        extractor.melSpectrogram(doubleArrayOf())
    }
}