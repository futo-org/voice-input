package org.futo.voiceinput.shared.whisper

import android.util.Log
import androidx.collection.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import org.futo.voiceinput.shared.ggml.BailLanguageException
import org.futo.voiceinput.shared.ggml.InferenceCancelledException
import org.futo.voiceinput.shared.types.InferenceState
import org.futo.voiceinput.shared.types.Language
import org.futo.voiceinput.shared.types.ModelInferenceCallback
import org.futo.voiceinput.shared.types.ModelLoader
import org.futo.voiceinput.shared.types.getLanguageFromWhisperString
import org.futo.voiceinput.shared.types.toWhisperString


data class MultiModelRunConfiguration(
    val primaryModel: ModelLoader,
    val languageSpecificModels: Map<Language, ModelLoader>
)

data class DecodingConfiguration(
    val glossary: List<String>,
    val languages: Set<Language>,
    val suppressSymbols: Boolean,
    val tier: Int,
)

private fun<T> MutableSet<T>.flatMapInPlace(transformator: (T) -> Iterable<T>) {
    val result = flatMap(transformator)
    clear()
    addAll(result)
}

object GlossaryProcessor {
    private val cache = LruCache<String, Glossary>(6)
    class Glossary(val compiledWords: String, private val transformationRules: List<Pair<Regex, String>>) {
        fun processResult(result: String) =
            transformationRules.fold(result) { v, rule -> rule.first.replace(v, rule.second) }
    }


    private fun wordToRegex(word: String) = "\\b${word.replace(" ", "[\\.,\\?!\\s]+")}\\b"
        .toRegex(RegexOption.IGNORE_CASE)

    fun compileGlossary(model: ModelLoader, languages: Set<Language>, words: List<String>): Glossary {
        val cacheKey = words.joinToString("\n") + model.caps.requiresPersonalDictSpecialCharacterProcessing + languages
        return cache[cacheKey] ?: run {
            val transformationRules = mutableListOf<Pair<Regex, String>>()
            val compiledWords = buildString {
                for (word in words) {
                    val transformedWords = mutableSetOf(word)
                    if (Language.English in languages && model.caps.requiresPersonalDictSpecialCharacterProcessing) {
                        val rules = listOf(
                            "@" to setOf("at", "at the"),
                            ".com" to setOf("dot com", "dotcom"), // asr4all likes dotcom sometimes :)
                            "." to setOf("dot"), // period?
                            "/" to setOf("slash", "or"),
                            "+" to setOf("plus"),
                            "-" to setOf("minus", "dash", ""),
                            ":" to setOf("colon"),

                            "10" to setOf("ten", "one zero"),
                            "11" to setOf("eleven", "one one"),
                            "12" to setOf("twelve", "one two"),
                            "13" to setOf("thirteen", "one three"),
                            "14" to setOf("fourteen", "one four"),
                            "15" to setOf("fifteen", "one five"),
                            "16" to setOf("sixteen", "one six"),
                            "17" to setOf("seventeen", "one seven"),
                            "18" to setOf("eighteen", "one eight"),
                            "19" to setOf("nineteen", "one nine"),

                            "0" to setOf("zero"),
                            "1" to setOf("one"),
                            "2" to setOf("two"),
                            "3" to setOf("three"),
                            "4" to setOf("four"),
                            "5" to setOf("five"),
                            "6" to setOf("six"),
                            "7" to setOf("seven"),
                            "8" to setOf("eight"),
                            "9" to setOf("nine")
                        )

                        rules.forEach { (character, replacements) ->
                            if (transformedWords.any { character in it }) {
                                transformedWords.flatMapInPlace {
                                    replacements.map { substitution ->
                                        it.replace(
                                            character,
                                            " $substitution "
                                        ).replace("  ", " ").trim()
                                    }
                                }
                            }
                        }
                    }

                    transformedWords.forEach {
                        append(it)
                        append('\n')

                        // PCEC may insert punctuation in long stuff like "Adamson one, two, three at Gmail dot com"
                        // so this evil regex is needed to fix it
                        if (it != word || it.any(Char::isUpperCase) || it.contains(' '))
                            transformationRules.add(wordToRegex(it) to word)
                    }
                }

                if ((Language.English in languages || Language.Spanish in languages)  && model.caps.requiresPersonalDictSpecialCharacterProcessing) {
                    for(word in listOf("FUTO Voice Input", "FUTO Keyboard", "FUTO")) {
                        append(word)
                        append('\n')
                        transformationRules.add(wordToRegex(word) to word)
                    }
                }
            }

            val glossary = Glossary(compiledWords, transformationRules)
            cache.put(cacheKey, glossary)

            glossary
        }
    }
}

class MultiModelRunner(
    private val modelManager: ModelManager
) {
    suspend fun preload(runConfiguration: MultiModelRunConfiguration) = coroutineScope {
        val jobs = mutableListOf<Job>()

        jobs.add(launch(Dispatchers.Default) {
            modelManager.obtainModel(runConfiguration.primaryModel)
        })

        if (runConfiguration.languageSpecificModels.count() < 2) {
            runConfiguration.languageSpecificModels.forEach {
                jobs.add(launch(Dispatchers.Default) {
                    modelManager.obtainModel(it.value)
                })
            }
        }

        jobs.joinAll()
    }

    @Throws(InferenceCancelledException::class)
    suspend fun run(
        sampleFlow: Flow<FloatArray>,
        runConfiguration: MultiModelRunConfiguration,
        decodingConfiguration: DecodingConfiguration,
        callback: ModelInferenceCallback
    ): String = coroutineScope {
        callback.updateStatus(InferenceState.LoadingModel)
        val primaryModel = modelManager.obtainModel(runConfiguration.primaryModel)

        val allowedLanguages = decodingConfiguration.languages.map { it.toWhisperString() }.toTypedArray()
        val bailLanguages = runConfiguration.languageSpecificModels.filter { it.value != runConfiguration.primaryModel }.keys.map { it.toWhisperString() }.toTypedArray()

        val glossary = GlossaryProcessor.compileGlossary(
            runConfiguration.primaryModel,
            decodingConfiguration.languages,
            decodingConfiguration.glossary
        )

        val result = try {
            callback.updateStatus(InferenceState.Encoding)
            primaryModel.infer(
                sampleFlow = sampleFlow,
                prompt = glossary.compiledWords,
                languages = allowedLanguages,
                bailLanguages = bailLanguages,
                partialResultCallback = {
                    callback.partialResult(glossary.processResult(it))
                },
                tier = decodingConfiguration.tier,
            )
        } catch(e: BailLanguageException) {
            callback.updateStatus(InferenceState.SwitchingModel)
            val language = getLanguageFromWhisperString(e.language)

            val specificModelLoader = runConfiguration.languageSpecificModels[language]!!
            val specificModel = modelManager.obtainModel(specificModelLoader)

            val remainder = sampleFlow.toList()
            specificModel.infer(
                sampleFlow = (listOf(e.samples) + remainder).asFlow(),
                prompt = glossary.compiledWords,
                languages = arrayOf(e.language),
                bailLanguages = arrayOf(),
                partialResultCallback = {
                    callback.partialResult(glossary.processResult(it))
                },
                tier = 3,//decodingConfiguration.tier,
            )
        }

        return@coroutineScope glossary.processResult(result)
    }

    fun cancelAll() {
        modelManager.cancelAll()
    }
}