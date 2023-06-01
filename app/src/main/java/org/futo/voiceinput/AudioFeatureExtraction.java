package org.futo.voiceinput;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.commons.math3.complex.Complex;
import org.apache.commons.math3.transform.DftNormalization;
import org.apache.commons.math3.transform.FastFourierTransformer;
import org.apache.commons.math3.transform.TransformType;

/**
 * This Class calculates the MFCC, STFT values of given audio samples.
 *
 * Source based on https://github.com/chiachunfu/speech/blob/master/speechandroid/src/org/tensorflow/demo/mfcc/MFCC.java
 *
 * @author abhi-rawat1
 *
 */
public class AudioFeatureExtraction {

    private int n_mfcc = 40;
    private double sampleRate = 44100.0;


    private int length = -1;
    private double fMax = sampleRate / 2.0;
    private double fMin = 0.0;
    private int n_fft = 2048;
    private int hop_length = 512;
    private int n_mels = 128;






    /**
     * Variable for holding Sample Rate value
     *
     * @param sampleRateVal
     */
    public void setSampleRate(double sampleRateVal) {
        sampleRate = sampleRateVal;
        this.fMax = this.sampleRate/2.0;
    }

    public int getLength() {
        return length;
    }

    public void setLength(int length) {
        this.length = length;
    }

    public double getfMax() {
        return fMax;
    }

    public void setfMax(double fMax) {
        this.fMax = fMax;
    }

    public double getfMin() {
        return fMin;
    }

    public void setfMin(double fMin) {
        this.fMin = fMin;
    }

    public int getN_fft() {
        return n_fft;
    }

    public void setN_fft(int n_fft) {
        this.n_fft = n_fft;
    }

    public int getHop_length() {
        return hop_length;
    }

    public void setHop_length(int hop_length) {
        this.hop_length = hop_length;
    }

    public int getN_mels() {
        return n_mels;
    }

    public void setN_mels(int n_mels) {
        this.n_mels = n_mels;
    }

    public int getN_mfcc() {
        return n_mfcc;
    }

    public double getSampleRate() {
        return sampleRate;
    }

    /**
     * Variable for holding n_mfcc value
     *
     * @param n_mfccVal
     */
    public void setN_mfcc(int n_mfccVal) {
        n_mfcc = n_mfccVal;
    }

    /**
     * This function extract MFCC values from given Audio Magnitude Values.
     *
     * @param doubleInputBuffer
     * @return
     */
    public float[] extractMFCCFeatures(float[] doubleInputBuffer) {
        final double[][] mfccResult = dctMfcc(doubleInputBuffer);
        return finalshape(mfccResult);
    }

    /**
     * This function converts 2D MFCC values into 1d
     *
     * @param mfccSpecTro
     * @return
     */
    private float[] finalshape(double[][] mfccSpecTro) {
        float[] finalMfcc = new float[mfccSpecTro[0].length * mfccSpecTro.length];
        int k = 0;
        for (int i = 0; i < mfccSpecTro[0].length; i++) {
            for (int j = 0; j < mfccSpecTro.length; j++) {
                finalMfcc[k] = (float) mfccSpecTro[j][i];
                k = k + 1;
            }
        }
        return finalMfcc;
    }

    /**
     * This function converts DCT values into mfcc
     *
     * @param y
     * @return
     */
    private double[][] dctMfcc(float[] y) {
        final double[][] specTroGram = powerToDb(melSpectrogram(y));
        final double[][] dctBasis = dctFilter(n_mfcc, n_mels);
        double[][] mfccSpecTro = new double[n_mfcc][specTroGram[0].length];
        for (int i = 0; i < n_mfcc; i++) {
            for (int j = 0; j < specTroGram[0].length; j++) {
                for (int k = 0; k < specTroGram.length; k++) {
                    mfccSpecTro[i][j] += dctBasis[i][k] * specTroGram[k][j];
                }
            }
        }
        return mfccSpecTro;
    }

