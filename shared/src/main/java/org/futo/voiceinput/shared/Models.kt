package org.futo.voiceinput.shared

import org.futo.voiceinput.shared.types.Language
import org.futo.voiceinput.shared.types.LegacyId
import org.futo.voiceinput.shared.types.ModelBuiltInAsset
import org.futo.voiceinput.shared.types.ModelCapabilities
import org.futo.voiceinput.shared.types.ModelDownloadable
import org.futo.voiceinput.shared.types.ModelLoader

val WhisperEnCapabilities = ModelCapabilities(
    supportedLanguages = setOf(Language.English),
    supportsLanguageDetection = false,
    supportsPersonalDict = true,
    supportsStreaming = false,
    isWhisper = true,
)

val WhisperMultiCapabilities = ModelCapabilities(
    supportedLanguages = Language.entries.toSet(),
    supportsLanguageDetection = true,
    supportsPersonalDict = true,
    supportsStreaming = false,
    isWhisper = true,
)

val Asr4AllCapabilities = ModelCapabilities(
    supportedLanguages = setOf(Language.English),
    supportsLanguageDetection = false,
    supportsPersonalDict = true,
    supportsStreaming = true,
    requiresPersonalDictSpecialCharacterProcessing = true,
)

private val Nemotron600M = ModelDownloadable(
    id = "nemotron-multi-600",
    legacyId = LegacyId.MultiNemotron,
    name = R.string.nemotron_streaming_name,
    ggmlFile = "nemotron-3.5-asr-streaming-0.6b-Q4_K_M.gguf",
    checksum = "41c99fa5fb6f3d35f68e79adc3e755eca2232a8d921178bd647b71194792b8fd",
    caps = ModelCapabilities(
        // from https://huggingface.co/nvidia/nemotron-3.5-asr-streaming-0.6b#supported-languages
        supportedLanguages = setOf(
            // transcription ready
            Language.English, Language.Spanish, Language.French,
            Language.Italian, Language.Portuguese, Language.Dutch,
            Language.German, Language.Turkish, Language.Russian,
            Language.Arabic, Language.Hindi, Language.Japanese,
            Language.Korean, Language.Vietnamese, Language.Ukrainian,

            // broad coverage
            Language.Polish, Language.Swedish, Language.Czech,
            Language.Norwegian, Language.Danish, Language.Bulgarian,
            Language.Finnish, Language.Croatian, Language.Slovak,
            Language.Chinese, Language.Hungarian, Language.Romanian,
            Language.Estonian,

            // adaptation ready excluded
        ),
        supportsLanguageDetection = false,
        supportsPersonalDict = false,
        supportsStreaming = true,
        paramCount = 600,
        unrecommendedLanguages = setOf(
            Language.Japanese, // Outputs nothing a lot of the time!
            Language.Korean,
        )
    )
)

