package org.futo.voiceinput.settings.pages

import android.content.Intent
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import org.futo.voiceinput.R
import org.futo.voiceinput.settings.ScreenTitle
import org.futo.voiceinput.settings.ScrollableList
import org.futo.voiceinput.theme.Typography


@Composable
fun CreditItem(name: String, thanksFor: String, link: String, license: String, copyright: String) {
    val uriHandler = LocalUriHandler.current

    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        shape = RoundedCornerShape(4.dp)
    ) {
        ClickableText(text = buildAnnotatedString {
            val fullString =
                "$name - $thanksFor\n$license - $copyright"

            addStyle(
                style = SpanStyle(
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    fontSize = Typography.bodyMedium.fontSize,
                    fontWeight = Typography.bodyMedium.fontWeight
                ),
                start = 0,
                end = fullString.length
            )


            val start = fullString.indexOf(name)
            val end = start + name.length

            addStyle(
                style = SpanStyle(
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    fontSize = Typography.titleLarge.fontSize,
                    fontWeight = Typography.titleLarge.fontWeight,
                    textDecoration = TextDecoration.Underline
                ),
                start = start,
                end = end
            )
            addStringAnnotation(
                tag = "URL",
                annotation = link,
                start = start,
                end = end
            )

            append(fullString)
        }, onClick = {
            uriHandler.openUri(link)
        }, modifier = Modifier.padding(8.dp), style = Typography.bodyLarge)
    }
}

@Composable
@Preview
fun CreditsScreen(openDependencies: () -> Unit = {}, navController: NavHostController = rememberNavController()) {
    ScrollableList {
        ScreenTitle(title = stringResource(id = R.string.credits_title), showBack = true, navController = navController)

        CreditItem(
            name = "OpenAI Whisper",
            thanksFor = stringResource(R.string.thanks_for_the_voice_recognition_model),
            link = "https://github.com/openai/whisper",
            license = "MIT",
            copyright = "Copyright (c) 2022 OpenAI"
        )

        CreditItem(
            name = "TensorFlow Lite",
            thanksFor = stringResource(R.string.thanks_for_the_machine_learning_inference_library),
            link = "https://mvnrepository.com/artifact/org.tensorflow/tensorflow-lite",
            license = "Apache-2.0",
            copyright = "Copyright (c) 2023 TensorFlow Authors"
        )

        CreditItem(
            name = "PocketFFT",
            thanksFor = stringResource(R.string.thanks_for_the_fft_library_used_to_convert_audio_to_model_input),
            link = "https://gitlab.mpcdf.mpg.de/mtr/pocketfft/-/blob/master/LICENSE.md",
            license = "BSD-3-Clause",
            copyright = "Copyright (c) 2010-2019 Max-Planck-Society"
        )

        CreditItem(
            name = "WebRTC",
            thanksFor = stringResource(R.string.thanks_for_the_voice_activity_detection_to_stop_recognition_on_silence),
            link = "https://webrtc.org/",
            license = "BSD-3-Clause",
            copyright = "Copyright (c) 2011, The WebRTC project authors"
        )

        CreditItem(
            name = "android-vad",
            thanksFor = stringResource(R.string.thanks_for_the_android_bindings_to_webrtc_voice_activity_detection),
            link = "https://github.com/gkonovalov/android-vad",
            license = "MIT",
            copyright = "Copyright (c) 2023 Georgiy Konovalov"
        )

        CreditItem(
            name = "OkHttp",
            thanksFor = stringResource(R.string.thanks_for_the_http_client_used_for_downloading_models),
            link = "https://square.github.io/okhttp/",
            license = "Apache-2.0",
            copyright = "Copyright (c) 2023 Square, Inc"
        )

        CreditItem(
            name = "Feather Icons",
            thanksFor = stringResource(R.string.thanks_for_icon),
            link = "https://github.com/feathericons/feather",
            license = "MIT",
            copyright = "Copyright (c) 2013-2017 Cole Bemis"
        )

        Button(
            onClick = openDependencies, modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
        ) {
            Text(stringResource(R.string.view_other_dependencies))
        }
        Text(
            stringResource(R.string.name_legal),
            style = Typography.bodyMedium,
            modifier = Modifier.padding(8.dp)
        )
    }
}


@Composable
fun DependenciesScreen(navController: NavHostController = rememberNavController()) {
    Column {
        ScreenTitle(title = stringResource(id = R.string.dependencies), showBack = true, navController = navController)
        AndroidView(factory = {
            WebView(it).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )

                // Open all links in external browser
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(
                        view: WebView?,
                        request: WebResourceRequest?
                    ): Boolean {
                        val intent = Intent(Intent.ACTION_VIEW, request!!.url)
                        view!!.context.startActivity(intent)
                        return true
                    }
                }
            }
        }, update = {
            it.loadUrl("file:///android_asset/license-list.html")
        })
    }
}