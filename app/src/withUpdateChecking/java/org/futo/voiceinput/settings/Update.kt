package org.futo.voiceinput.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.tooling.preview.Preview
import org.futo.voiceinput.openURI
import org.futo.voiceinput.updates.UpdateResult

@Composable
@Preview
fun ConditionalUpdate() {
    val (updateInfo, _) = useDataStore(LAST_UPDATE_CHECK_RESULT)

    val lastUpdateResult = if(!LocalInspectionMode.current){
        UpdateResult.fromString(updateInfo)
    } else {
        UpdateResult(123, "abc", "1.2.3")
    }

    val context = LocalContext.current
    if(lastUpdateResult != null && lastUpdateResult.isNewer()) {
        NavigationItem(
            title = "Update Available",
            subtitle = "${UpdateResult.currentVersionString()} -> ${lastUpdateResult.nextVersionString}",
            style = NavigationItemStyle.Misc,
            navigate = {
                context.openURI(lastUpdateResult.apkUrl)
            }
        )
    }
}
