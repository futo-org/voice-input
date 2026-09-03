package org.futo.voiceinput.shared.ggml


import androidx.annotation.Keep
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.newSingleThreadContext
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(DelicateCoroutinesApi::class)
val inferenceContext = newSingleThreadContext("whisper-ggml-inference")

enum class DecodingMode(val value: Int) {
    Greedy(0),
    BeamSearch5(5)
}

class BailLanguageException(val language: String): Exception()
class InferenceCancelledException : Exception()
class InvalidModelException : Exception("The Whisper model could not be loaded from the given buffer")

@Keep
class WhisperGGML(
    file: File
) {
    private var handle: Long = 0L
    init {
        handle = openNative(file.absolutePath)

        if(handle == 0L) {
            throw InvalidModelException()
        }
    }

    private var partialResultCallback: (String) -> Unit = { }

    @Keep
    private fun invokePartialResult(text: String) {
        partialResultCallback(text.trim())
    }

    // empty languages = autodetect any language
    // 1 language = will force that language
    // 2 or more languages = autodetect between those languages
    @Throws(BailLanguageException::class, InferenceCancelledException::class)
    suspend fun infer(
        sampleFlow: Flow<FloatArray>,
        prompt: String,
        languages: Array<String>,
        bailLanguages: Array<String>,
        partialResultCallback: (String) -> Unit
    ): String = withContext(inferenceContext) {
        if(handle == 0L) {
            throw IllegalStateException("WhisperGGML has already been closed, cannot infer")
        }
        this@WhisperGGML.partialResultCallback = partialResultCallback

        val streamStatus = startStreamNative(handle, prompt, languages, bailLanguages)
        if(!streamStatus) return@withContext offlineInfer(sampleFlow, prompt, languages, bailLanguages)

        try {
            val frames = mutableListOf<FloatArray>()
            var awaitingOfflineFallback = false
            sampleFlow.collect {
                frames.add(it)
                if(awaitingOfflineFallback) return@collect
                if(!feedStreamNative(handle, it)) {
                    stopStreamNative(handle, false)
                    awaitingOfflineFallback = true
                }
            }

            if(awaitingOfflineFallback) return@withContext offlineInfer(frames, prompt, languages, bailLanguages)

            val finalResult = stopStreamNative(handle, true) ?: "?"
            return@withContext finalResult
        } catch(e: Throwable) {
            stopStreamNative(handle, false)
            throw e
        }
    }

    private suspend fun offlineInfer(
        sampleFlow: Flow<FloatArray>,
        prompt: String,
        languages: Array<String>,
        bailLanguages: Array<String>
    ) = offlineInfer(sampleFlow.toList(), prompt, languages, bailLanguages)

    private suspend fun offlineInfer(
        sampleList: List<FloatArray>,
        prompt: String,
        languages: Array<String>,
        bailLanguages: Array<String>
    ): String = withContext(inferenceContext) {
        val samples = FloatArray(sampleList.sumOf { it.size })
        var i = 0
        for(sample in sampleList) {
            sample.copyInto(samples, i)
            i += sample.size
        }

        val result = offlineInferNative(handle, samples, prompt, languages, bailLanguages)?.trim()
        if(result == null) throw IllegalStateException("Inference failed")

        if(result.contains("<>CANCELLED<>")) {
            if(result.contains("flag")) {
                throw InferenceCancelledException()
            } else if(result.contains("lang=")) {
                val language = result.split("lang=")[1]
                throw BailLanguageException(language)
            } else {
                throw IllegalStateException("Cancelled for unknown reason")
            }

        } else {
            return@withContext result
        }
    }

    fun cancel() {
        if(handle == 0L) return
        cancelNative(handle)
    }

    suspend fun close() = withContext(inferenceContext) {
        if(handle != 0L) {
            closeNative(handle)
        }
        handle = 0L
    }

    private external fun openNative(path: String): Long
    private external fun offlineInferNative(handle: Long, samples: FloatArray, prompt: String, languages: Array<String>, bailLanguages: Array<String>): String?
    private external fun startStreamNative(handle: Long, prompt: String, languages: Array<String>, bailLanguages: Array<String>): Boolean
    private external fun feedStreamNative(handle: Long, samples: FloatArray): Boolean
    private external fun stopStreamNative(handle: Long, finalize: Boolean): String?
    private external fun cancelNative(handle: Long)
    private external fun closeNative(handle: Long)
}