val ALL_MODELS: List<ModelLoader> = listOf(
    ModelDownloadable(
        id = "whisper-en-tiny",
        legacyId = LegacyId.EnglishWhisperTiny,
        name = R.string.tiny_en_name,
        ggmlFile = "tiny_en_acft_q8_0.bin",
        checksum = "4b5480aa1b14a7efc5b578ef176510970a898049671c3cd237285b3e3f6bfbfc",
        caps = WhisperEnCapabilities.copy(paramCount = 39),
    ),
    ModelDownloadable(
        id = "whisper-en-base",
        legacyId = LegacyId.EnglishWhisperBase,
        name = R.string.base_en_name,
        ggmlFile = "base_en_acft_q8_0.bin",
        checksum = "e9b4b7b81b8a28769e8aa9962aa39bb9f21b622cf6a63982e93f065ed5caf1c8",
        caps = WhisperEnCapabilities.copy(paramCount = 74),
    ),
    ModelDownloadable(
        id = "whisper-en-small",
        legacyId = LegacyId.EnglishWhisperSmall,
        name = R.string.small_en_name,
        ggmlFile = "small_en_acft_q8_0.bin",
        checksum = "58fbe949992dafed917590d58bc12ca577b08b9957f0b3e0d7ee71b64bed3aa8",
        caps = WhisperEnCapabilities.copy(paramCount = 244),
    ),
    ModelDownloadable(
        id = "asr4all-s",
        legacyId = LegacyId.EnglishAsr4allS,
        name = R.string.asr4all_s_name,
        ggmlFile = "asr4all-s-gptq-Q8_0.gguf",
        checksum = "812674fe5865136a5404c52c4f551da454e5d90d520ef770b7ca925f5babcddb",
        caps = Asr4AllCapabilities.copy(paramCount = 28),
        backwardsCompatFiles = listOf("asr4all-s-Q8_0.gguf", "asr4all-s-Q8_0-v2.gguf"),
    ),
    ModelBuiltInAsset(
        id = "asr4all-m",
        legacyId = LegacyId.EnglishAsr4allM,
        name = R.string.asr4all_m_name,
        ggmlFile = "asr4all-m-gptq-Q8_0.gguf",
        caps = Asr4AllCapabilities.copy(paramCount = 56),
    ),
    ModelDownloadable(
        id = "asr4all-l",
        legacyId = LegacyId.EnglishAsr4allL,
        name = R.string.asr4all_l_name,
        ggmlFile = "asr4all-l-gptq-Q8_0.gguf",
        checksum = "d2bdc25d4bda36a611dc17b4cb0c562a43ab2b2d4cc62294dcac6a8b9232e2d4",
        caps = Asr4AllCapabilities.copy(paramCount = 97),
        backwardsCompatFiles = listOf("asr4all-l-Q8_0.gguf", "asr4all-l-Q8_0-v2.gguf"),
    ),
    ModelDownloadable(
        id = "parakeet-tdt-110m",
        legacyId = LegacyId.EnglishParakeetTDT,
        name = R.string.parakeet_110m_name,
        ggmlFile = "parakeet-tdt_ctc-110m-Q8_0.gguf",
        checksum = "7dd44c74a331d788a4e5f8b16913b3feb29ced22cf5613aad0e0f6cd30516296",
        caps = ModelCapabilities(
            supportedLanguages = setOf(Language.English),
            supportsLanguageDetection = false,
            supportsPersonalDict = false,
            supportsStreaming = false,
            paramCount = 110,
        )
    ),
    ModelDownloadable(
        id = "parakeet-unified-en",
        legacyId = LegacyId.EnglishParakeetUnified,
        name = R.string.parakeet_unified_en_name,
        ggmlFile = "parakeet-unified-en-0.6b-Q4_K_M.gguf",
        checksum = "a8bf3de2b393bd14ead5a858c3748d5e3b07a20fdeabdd3b498fba4f463fa929",
        caps = ModelCapabilities(
            supportedLanguages = setOf(Language.English),
            supportsLanguageDetection = false,
            supportsPersonalDict = false,
            supportsStreaming = false, // technically true but no chance it runs fast enough
            paramCount = 600,
            banishedToOther = true // should use nemotron instead
        )
    ),
    ModelDownloadable(
        id = "nemotron-en",
        legacyId = LegacyId.EnglishNemotron,
        name = R.string.nemotron_en_streaming_name,
        ggmlFile = "nemotron-speech-streaming-en-0.6b-Q4_K_M.gguf",
        checksum = "dc959ca31499b114e395c44eb4f0778968f20e5cfb03305a08a39925b2da8e1e",
        caps = ModelCapabilities(
            supportedLanguages = setOf(Language.English),
            supportsLanguageDetection = false,
            supportsPersonalDict = false,
            supportsStreaming = true,
            paramCount = 600,
        )
    ),
    ModelDownloadable(
        id = "moonshine-en",
        legacyId = LegacyId.EnglishMoonshine,
        name = R.string.moonshine_streaming_small_name,
        ggmlFile = "moonshine-streaming-small-Q8_0.gguf",
        checksum = "d03670f69629b649085d0f44a63d97668b4119117cc9611a4e4ad94341713dfc",
        caps = ModelCapabilities(
            supportedLanguages = setOf(Language.English),
            supportsLanguageDetection = false,
            supportsPersonalDict = false, // TODO: double check
            supportsStreaming = true,
            paramCount = 100,
            banishedToOther = true // seems to struggle streaming
        )
    ),

    // Multilingual
    ModelDownloadable(
        id = "whisper-multi-tiny",
        legacyId = LegacyId.MultiWhisperTiny,
        name = R.string.tiny_name,
        ggmlFile = "tiny_acft_q8_0.bin",
        checksum = "07aa4d514144deacf5ffec5cacb36c93dee272fda9e64ac33a801f8cd5cbd953",
        caps = WhisperMultiCapabilities.copy(paramCount = 39),
    ),
    ModelDownloadable(
        id = "whisper-multi-base",
        legacyId = LegacyId.MultiWhisperBase,
        name = R.string.base_name,
        ggmlFile = "base_acft_q8_0.bin",
        checksum = "e44f352c9aa2c3609dece20c733c4ad4a75c28cd9ab07d005383df55fa96efc4",
        caps = WhisperMultiCapabilities.copy(paramCount = 74),
    ),
    ModelDownloadable(
        id = "whisper-multi-small",
        legacyId = LegacyId.MultiWhisperSmall,
        name = R.string.small_name,
        ggmlFile = "small_acft_q8_0.bin",
        checksum = "15ef255465a6dc582ecf1ec651a4618c7ee2c18c05570bbe46493d248d465ac4",
        caps = WhisperMultiCapabilities.copy(paramCount = 244),
    ),
    Nemotron600M,

    // Spanish
    ModelDownloadable(
        id = "asr4all-m-spanish",
        name = R.string.asr4all_m_es_name,
        ggmlFile = "asr4all-m-es-gptq-Q8_0.gguf",
        checksum = "fee45412add85caa2f7a257c09cc30bf15e48e457ab28e0571d277548f0f6f2e",
        caps = Asr4AllCapabilities.copy(paramCount = 56, supportedLanguages = setOf(Language.Spanish)),
    ),
)

private val MODELS_BY_ID = ALL_MODELS.associateBy { it.id }

fun lookupModel(id: String): ModelLoader? = MODELS_BY_ID[id]
fun lookupModelLegacy(multilingual: Boolean, idx: Int): ModelLoader? = when {
    !multilingual && idx == LegacyId.EnglishNemotronMulti.idx -> Nemotron600M
    else -> ALL_MODELS.firstOrNull {
        it.legacyId.multilingual == multilingual && it.legacyId.idx == idx
    }
}

val DefaultModelsByLanguage = mapOf(
    Language.English to "asr4all-m",
    Language.Spanish to "asr4all-m-spanish",
    // could replace unrecommended languages with overrides here for japanese chinese korean etc
)