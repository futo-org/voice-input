package org.futo.voiceinput.ggml

import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.newSingleThreadContext
import kotlinx.coroutines.withContext
import java.nio.Buffer

@OptIn(DelicateCoroutinesApi::class)
val inferenceContext = newSingleThreadContext("whisper-ggml-inference")

class WhisperGGML(
    modelBuffer: Buffer,
    val partialResultCallback: (String) -> Unit
) {
    companion object {
        init {
            System.loadLibrary("voiceinput")
        }
    }

    private var handle: Long = 0L
    init {
        handle = openFromBufferNative(modelBuffer)

        if(handle == 0L) {
            throw IllegalArgumentException("The Whisper model could not be loaded from the given buffer")
        }
    }

    private fun invokePartialResult(text: String) {
        partialResultCallback(text)
    }

    // empty languages = autodetect any language
    // 1 language = will force that language
    // 2 or more languages = autodetect between those languages
    suspend fun infer(samples: FloatArray, prompt: String, languages: Array<String>): String = withContext(inferenceContext) {
        if(handle == 0L) {
            throw IllegalStateException("WhisperGGML has already been closed, cannot infer")
        }
        return@withContext inferNative(handle, samples, prompt, languages)
    }

    fun close() {
        if(handle != 0L) {
            closeNative(handle)
        }
        handle = 0L
    }

    private external fun openNative(path: String): Long
    private external fun openFromBufferNative(buffer: Buffer): Long
    private external fun inferNative(handle: Long, samples: FloatArray, prompt: String, languages: Array<String>): String
    private external fun closeNative(handle: Long)
}