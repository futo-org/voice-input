package org.futo.voiceinput.settings.pages

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import org.futo.voiceinput.R
import org.futo.voiceinput.settings.ScreenTitle
import org.futo.voiceinput.settings.THEME_KEY
import org.futo.voiceinput.settings.useDataStore
import org.futo.voiceinput.theme.selector.ThemePicker

@Preview
@Composable
fun ThemeScreen(navController: NavHostController = rememberNavController()) {
    val (theme, setTheme) = useDataStore(THEME_KEY)

    val context = LocalContext.current
    Column(modifier = Modifier.fillMaxSize()) {
        ScreenTitle(stringResource(R.string.theme), showBack = true, navController)
        ThemePicker {
            setTheme(it.key)
        }
    }
}