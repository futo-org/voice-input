package org.futo.voiceinput.updates

import kotlinx.serialization.Serializable
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
    }
}
