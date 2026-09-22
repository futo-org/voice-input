package org.futo.voiceinput.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import org.futo.voiceinput.R
import org.futo.voiceinput.downloader.DownloadActivity
import org.futo.voiceinput.loadCurrentModelList
import org.futo.voiceinput.shared.types.ModelPresence
import org.futo.voiceinput.startModelDownloadActivity

@Composable
fun ConditionalModelUpdate() {
    val context = LocalContext.current

    val models = remember(DownloadActivity.downloadsCompleted.intValue) {
        context.loadCurrentModelList()
            .map { it to it.getPresence(context) }
            .filter { it.second != ModelPresence.Exists }
    }
    if(models.isNotEmpty()) {
        NavigationItem(
            title = stringResource(
                if(models.any { it.second == ModelPresence.Missing})
                    R.string.voice_input_window_indicator_model_requires_download
                else R.string.voice_input_window_indicator_model_update_available
            ),
            subtitle = models.map { stringResource(it.first.name) }.joinToString(", "),
            style = NavigationItemStyle.Misc,
            navigate = {
                context.startModelDownloadActivity(models.map { it.first })
            }
        )
    }
}