    /**
     * This function generates mel spectrogram values
     *
     * @param y
     * @return
     */
    public double[][] melSpectrogram(float[] y) {
        double[][] melBasis = melFilter();
        double[][] spectro = extractSTFTFeatures(y);
        double[][] melS = new double[melBasis.length][spectro[0].length];
        for (int i = 0; i < melBasis.length; i++) {
            for (int j = 0; j < spectro[0].length; j++) {
                for (int k = 0; k < melBasis[0].length; k++) {
                    melS[i][j] += melBasis[i][k] * spectro[k][j];
                }
            }
        }
        return melS;
    }



    /**
     * This function generates mel spectrogram values with extracted STFT features as complex values
     *
     * @param y
     * @return
     */
    public float [][] melSpectrogramWithComplexValueProcessing(float[] y) {

        Complex[][] spectro = extractSTFTFeaturesAsComplexValues(y, true);
        double[][] spectroAbsVal = new double[spectro.length][spectro[0].length];

        for(int i=0;i<spectro.length;i++) {
            for(int j=0;j<spectro[0].length;j++) {
                Complex complexVal = spectro[i][j];
                double spectroDblVal = Math.sqrt((Math.pow(complexVal.getReal(), 2) + Math.pow(complexVal.getImaginary(), 2)));
                spectroAbsVal[i][j] = Math.pow(spectroDblVal,2);
            }
        }

        double[][] melBasis = melFilter();
        float[][] melS = new float[melBasis.length][spectro[0].length];
        for (int i = 0; i < melBasis.length; i++) {
            for (int j = 0; j < spectro[0].length; j++) {
                for (int k = 0; k < melBasis[0].length; k++) {
                    melS[i][j] += melBasis[i][k] * spectroAbsVal[k][j];
                }
            }
        }
        return melS;


    }


    public double[][] stftMagSpec(double[] y){
        //Short-time Fourier transform (STFT)
        final double[] fftwin = getWindow();
        //pad y with reflect mode so it's centered. This reflect padding implementation is
        // not perfect but works for this demo.
        double[] ypad = new double[n_fft+y.length];
        for (int i = 0; i < n_fft/2; i++){
            ypad[(n_fft/2)-i-1] = y[i+1];
            ypad[(n_fft/2)+y.length+i] = y[y.length-2-i];
        }
        for (int j = 0; j < y.length; j++){
            ypad[(n_fft/2)+j] = y[j];
        }

        y = null;

        final double[][] frame = yFrame(ypad);
        double[][] fftmagSpec = new double[1+n_fft/2][frame[0].length];
        double[] fftFrame = new double[n_fft];

        for (int k = 0; k < frame[0].length; k++){
            int fftFrameCounter=0;

            for (int l =0; l < n_fft; l++){
                fftFrame[l] = fftwin[l]*frame[l][k];
                fftFrameCounter = fftFrameCounter + 1;

            }

            double[] tempConversion = new double[fftFrame.length];
            double[] tempImag = new double[fftFrame.length];

            FastFourierTransformer transformer = new FastFourierTransformer(DftNormalization.STANDARD);
            try {
                Complex[] complx = transformer.transform(fftFrame, TransformType.FORWARD);

                for (int i = 0; i < complx.length; i++) {
                    double rr = (complx[i].getReal());

                    double ri = (complx[i].getImaginary());

                    tempConversion[i] = rr * rr + ri * ri;
                    tempImag[i] = ri;
                }

            } catch (IllegalArgumentException e) {
                System.out.println(e);
            }


            double[] magSpec = tempConversion;
            for (int i =0; i < 1+n_fft/2; i++){
                fftmagSpec[i][k] = magSpec[i];
            }


        }
        return fftmagSpec;
    }


    /**
     * This function extracts the STFT values as complex values
     *
     * @param y
     * @return
     */

