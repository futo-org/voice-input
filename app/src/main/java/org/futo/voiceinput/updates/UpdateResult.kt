package org.futo.voiceinput.updates

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import org.futo.voiceinput.BuildConfig

@Serializable
data class UpdateResult(
    val nextVersion: Int,
    val apkUrl: String
) {
    fun isNewer(): Boolean {
        return nextVersion > currentVersion()
    }

    companion object {
        fun currentVersion(): Int {
            return BuildConfig.VERSION_CODE
        }

        fun fromString(value: String): UpdateResult? {
            if(value.isEmpty()) {
                return null
            }

            try {
                return Json.decodeFromString<UpdateResult>(value)
            } catch(e: SerializationException) {
                return null
            } catch(e: IllegalArgumentException) {
                return null
            }
        }
    }
}
