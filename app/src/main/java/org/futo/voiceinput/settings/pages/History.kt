package org.futo.voiceinput.settings.pages

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.text.format.DateUtils
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import org.futo.voiceinput.R
import org.futo.voiceinput.settings.NavigationItem
import org.futo.voiceinput.settings.NavigationItemStyle
import org.futo.voiceinput.settings.ScreenTitle
import org.futo.voiceinput.settings.ScrollableList
import org.futo.voiceinput.settings.SettingsViewModel
import org.futo.voiceinput.settings.TRANSCRIPTION_HISTORY
import org.futo.voiceinput.settings.useDataStore
import org.futo.voiceinput.theme.Typography
import org.json.JSONArray

private data class HistoryEntry(val time: Long, val text: String)

private fun parseHistory(json: String): List<HistoryEntry> {
    if (json.isEmpty()) return emptyList()

    return try {
        val array = JSONArray(json)
        (0 until array.length()).mapNotNull { i ->
            val entry = array.optJSONObject(i) ?: return@mapNotNull null
            val text = entry.optString("text")
            if (text.isEmpty()) null else HistoryEntry(entry.optLong("time"), text)
        }
    } catch (e: Exception) {
        emptyList()
    }
}

@Composable
private fun HistoryRow(entry: HistoryEntry, onCopy: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onCopy)
            .padding(16.dp, 10.dp)
    ) {
        Text(entry.text, style = Typography.bodyMedium)
        if (entry.time > 0) {
            Text(
                DateUtils.getRelativeTimeSpanString(entry.time).toString(),
                style = Typography.labelSmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
            )
        }
    }
}

@Composable
@Preview
fun HistoryScreen(
    settingsViewModel: SettingsViewModel = viewModel(),
    navController: NavHostController = rememberNavController()
) {
    val context = LocalContext.current
    val (historyJson, setHistoryJson) = useDataStore(TRANSCRIPTION_HISTORY)
    val entries = remember(historyJson) { parseHistory(historyJson) }

    ScrollableList {
        ScreenTitle(
            title = stringResource(R.string.transcription_history),
            showBack = true,
            navController = navController
        )

        if (entries.isEmpty()) {
            Text(
                stringResource(R.string.transcription_history_empty),
                style = Typography.bodyMedium,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            )
        } else {
            Text(
                stringResource(R.string.transcription_history_hint),
                style = Typography.labelSmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp, 4.dp)
            )

            entries.forEach { entry ->
                HistoryRow(entry) {
                    val clipboard =
                        context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(
                        ClipData.newPlainText(context.getString(R.string.app_name), entry.text)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            NavigationItem(
                title = stringResource(R.string.clear_transcription_history),
                style = NavigationItemStyle.Misc,
                navigate = { setHistoryJson("") }
            )
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}
