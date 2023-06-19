@file:Suppress("SpellCheckingInspection")

package org.futo.voiceinput

import org.apache.commons.math3.transform.DftNormalization
import org.apache.commons.math3.transform.FastFourierTransformer
import org.apache.commons.math3.transform.TransformType
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln

private fun createHannWindow(nFFT: Int): DoubleArray {
    val window = DoubleArray(nFFT)

    // Create a Hann window for even nFFT.
    // The Hann window is a taper formed by using a raised cosine or sine-squared
    // with ends that touch zero.
    for (i in 0 until nFFT) {
        window[i] = 0.5 - 0.5 * cos(2.0 * Math.PI * i / nFFT)
    }

    return window
}

/**
 * This Class calculates the MFCC, STFT values of given audio samples.
 * Source based on [MFCC.java](https://github.com/chiachunfu/speech/blob/master/speechandroid/src/org/tensorflow/demo/mfcc/MFCC.java)
 *
 * @author abhi-rawat1
 */
class AudioFeatureExtraction(
    val hopLength: Int,
    val sampleRate: Double,
    val nFFT: Int,
    val nMels: Int,
) {
    /**
     * Variable for holding n_mfcc value
     *
     */
    
    var length = -1
    private var fMax = sampleRate / 2.0
    private var fMin = 0.0

    private val window: DoubleArray = createHannWindow(nFFT)

    private val melBasis = melFilter()

    /**
     * This function generates mel spectrogram values
     */
    fun melSpectrogram(y: FloatArray): Array<DoubleArray> {
        val spectro = extractSTFTFeatures(y)
        val melS = Array(melBasis.size) { DoubleArray(spectro[0].size) }
        for (i in melBasis.indices) {
            for (j in spectro[0].indices) {
                for (k in melBasis[0].indices) {
                    melS[i][j] += melBasis[i][k] * spectro[k][j]
                }
            }
        }
        return melS
    }

    /**
     * This function pads the y values
     */
    private fun padFrame(yValues: FloatArray): Array<DoubleArray> {
        val ypad = DoubleArray(nFFT + yValues.size)
        for (i in 0 until nFFT / 2) {
            ypad[nFFT / 2 - i - 1] = yValues[i + 1].toDouble()
            ypad[nFFT / 2 + yValues.size + i] = yValues[yValues.size - 2 - i].toDouble()
        }
        for (j in yValues.indices) {
            ypad[nFFT / 2 + j] = yValues[j].toDouble()
        }
        return yFrame(ypad)
    }

    /**
     * This function extract STFT values from given Audio Magnitude Values.
     *
     */
    private fun extractSTFTFeatures(y: FloatArray): Array<DoubleArray> {
        // Short-time Fourier transform (STFT)
        val fftwin = window

        // pad y with reflect mode so it's centered. This reflect padding implementation
        // is
        val frame = padFrame(y)
        val fftmagSpec = Array(1 + nFFT / 2) { DoubleArray(frame[0].size) }
        val fftFrame = DoubleArray(nFFT)
        for (k in frame[0].indices) {
            var fftFrameCounter = 0
            for (l in 0 until nFFT) {
                fftFrame[fftFrameCounter] = fftwin[l] * frame[l][k]
                fftFrameCounter += 1
            }
            val magSpec = DoubleArray(fftFrame.size)
            val transformer = FastFourierTransformer(DftNormalization.STANDARD)
            try {
                val complx = transformer.transform(fftFrame, TransformType.FORWARD)
                for (i in complx.indices) {
                    val rr = complx[i].real
                    val ri = complx[i].imaginary
                    magSpec[i] = rr * rr + ri * ri
                }
            } catch (e: IllegalArgumentException) {
                e.printStackTrace()
            }
            for (i in 0 until 1 + nFFT / 2) {
                fftmagSpec[i][k] = magSpec[i]
            }
        }
        return fftmagSpec
    }// Return a Hann window for even nFFT.



    /**
     * This function is used to apply padding and return Frame
     *
     */
    private fun yFrame(ypad: DoubleArray): Array<DoubleArray> {
        val nFrames = 1 + (ypad.size - nFFT) / hopLength
        val winFrames = Array(nFFT) { DoubleArray(nFrames) }
        for (i in 0 until nFFT) {
            for (j in 0 until nFrames) {
                winFrames[i][j] = ypad[j * hopLength + i]
            }
        }
        return winFrames
    }

    /**
     * This function is used to create a Filterbank matrix to combine FFT bins into
     * Mel-frequency bins.
     *
     */
    private fun melFilter(): Array<DoubleArray> {
        // Create a Filterbank matrix to combine FFT bins into Mel-frequency bins.
        // Center freqs of each FFT bin
        val fftFreqs = fftFreq()
        // 'Center freqs' of mel bands - uniformly spaced between limits
        val melF = melFreq(nMels + 2)
        val fdiff = DoubleArray(melF.size - 1)
        for (i in 0 until melF.size - 1) {
            fdiff[i] = melF[i + 1] - melF[i]
        }
        val ramps = Array(melF.size) { DoubleArray(fftFreqs.size) }
        for (i in melF.indices) {
            for (j in fftFreqs.indices) {
                ramps[i][j] = melF[i] - fftFreqs[j]
            }
        }
        val weights = Array(nMels) { DoubleArray(1 + nFFT / 2) }
        for (i in 0 until nMels) {
            for (j in fftFreqs.indices) {
                val lowerF = -ramps[i][j] / fdiff[i]
                val upperF = ramps[i + 2][j] / fdiff[i + 1]
                if (lowerF > upperF && upperF > 0) {
                    weights[i][j] = upperF
                } else if (lowerF > upperF && upperF < 0) {
                    weights[i][j] = 0.0
                } else if (lowerF < upperF && lowerF > 0) {
                    weights[i][j] = lowerF
                } else if (lowerF < upperF && lowerF < 0) {
                    weights[i][j] = 0.0
                }
            }
        }
        val enorm = DoubleArray(nMels)
        for (i in 0 until nMels) {
            enorm[i] = 2.0 / (melF[i + 2] - melF[i])
            for (j in fftFreqs.indices) {
                weights[i][j] *= enorm[i]
            }
        }
        return weights

        // need to check if there's an empty channel somewhere
    }

    /**
     * To get fft frequencies
     *
     */
    private fun fftFreq(): DoubleArray {
        // Alternative implementation of np.fft.fftfreqs
        val freqs = DoubleArray(1 + nFFT / 2)
        for (i in 0 until 1 + nFFT / 2) {
            freqs[i] = 0 + sampleRate / 2 / (nFFT / 2) * i
        }
        return freqs
    }

    /**
     * To get mel frequencies
     *
     */
    private fun melFreq(numMels: Int): DoubleArray {
        // 'Center freqs' of mel bands - uniformly spaced between limits
        val LowFFreq = DoubleArray(1)
        val HighFFreq = DoubleArray(1)
        LowFFreq[0] = fMin
        HighFFreq[0] = fMax
        val melFLow = freqToMel(LowFFreq)
        val melFHigh = freqToMel(HighFFreq)
        val mels = DoubleArray(numMels)
        for (i in 0 until numMels) {
            mels[i] = melFLow[0] + (melFHigh[0] - melFLow[0]) / (numMels - 1) * i
        }
        return melToFreq(mels)
    }

    /**
     * To convert mel frequencies into hz frequencies
     *
     */
    private fun melToFreq(mels: DoubleArray): DoubleArray {
        // Fill in the linear scale
        val f_min = 0.0
        val f_sp = 200.0 / 3
        val freqs = DoubleArray(mels.size)

        // And now the nonlinear scale
        val min_log_hz = 1000.0 // beginning of log region (Hz)
        val min_log_mel = (min_log_hz - f_min) / f_sp // same (Mels)
        val logstep = ln(6.4) / 27.0
        for (i in mels.indices) {
            if (mels[i] < min_log_mel) {
                freqs[i] = f_min + f_sp * mels[i]
            } else {
                freqs[i] = min_log_hz * exp(logstep * (mels[i] - min_log_mel))
            }
        }
        return freqs
    }

    /**
     * To convert hz frequencies into mel frequencies
     *
     */
    protected fun freqToMel(freqs: DoubleArray): DoubleArray {
        val f_min = 0.0
        val f_sp = 200.0 / 3
        val mels = DoubleArray(freqs.size)

        // Fill in the log-scale part
        val min_log_hz = 1000.0 // beginning of log region (Hz)
        val min_log_mel = (min_log_hz - f_min) / f_sp // # same (Mels)
        val logstep = ln(6.4) / 27.0 // step size for log region
        for (i in freqs.indices) {
            if (freqs[i] < min_log_hz) {
                mels[i] = (freqs[i] - f_min) / f_sp
            } else {
                mels[i] = min_log_mel + ln(freqs[i] / min_log_hz) / logstep
            }
        }
        return mels
    }

    /**
     * To get log10 value.
     *
     */
    fun log10(value: Double): Double {
        return ln(value) / ln(10.0)
    }
}