    public Complex[][] extractSTFTFeaturesAsComplexValues(float[] y, boolean paddingFlag) {

        // Short-time Fourier transform (STFT)
        final double[] fftwin = getWindow();

        // pad y with reflect mode so it's centered. This reflect padding implementation
        // is
        final double[][] frame = padFrame(y, paddingFlag);
        double[][] fftmagSpec = new double[1 + n_fft / 2][frame[0].length];

        double[] fftFrame = new double[n_fft];

        Complex [][] complex2DArray = new Complex[1+n_fft/2][frame[0].length];
        Complex [] cmplx1DArr = new Complex [n_fft];


        float [][] invFrame = new float [n_fft][frame[0].length];

        for (int k = 0; k < frame[0].length; k++) {
            int fftFrameCounter = 0;
            for (int l = 0; l < n_fft; l++) {
                fftFrame[fftFrameCounter] = fftwin[l] * frame[l][k];
                fftFrameCounter = fftFrameCounter + 1;
            }

            double[] tempConversion = new double[fftFrame.length];
            double[] tempImag = new double[fftFrame.length];

            FastFourierTransformer transformer = new FastFourierTransformer(DftNormalization.STANDARD);

            try {
                Complex[] complx = transformer.transform(fftFrame, TransformType.FORWARD);

                Complex[] Invcomplx = transformer.transform(complx, TransformType.INVERSE);

                //FFT transformed data will be over the length of FFT
                //data will be sinusoidal in nature - so taking the values of 1+n_fft/2 only for processing
                for(int i=0;i<1+n_fft/2;i++) {
                    complex2DArray[i][k] = complx[i];

                }



                Complex [] cmplxINV1DArr = new Complex [n_fft];


                for(int j=0;j<1+n_fft/2;j++) {
                    cmplxINV1DArr[j] = complex2DArray[j][k];
                }

                int j_index = 2;
                for(int k1=1+n_fft/2;k1<n_fft;k1++){
                    cmplxINV1DArr[k1]=new Complex(cmplxINV1DArr[k1-j_index].getReal(), -1 * cmplxINV1DArr[k1-j_index].getImaginary());
                    j_index = j_index + 2;
                }

                Complex[] complx1 = transformer.transform(cmplxINV1DArr, TransformType.INVERSE);


                for(int p=0;p<complx1.length;p++) {
                    if(fftwin[p]!=0) {
                        invFrame[p][k] = (float) (complx1[p].getReal()/fftwin[p]);
                        //invFrame[p][i] = (float) (complx[p].getReal() * fftwin[p]);
                    }else {
                        invFrame[p][k] = 0;
                    }
                }


            } catch (IllegalArgumentException e) {
                System.out.println(e);
            }

        }


        float [] yValues = new float [(hop_length * (invFrame[0].length-1) + n_fft)];

        for (int i = 0; i < n_fft; i++) {
            for (int j = 0; j < invFrame[0].length; j++) {
                yValues[j*hop_length + i] = invFrame[i][j];

            }
        }


        return complex2DArray;

    }


    /**
     * This function extracts the inverse STFT values as complex values
     *
     * @param
     * @return
     */

    public float [] extractInvSTFTFeaturesAsFloatValues(Complex[][] cmplxSTFTValues, boolean paddingFlag){

        int n_fft = 2 *(cmplxSTFTValues.length - 1);


        int n_frames = cmplxSTFTValues[0].length;


        int length =  ((n_frames - 1) * hop_length) + n_fft;

        if(this.length != -1) {
            length = this.length;
        }

        // Short-time Fourier transform (STFT)
        final double[] fftwin = getWindow();

        float [][] invFrame = new float [n_fft][n_frames];

        Complex[] complx = null;

        for(int i=0;i<cmplxSTFTValues[0].length;i++) {
            Complex [] cmplx1DArr = new Complex [n_fft];
            for(int j=0;j<1+n_fft/2;j++) {
                cmplx1DArr[j] = cmplxSTFTValues[j][i];
            }

            //processed FFT values would be of length 1+n_fft/2
            //to peform inv FFT, we need to recreate the values back to the length of n_fft
            //as the values are inverse in nature - recreating the second half from the first off
            //for n_fft value of 4096 - value at the index of 2049 and 2047 will be same and the sequence will continue for (2050-2046), (2051-2045) etc
            //below loop will recreate those values
            int j_index = 2;
            for(int k=1+n_fft/2;k<n_fft;k++){
                cmplx1DArr[k]=new Complex(cmplx1DArr[k-j_index].getReal(), -1 * cmplx1DArr[k-j_index].getImaginary());
                j_index = j_index + 2;
            }


            FastFourierTransformer transformer = new FastFourierTransformer(DftNormalization.STANDARD);


            try {
                complx = transformer.transform(cmplx1DArr, TransformType.INVERSE);

            } catch (IllegalArgumentException e) {
                System.out.println(e);
            }

            for(int p=0;p<complx.length;p++) {
                if(fftwin[p]!=0) {
                    invFrame[p][i] = (float) (complx[p].getReal()/fftwin[p]);
                    //invFrame[p][i] = (float) (complx[p].getReal() * fftwin[p]);
                }else {
                    invFrame[p][i] = 0;
                }
            }

        }


        float [] yValues = new float [length];

        for (int i = 0; i < n_fft; i++) {
            for (int j = 0; j < n_frames; j++) {
                yValues[j*hop_length + i] = invFrame[i][j];

            }
        }



        float [] yValues_unpadded = new float[yValues.length-n_fft];

        if(paddingFlag) {

            for(int i=0;i<yValues_unpadded.length;i++) {
                yValues_unpadded[i] = yValues[n_fft/2 + i];
            }

            return yValues_unpadded;
        }


        return yValues;

    }


