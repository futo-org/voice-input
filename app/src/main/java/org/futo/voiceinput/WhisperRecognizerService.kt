package org.futo.voiceinput

import android.app.Service
import android.content.Intent
import android.speech.RecognitionService

class WhisperRecognizerService : RecognitionService() {
    override fun onStartListening(p0: Intent?, p1: Callback?) {
        print("Start Listening :)")
        TODO("Not yet implemented")
    }

    override fun onCancel(p0: Callback?) {
        print("Cancel :)")
        TODO("Not yet implemented")
    }

    override fun onStopListening(p0: Callback?) {
        print("STOP Listening :)")
        TODO("Not yet implemented")
    }
}