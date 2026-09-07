package org.futo.voiceinput.shared.types

import android.content.Context
import androidx.annotation.StringRes
import org.futo.voiceinput.shared.ggml.WhisperGGML
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

data class ModelCapabilities(
    val supportedLanguages: Set<Language>,
    val supportsLanguageDetection: Boolean,
    val supportsPersonalDict: Boolean,
    val supportsStreaming: Boolean,
    val isWhisper: Boolean = false,
)

// Maybe add `val languages: Set<Language>`
interface ModelLoader {
    @get:StringRes
    val name: Int
    val caps: ModelCapabilities

    fun exists(context: Context): Boolean
    fun getRequiredDownloadList(context: Context): List<String>

    fun loadGGML(context: Context): WhisperGGML

    fun key(context: Context): Any
}

internal class ModelBuiltInAsset(
    override val name: Int,
    val ggmlFile: String,
    override val caps: ModelCapabilities,
) : ModelLoader {
    override fun exists(context: Context): Boolean {
        return true
    }

    override fun getRequiredDownloadList(context: Context): List<String> {
        return listOf()
    }

    override fun loadGGML(context: Context): WhisperGGML {
        val file = File(context.codeCacheDir, "_FUTOASSET_$ggmlFile")

        if(file.isFile) file.delete()

        context.assets.open(ggmlFile).use { fis ->
            file.outputStream().use { fos ->
                fis.copyTo(fos)
            }
        }
        return WhisperGGML(file)
    }

    override fun key(context: Context): Any {
        return "BuiltIn$ggmlFile"
    }
}

@Throws(IOException::class)
private fun tryOpenDownloadedModel(file: File): MappedByteBuffer {
    val fis = file.inputStream()
    val channel = fis.channel

    return channel.map(
        FileChannel.MapMode.READ_ONLY,
        0, channel.size()
    ).load()
}

@Throws(IOException::class)
private fun Context.tryOpenDownloadedModel(pathStr: String): MappedByteBuffer {
    return tryOpenDownloadedModel(File(filesDir, pathStr))
}

internal class ModelDownloadable(
    override val name: Int,
    val ggmlFile: String,
    val checksum: String,
    override val caps: ModelCapabilities,
) : ModelLoader {

    override fun exists(context: Context): Boolean {
        return getRequiredDownloadList(context).isEmpty()
    }

    override fun getRequiredDownloadList(context: Context): List<String> {
        return listOf(ggmlFile).filter {
            !File(context.filesDir, it).exists()
        }
    }

    override fun loadGGML(context: Context): WhisperGGML {
        val file = File(context.filesDir, ggmlFile)
        if(!file.isFile) throw FileNotFoundException()
        return WhisperGGML(file)
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

    override fun getRequiredDownloadList(context: Context): List<String> {
        return listOf()
    }

    override fun loadGGML(context: Context): WhisperGGML {
        if(!file.isFile) throw FileNotFoundException()
        return WhisperGGML(file)
    }

    override fun key(context: Context): Any {
        return "File${file.absolutePath}"
    }
}