    private double [][] padCenter(double [] fftwin, int size) {

        int n = fftwin.length;

        int lpad = ((size - n)/2);

        //this is a temp way for padding...this code needs to be updated as per pad_center method of istft function

        double [][] fftWin2D = new double [n][1];

        for(int i=0;i<n;i++) {
            fftWin2D[i][0]= fftwin[i];
        }

        return fftWin2D;
    }

    /**
     * This function pads the y values
     *
     * @param
     * @return
     */

    private double[][] padFrame(float[] yValues, boolean paddingFlag){

        double[][] frame = null;

        if(paddingFlag) {


            double[] ypad = new double[n_fft + yValues.length];
            for (int i = 0; i < n_fft / 2; i++) {
                ypad[(n_fft / 2) - i - 1] = yValues[i + 1];
                ypad[(n_fft / 2) + yValues.length + i] = yValues[yValues.length - 2 - i];
            }
            for (int j = 0; j < yValues.length; j++) {
                ypad[(n_fft / 2) + j] = yValues[j];
            }

            frame = yFrame(ypad);
        }
        else {


            double[] yDblValues = new double[yValues.length];
            for (int i = 0 ; i < yValues.length; i++)
            {
                yDblValues[i] = (double) yValues[i];
            }

            frame = yFrame(yDblValues);

        }

        return frame;
    }

    /**
     * This function extract STFT values from given Audio Magnitude Values.
     *
     * @param y
     * @return
     */
    public double[][] extractSTFTFeatures(float[] y) {
        // Short-time Fourier transform (STFT)
        final double[] fftwin = getWindow();

        // pad y with reflect mode so it's centered. This reflect padding implementation
        // is
        final double[][] frame = padFrame(y, true);
        double[][] fftmagSpec = new double[1 + n_fft / 2][frame[0].length];

        double[] fftFrame = new double[n_fft];

        for (int k = 0; k < frame[0].length; k++) {
            int fftFrameCounter = 0;
            for (int l = 0; l < n_fft; l++) {
                fftFrame[fftFrameCounter] = fftwin[l] * frame[l][k];
                fftFrameCounter = fftFrameCounter + 1;
            }

            double[] tempConversion = new double[fftFrame.length];
            double[] tempImag = new double[fftFrame.length];

            FastFourierTransformer transformer = new FastFourierTransformer(DftNormalization.STANDARD);



            try {
                Complex[] complx = transformer.transform(fftFrame, TransformType.FORWARD);

                for (int i = 0; i < complx.length; i++) {
                    double rr = (complx[i].getReal());

                    double ri = (complx[i].getImaginary());

                    tempConversion[i] = rr * rr + ri*ri;
                    tempImag[i] = ri;
                }

            } catch (IllegalArgumentException e) {
                System.out.println(e);
            }



            double[] magSpec = tempConversion;
            for (int i = 0; i < 1 + n_fft / 2; i++) {
                fftmagSpec[i][k] = magSpec[i];
            }
        }
        return fftmagSpec;
    }

    /**
     * This function is used to get hann window, librosa
     *
     * @return
     */
    private double[] getWindow() {
        // Return a Hann window for even n_fft.
        // The Hann window is a taper formed by using a raised cosine or sine-squared
        // with ends that touch zero.
        double[] win = new double[n_fft];
        for (int i = 0; i < n_fft; i++) {
            win[i] = 0.5 - 0.5 * Math.cos(2.0 * Math.PI * i / n_fft);
        }
        return win;
    }

