package org.futo.voiceinput.shared.whisper

import android.content.Context
import android.util.Log
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.futo.voiceinput.shared.ggml.WhisperGGML
import org.futo.voiceinput.shared.types.ModelLoader

class ModelManager(
    val context: Context
) {
    private val loadedModels: HashMap<Any, WhisperGGML> = hashMapOf()
    private val cleanupMutex = Mutex()

    fun obtainModel(model: ModelLoader): WhisperGGML {
        //if(users.isEmpty())
        //    throw IllegalStateException("There must be a user during obtainModel")

        return loadedModels.getOrPut(model.key(context)) {
            model.loadGGML(context)
        }
    }

    fun cancelAll() {
        loadedModels.forEach {
            it.value.cancel()
        }
    }

    suspend fun cleanUp() {
        cleanupMutex.withLock {
            if(users.isNotEmpty()) return

            if (loadedModels.isNotEmpty()) {
                Log.d("ModelManager", "cleanUp() for ${loadedModels.size} models")
            }

            for (model in loadedModels.entries) {
                model.value.cancel()
                model.value.close()
            }

            loadedModels.clear()
        }
    }

    private val users = mutableSetOf<Any>()
    fun startUse(sentinel: Any) = users.add(sentinel).also { Exception("startuse").printStackTrace() }
    fun finishUse(sentinel: Any) = users.remove(sentinel).also { Exception("enduse").printStackTrace() }
}
