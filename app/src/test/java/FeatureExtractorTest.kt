
import org.futo.voiceinput.MelScale
import org.futo.voiceinput.Normalization
import org.futo.voiceinput.createHannWindow
import org.futo.voiceinput.createTriangularFilterBank
import org.futo.voiceinput.diff
import org.futo.voiceinput.freqToMel
import org.futo.voiceinput.linspace
import org.futo.voiceinput.melFilterBank
import org.futo.voiceinput.melToFreq
import org.futo.voiceinput.padY
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
    @Test fun featureExtractor_TestLinspace() {
        val values = linspace(0.0, 8000.0, 4)
        val expectedResult = arrayOf(0.0, 2666.666666666667, 5333.333333333333, 8000.0)

        assertTrue(values.toTypedArray().isEqualApprox(expectedResult))
    }

    @Test fun featureExtractor_TestLinspace2() {
        val values = linspace(0.0, 16000 / 2.0, 10)
        val expectedResult = arrayOf(
            0.0,
            888.888888888889,
            1777.77777777778,
            2666.66666666667,
            3555.55555555556,
            4444.44444444444,
            5333.33333333333,
            6222.22222222222,
            7111.11111111111,
            8000.0
        )

        assertTrue(values.toTypedArray().isEqualApprox(expectedResult))
    }

    @Test fun featureExtractor_TestDiff() {
        val testData = arrayOf(0.0, 2.0, 3.0, 4.0, 5.0, 0.0, 2.0, 4.0, -1.0).toDoubleArray()
        val result = diff(testData)

        val expectedResult = arrayOf(2.0, 1.0, 1.0, 1.0, -5.0, 2.0, 2.0, -5.0)

        assertTrue(result.toTypedArray().isEqualApprox(expectedResult))
    }

    @Test fun featureExtractor_ComparePadY() {
        val testData = arrayOf(5.0, 5.5, 0.1, 3.3, 4.9, 8.3, 1.33).toDoubleArray()
        val expectedResult = arrayOf(3.3, 0.1, 5.5, 5.0, 5.5, 0.1, 3.3, 4.9, 8.3, 1.33, 8.3, 4.9, 3.3)
        val result = padY(testData, 6)

        assertTrue(result.toTypedArray().isEqualApprox(expectedResult))
    }

    @Test fun featureExtractor_TestTriangularFilterBank() {
        val expectedResult = arrayOf(
            arrayOf(0.0, 0.0, 0.0, 0.0, 0.0),
            arrayOf(0.23215886, 0.76784114, 0.0, 0.0, 0.0),
            arrayOf(0.0, 0.0, 0.92255337, 0.07744663, 0.0),
            arrayOf(0.0, 0.0, 0.1479165, 0.8520835, 0.0),
            arrayOf(0.0, 0.0, 0.0, 0.62682501, 0.37317499),
            arrayOf(0.0, 0.0, 0.0, 0.16557449, 0.83442551),
            arrayOf(0.0, 0.0, 0.0, 0.0, 0.82394237),
            arrayOf(0.0, 0.0, 0.0, 0.0, 0.54929492),
            arrayOf(0.0, 0.0, 0.0, 0.0, 0.27464746),
            arrayOf(0.0, 0.0, 0.0, 0.0, 0.0),
        )
        val samplingRate = 16000
        val numFrequencyBins = 10
        val numMelFilters = 5

        val fftFreqs = linspace(0.0, samplingRate / 2.0, numFrequencyBins)

        val melMin = freqToMel(0.0, melScale = MelScale.Slaney)
        val melMax = freqToMel(samplingRate / 2.0, melScale = MelScale.Slaney)
        val melFreqs = linspace(melMin, melMax, numMelFilters + 2)
        val filterFreqs = melToFreq(melFreqs, melScale = MelScale.Slaney)

        val filter = createTriangularFilterBank(fftFreqs, filterFreqs)

        assertTrue(filter.isEqualApprox(expectedResult))
    }

    @Test fun featureExtractor_TestMelFilterBank() {
        val expectedResult = arrayOf(
            arrayOf(0.00000000e+00, 0.00000000e+00, 0.00000000e+00, 0.00000000e+00, 0.00000000e+00),
            arrayOf(4.61711232e-04, 1.29464619e-03, 0.00000000e+00, 0.00000000e+00, 0.00000000e+00),
            arrayOf(0.00000000e+00, 0.00000000e+00, 1.00783966e-03, 5.03780297e-05, 0.00000000e+00),
            arrayOf(0.00000000e+00, 0.00000000e+00, 1.61590771e-04, 5.54269286e-04, 0.00000000e+00),
            arrayOf(0.00000000e+00, 0.00000000e+00, 0.00000000e+00, 4.07741554e-04, 1.44540612e-04),
            arrayOf(0.00000000e+00, 0.00000000e+00, 0.00000000e+00, 1.07704059e-04, 3.23195227e-04),
            arrayOf(0.00000000e+00, 0.00000000e+00, 0.00000000e+00, 0.00000000e+00, 3.19134827e-04),
            arrayOf(0.00000000e+00, 0.00000000e+00, 0.00000000e+00, 0.00000000e+00, 2.12756552e-04),
            arrayOf(0.00000000e+00, 0.00000000e+00, 0.00000000e+00, 0.00000000e+00, 1.06378276e-04),
            arrayOf(0.00000000e+00, 0.00000000e+00, 0.00000000e+00, 0.00000000e+00, 0.00000000e+00)
        )

        val result = melFilterBank(
            10,
            5,
            0.0,
            8000.0,
            16000,
            Normalization.Slaney,
            MelScale.Slaney
        )

        assertTrue(result.isEqualApprox(expectedResult))
        assertTrue(result.transpose().isEqualApprox(expectedResult.as2DDoubleArray().transpose()))
    }

    @Test fun featureExtractor_CompareHannWindow() {
        val expectedResult = arrayOf(0.0, 0.14644661, 0.5, 0.85355339, 1.0, 0.85355339, 0.5, 0.14644661)
        val result = createHannWindow(8)

        assertTrue(result.toTypedArray().isEqualApprox(expectedResult))
    }
}