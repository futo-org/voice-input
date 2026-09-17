package org.futo.voiceinput.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import org.futo.voiceinput.R
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

    // App update is prioritized over model update because it's more important
    if(lastUpdateResult != null && lastUpdateResult.isNewer()) {
        NavigationItem(
            title = stringResource(R.string.update_available),
            subtitle = "${UpdateResult.currentVersionString()} -> ${lastUpdateResult.nextVersionString}",
            style = NavigationItemStyle.Misc,
            navigate = {
                context.openURI(lastUpdateResult.apkUrl)
            }
        )
    } else {
        ConditionalModelUpdate()
    }
}
