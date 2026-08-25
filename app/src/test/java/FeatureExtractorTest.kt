
import org.futo.voiceinput.transpose
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs

private fun loadResourceFile(loader: ClassLoader, file: String): ByteArray {
    return File(loader.getResource(file).file).inputStream().use {
        it.readBytes()
    }
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

private fun ByteArray.littleEndianToDoubleArray(): DoubleArray {
    val numElements = this.size / 8
    val byteBuffer = ByteBuffer.wrap(this).order(ByteOrder.LITTLE_ENDIAN)
    val outArray = DoubleArray(numElements)

    for(i in 0 until numElements) {
        outArray[i] = byteBuffer.double
    }

    return outArray
}

fun Double.isEqualApprox(other: Double): Boolean {
    return abs(this - other) < 1.0e-5
}

fun Float.isEqualApprox(other: Float): Boolean {
    return abs(this - other) < 1.0e-5
}

fun Array<Double>.isEqualApprox(other: Array<Double>): Boolean {
    if(this.size != other.size) return false
    for(i in indices) {
        if(!this[i].isEqualApprox(other[i])) return false
    }

    return true
}

fun Array<Array<Double>>.isEqualApprox(other: Array<Array<Double>>): Boolean {
    if(this.size != other.size) return false
    for(i in indices) {
        if(!this[i].isEqualApprox(other[i])) return false
    }

    return true
}

fun Array<DoubleArray>.isEqualApprox(other: Array<Array<Double>>): Boolean {
    if(this.size != other.size) return false
    for(i in indices) {
        if(!this[i].toTypedArray().isEqualApprox(other[i])) return false
    }

    return true
}

fun Array<DoubleArray>.isEqualApprox(other: Array<DoubleArray>): Boolean {
    if(this.size != other.size) return false
    for(i in indices) {
        if(!this[i].toTypedArray().isEqualApprox(other[i].toTypedArray())) return false
    }

    return true
}

fun Array<Array<Double>>.as2DDoubleArray(): Array<DoubleArray> {
    return map {
        it.toDoubleArray()
    }.toTypedArray()
}

class FeatureExtractorTest {
    @Test fun dummy() {}
}