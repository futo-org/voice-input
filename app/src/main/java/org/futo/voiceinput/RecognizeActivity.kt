package org.futo.voiceinput

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Bundle
import android.speech.RecognizerIntent
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.view.WindowCompat
import org.futo.voiceinput.AudioFeatureExtraction
import org.futo.voiceinput.WhisperTokenizer
import org.futo.voiceinput.databinding.ActivityRecognizeBinding
import org.futo.voiceinput.ml.Whisper
import org.tensorflow.lite.DataType
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer
import java.util.Timer


class RecognizeActivity : AppCompatActivity() {
    private lateinit var binding: ActivityRecognizeBinding

    private val PERMISSION_CODE = 904151;

    private var isRecording = false
    private lateinit var recorder: AudioRecord
    private var timeoutTimer = Timer()

    // somehow cache this so we don't load every time activity is started?
    private lateinit var model: Whisper


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        binding = ActivityRecognizeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        WhisperTokenizer.init(this);

        recorder = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            16000,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT, // could use ENCODING_PCM_FLOAT
            16000 * 2 * 30
        )

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            // request permission and call startRecording once granted, or exit activity if denied
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), PERMISSION_CODE)
        }else{
            startRecording()
        }

        binding.FinishRecording.setOnClickListener {
            onFinishRecording()
        }

        model = Whisper.newInstance(this)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if(requestCode == PERMISSION_CODE){
            for (i in permissions.indices) {
                val permission = permissions[i]
                val grantResult = grantResults[i]
                if (permission == Manifest.permission.RECORD_AUDIO) {
                    if (grantResult == PackageManager.PERMISSION_GRANTED) {
                        startRecording()
                    } else {
                        // show dialog saying cannot do speech to text without mic permission?
                        setResult(RESULT_CANCELED, null)
                        finish()
                    }
                }
            }
        }
    }

    fun sendResult(result: String) {
        val returnIntent = Intent()

        val results = listOf(result);
        returnIntent.putStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS, ArrayList(results))
        setResult(RESULT_OK, returnIntent)
        finish()
    }

    
    /*var timeoutTask = TimerTask() {
        override fun run() {

        }
    }*/

    fun startRecording(){
        try {
            recorder = AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                16000,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT, // could use ENCODING_PCM_FLOAT
                16000 * 2 * 30
            )

            recorder.startRecording()

            isRecording = true

            // ????

            // set 30 second timeout
            //timeoutTimer.schedule(, 30_000L)

            // Have a UI feedback for audio magnitude!!
            // When button pressed, or silence for a while, stop recording and process result
            // 30 second limit as well
            // Then call sendResult(value)
        } catch(e: SecurityException){
            // this should not be reached, as this function should never be called without
            // permission.
            e.printStackTrace()
        }
    }

    fun runModel(shorts: ShortArray){
        // todo: this still only do 5 seconds we need 30 seconds or whatever
        // lenght we recorded
        val audioSamples = FloatArray(16000 * 30)
        for (i in 0 until 16000 * 30) {
            audioSamples[i] = (shorts[i].toDouble() / 32768.0).toFloat()
        }

        val extractor =
            AudioFeatureExtraction()
        extractor.hop_length = 160
        extractor.n_fft = 512
        extractor.sampleRate = 16000.0
        extractor.n_mels = 80


        val mel = FloatArray(80 * 3000)
        val data = extractor.melSpectrogram(audioSamples)
        for (i in 0..79) {
            for (j in data[i].indices) {
                if((i * 3000 + j) >= (80 * 3000)) {
                    continue
                }
                mel[i * 3000 + j] = ((extractor.log10(
                    Math.max(
                        0.000000001,
                        data[i][j]
                    )
                ) + 4.0) / 4.0).toFloat()
            }
        }

        // Creates inputs for reference.
        val inputFeature0 = TensorBuffer.createFixedSize(intArrayOf(1, 80, 3000), DataType.FLOAT32)
        inputFeature0.loadArray(mel)


        println("Call model...")
        // Runs model inference and gets result.
        val outputs: Whisper.Outputs = model.process(inputFeature0)
        val outputFeature0 = outputs.outputFeature0AsTensorBuffer

        println("Output...")
        val text = WhisperTokenizer.convertTokensToString(outputFeature0)

        sendResult(text)
    }

    fun onFinishRecording() {
        // stop recorder
        if(!isRecording) {
            println("Finish Recording should not be called when not recording")
            return
        }

        isRecording = false
        recorder.stop()

        val shorts = ShortArray(16000 * 30)
        recorder.read(shorts, 0, 16000 * 30)

        runModel(shorts)
    }

    override fun onDestroy() {
        super.onDestroy()

        // popupWindow
    }
}