    /**
     * This function is used to apply padding and return Frame
     *
     * @param ypad
     * @return
     */
    private double[][] yFrame(double[] ypad) {

        final int n_frames = 1 + (ypad.length - n_fft) / hop_length;

        double[][] winFrames = new double[n_fft][n_frames];

        for (int i = 0; i < n_fft; i++) {
            for (int j = 0; j < n_frames; j++) {
                winFrames[i][j] = ypad[j * hop_length + i];
            }
        }
        return winFrames;
    }

    /**
     * This function is used to convert Power Spectrogram values into db values.
     *
     * @param melS
     * @return
     */
    private double[][] powerToDb(double[][] melS) {
        // Convert a power spectrogram (amplitude squared) to decibel (dB) units
        // This computes the scaling ``10 * log10(S / ref)`` in a numerically
        // stable way.
        double[][] log_spec = new double[melS.length][melS[0].length];
        double maxValue = -100;
        for (int i = 0; i < melS.length; i++) {
            for (int j = 0; j < melS[0].length; j++) {
                double magnitude = Math.abs(melS[i][j]);
                if (magnitude > 1e-10) {
                    log_spec[i][j] = 10.0 * log10(magnitude);
                } else {
                    log_spec[i][j] = 10.0 * (-10);
                }
                if (log_spec[i][j] > maxValue) {
                    maxValue = log_spec[i][j];
                }
            }
        }

        // set top_db to 80.0
        for (int i = 0; i < melS.length; i++) {
            for (int j = 0; j < melS[0].length; j++) {
                if (log_spec[i][j] < maxValue - 80.0) {
                    log_spec[i][j] = maxValue - 80.0;
                }
            }
        }
        // ref is disabled, maybe later.
        return log_spec;
    }

    /**
     * This function is used to get dct filters.
     *
     * @param n_filters
     * @param n_input
     * @return
     */
    private double[][] dctFilter(int n_filters, int n_input) {
        // Discrete cosine transform (DCT type-III) basis.
        double[][] basis = new double[n_filters][n_input];
        double[] samples = new double[n_input];
        for (int i = 0; i < n_input; i++) {
            samples[i] = (1 + 2 * i) * Math.PI / (2.0 * (n_input));
        }
        for (int j = 0; j < n_input; j++) {
            basis[0][j] = 1.0 / Math.sqrt(n_input);
        }
        for (int i = 1; i < n_filters; i++) {
            for (int j = 0; j < n_input; j++) {
                basis[i][j] = Math.cos(i * samples[j]) * Math.sqrt(2.0 / (n_input));
            }
        }
        return basis;
    }

    /**
     * This function is used to create a Filterbank matrix to combine FFT bins into
     * Mel-frequency bins.
     *
     * @return
     */
    private double[][] melFilter() {
        // Create a Filterbank matrix to combine FFT bins into Mel-frequency bins.
        // Center freqs of each FFT bin
        final double[] fftFreqs = fftFreq();
        // 'Center freqs' of mel bands - uniformly spaced between limits
        final double[] melF = melFreq(n_mels + 2);

        double[] fdiff = new double[melF.length - 1];
        for (int i = 0; i < melF.length - 1; i++) {
            fdiff[i] = melF[i + 1] - melF[i];
        }

        double[][] ramps = new double[melF.length][fftFreqs.length];
        for (int i = 0; i < melF.length; i++) {
            for (int j = 0; j < fftFreqs.length; j++) {
                ramps[i][j] = melF[i] - fftFreqs[j];
            }
        }

        double[][] weights = new double[n_mels][1 + n_fft / 2];
        for (int i = 0; i < n_mels; i++) {
            for (int j = 0; j < fftFreqs.length; j++) {
                double lowerF = -ramps[i][j] / fdiff[i];
                double upperF = ramps[i + 2][j] / fdiff[i + 1];
                if (lowerF > upperF && upperF > 0) {
                    weights[i][j] = upperF;
                } else if (lowerF > upperF && upperF < 0) {
                    weights[i][j] = 0;
                } else if (lowerF < upperF && lowerF > 0) {
                    weights[i][j] = lowerF;
                } else if (lowerF < upperF && lowerF < 0) {
                    weights[i][j] = 0;
                } else {
                }
            }
        }

        double enorm[] = new double[n_mels];
        for (int i = 0; i < n_mels; i++) {
            enorm[i] = 2.0 / (melF[i + 2] - melF[i]);
            for (int j = 0; j < fftFreqs.length; j++) {
                weights[i][j] *= enorm[i];
            }
        }
        return weights;

        // need to check if there's an empty channel somewhere
    }

