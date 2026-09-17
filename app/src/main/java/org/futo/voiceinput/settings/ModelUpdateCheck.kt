package org.futo.voiceinput.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import org.futo.voiceinput.R
import org.futo.voiceinput.loadCurrentModelList
import org.futo.voiceinput.shared.types.ModelPresence
import org.futo.voiceinput.startModelDownloadActivity

@Composable
fun ConditionalModelUpdate() {
    val context = LocalContext.current

    val models = remember {
        context.loadCurrentModelList()
            .also { println("model list = ${it.joinToString { context.getString(it.name) }}") }
            .map { it to it.getPresence(context) }
            .also { println("model presence = ${it.joinToString { context.getString(it.first.name) + " " + it.second.name }}") }
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