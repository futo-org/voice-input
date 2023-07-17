package org.futo.voiceinput.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.tooling.preview.Preview
import org.futo.voiceinput.LAST_UPDATE_CHECK_RESULT
import org.futo.voiceinput.openURI
import org.futo.voiceinput.updates.UpdateResult

@Composable
@Preview
fun ConditionalUpdate() {
    val (updateInfo, _) = useDataStore(key = LAST_UPDATE_CHECK_RESULT, default = "")

    val lastUpdateResult = if(!LocalInspectionMode.current){
        UpdateResult.fromString(updateInfo)
    } else {
        UpdateResult(123, "abc")
    }

    val context = LocalContext.current
    if(lastUpdateResult != null && lastUpdateResult.isNewer()) {
        SettingItem(
            title = "Update Available",
            subtitle = "Tap to download APK for v${lastUpdateResult.nextVersion}",
            onClick = {
                context.openURI(lastUpdateResult.apkUrl)
            }
        ) {
            Icon(Icons.Default.ArrowForward, contentDescription = "Go")
        }

    }
}