    /**
     * To get fft frequencies
     *
     * @return
     */
    private double[] fftFreq() {
        // Alternative implementation of np.fft.fftfreqs
        double[] freqs = new double[1 + n_fft / 2];
        for (int i = 0; i < 1 + n_fft / 2; i++) {
            freqs[i] = 0 + (sampleRate / 2) / (n_fft / 2) * i;
        }
        return freqs;
    }

    /**
     * To get mel frequencies
     *
     * @param numMels
     * @return
     */
    private double[] melFreq(int numMels) {
        // 'Center freqs' of mel bands - uniformly spaced between limits
        double[] LowFFreq = new double[1];
        double[] HighFFreq = new double[1];
        LowFFreq[0] = fMin;
        HighFFreq[0] = fMax;
        final double[] melFLow = freqToMel(LowFFreq);
        final double[] melFHigh = freqToMel(HighFFreq);
        double[] mels = new double[numMels];
        for (int i = 0; i < numMels; i++) {
            mels[i] = melFLow[0] + (melFHigh[0] - melFLow[0]) / (numMels - 1) * i;
        }
        return melToFreq(mels);
    }

    /**
     * To convert mel frequencies into hz frequencies
     *
     * @param mels
     * @return
     */
    private double[] melToFreqS(double[] mels) {
        double[] freqs = new double[mels.length];
        for (int i = 0; i < mels.length; i++) {
            freqs[i] = 700.0 * (Math.pow(10, mels[i] / 2595.0) - 1.0);
        }
        return freqs;
    }

    /**
     * To convert hz frequencies into mel frequencies.
     *
     * @param freqs
     * @return
     */
    protected double[] freqToMelS(double[] freqs) {
        double[] mels = new double[freqs.length];
        for (int i = 0; i < freqs.length; i++) {
            mels[i] = 2595.0 * log10(1.0 + freqs[i] / 700.0);
        }
        return mels;
    }

    /**
     * To convert mel frequencies into hz frequencies
     *
     * @param mels
     * @return
     */
    private double[] melToFreq(double[] mels) {
        // Fill in the linear scale
        final double f_min = 0.0;
        final double f_sp = 200.0 / 3;
        double[] freqs = new double[mels.length];

        // And now the nonlinear scale
        final double min_log_hz = 1000.0; // beginning of log region (Hz)
        final double min_log_mel = (min_log_hz - f_min) / f_sp; // same (Mels)
        final double logstep = Math.log(6.4) / 27.0;

        for (int i = 0; i < mels.length; i++) {
            if (mels[i] < min_log_mel) {
                freqs[i] = f_min + f_sp * mels[i];
            } else {
                freqs[i] = min_log_hz * Math.exp(logstep * (mels[i] - min_log_mel));
            }
        }
        return freqs;
    }

    /**
     * To convert hz frequencies into mel frequencies
     *
     * @param freqs
     * @return
     */
    protected double[] freqToMel(double[] freqs) {
        final double f_min = 0.0;
        final double f_sp = 200.0 / 3;
        double[] mels = new double[freqs.length];

        // Fill in the log-scale part

        final double min_log_hz = 1000.0; // beginning of log region (Hz)
        final double min_log_mel = (min_log_hz - f_min) / f_sp; // # same (Mels)
        final double logstep = Math.log(6.4) / 27.0; // step size for log region

        for (int i = 0; i < freqs.length; i++) {
            if (freqs[i] < min_log_hz) {
                mels[i] = (freqs[i] - f_min) / f_sp;
            } else {
                mels[i] = min_log_mel + Math.log(freqs[i] / min_log_hz) / logstep;
            }
        }
        return mels;
    }

    /**
     * To get log10 value.
     *
     * @param value
     * @return
     */
    public double log10(double value) {
        return Math.log(value) / Math.log(10);
    }
}
