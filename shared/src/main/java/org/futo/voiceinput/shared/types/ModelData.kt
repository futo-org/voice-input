package org.futo.voiceinput.shared.types

import android.content.Context
import androidx.annotation.StringRes
import org.futo.voiceinput.shared.ggml.InvalidModelException
import org.futo.voiceinput.shared.ggml.WhisperGGML
import java.io.File
import java.io.FileNotFoundException

data class ModelCapabilities(
    val supportedLanguages: Set<Language>,
    val supportsLanguageDetection: Boolean,
    val supportsPersonalDict: Boolean,
    val supportsStreaming: Boolean,
    val isWhisper: Boolean = false,
)

enum class ModelPresence {
    /// Model exists and can run
    Exists,

    /// An upgrade is available but is optional
    UpgradeAvailable,

    /// Required files are missing (or incompatible upgrade)
    Missing
}

interface ModelLoader {
    @get:StringRes
    val name: Int
    val caps: ModelCapabilities

    /// Returns true if the files necessary to execute the model are present
    fun exists(context: Context): Boolean

    /// Returns list of files missing to execute the model. If `forUpgrade` is true, then it returns
    /// the missing upgrade files (in the case of backwards compatible files being present)
    fun getRequiredDownloadList(context: Context, forUpgrade: Boolean = false): List<String>

    /// Returns list of files to clean up after an upgrade has been completed.
    fun getCleanupListAfterUpgrade(context: Context): List<String> = emptyList()

    fun loadGGML(context: Context): WhisperGGML

    fun key(context: Context): Any

    fun getPresence(context: Context): ModelPresence =
        when {
            getRequiredDownloadList(context, false).isNotEmpty() -> ModelPresence.Missing
            getRequiredDownloadList(context, true).isNotEmpty() -> ModelPresence.UpgradeAvailable
            else -> ModelPresence.Exists
        }
}

internal class ModelBuiltInAsset(
    override val name: Int,
    val ggmlFile: String,
    override val caps: ModelCapabilities,
) : ModelLoader {
    private var refreshed = false

    override fun exists(context: Context): Boolean {
        return true
    }

    override fun getRequiredDownloadList(context: Context, forUpgrade: Boolean): List<String> {
        return emptyList()
    }

    override fun loadGGML(context: Context): WhisperGGML {
        val file = File(context.codeCacheDir, "_FUTOASSET_$ggmlFile")

        if(!refreshed) {
            refreshed = true
            if (file.isFile) file.delete()
            context.assets.open(ggmlFile).use { fis ->
                file.outputStream().use { fos ->
                    fis.copyTo(fos)
                }
            }
        }

        return WhisperGGML(file)
    }

    override fun key(context: Context): Any {
        return "BuiltIn$ggmlFile"
    }

    override fun getPresence(context: Context): ModelPresence = ModelPresence.Exists
}

private fun Context.ggmlFile(fname: String) = File(filesDir, fname)

internal class ModelDownloadable(
    override val name: Int,
    val ggmlFile: String,
    val checksum: String,
    override val caps: ModelCapabilities,
    /// Old model filenames that can still run (deleted after upgrade)
    val backwardsCompatFiles: List<String> = emptyList(),
    /// Old model filenames that can no longer run (deleted after upgrade)
    val otherCleanupFiles: List<String> = emptyList(),
) : ModelLoader {
    private fun matchingFile(context: Context): File? =
        if(context.ggmlFile(ggmlFile).isFile)
            context.ggmlFile(ggmlFile)
        else
            backwardsCompatFiles.firstNotNullOfOrNull { compatFile ->
                context.ggmlFile(compatFile).let { if(it.exists()) it else null }
            }

    override fun exists(context: Context): Boolean = getRequiredDownloadList(context).isEmpty()

    override fun getRequiredDownloadList(context: Context, forUpgrade: Boolean): List<String> = when {
        forUpgrade && !context.ggmlFile(ggmlFile).isFile -> listOf(ggmlFile)
        matchingFile(context) == null -> listOf(ggmlFile)
        else -> emptyList()
    }

    override fun getCleanupListAfterUpgrade(context: Context): List<String> =
        backwardsCompatFiles

    override fun loadGGML(context: Context): WhisperGGML {
        val file = matchingFile(context) ?: throw FileNotFoundException()
        return try {
            WhisperGGML(file)
        } catch(e: InvalidModelException) {
            if(e.fileError) {
                // The file is most likely invalid. Delete it.
                file.delete()
            }

            throw e
        }
    }

    override fun key(context: Context): Any {
        return "Downloadable$ggmlFile$checksum"
    }
}

public class ModelFileFile(
    override val name: Int,
    val file: File,
    override val caps: ModelCapabilities,
) : ModelLoader {

    override fun exists(context: Context): Boolean {
        return file.exists()
    }

    override fun getRequiredDownloadList(context: Context, forUpgrade: Boolean): List<String> {
        return emptyList()
    }

    override fun loadGGML(context: Context): WhisperGGML {
        if(!file.isFile) throw FileNotFoundException()
        return WhisperGGML(file)
    }

    override fun key(context: Context): Any {
        return "File${file.absolutePath}"
    }

    override fun getPresence(context: Context): ModelPresence = ModelPresence.Exists
}
