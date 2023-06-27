package org.futo.voiceinput.settings

import android.content.Intent
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import org.futo.voiceinput.ui.theme.Sky200
import org.futo.voiceinput.ui.theme.Typography


@Composable
fun CreditItem(name: String, thanksFor: String, link: String, license: String, copyright: String) {
    val uriHandler = LocalUriHandler.current

    Surface(color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.padding(8.dp), shape = RoundedCornerShape(4.dp)) {
        ClickableText(text = buildAnnotatedString {
            val fullString =
                "$name - Thanks for $thanksFor. $name is licensed under $license. $copyright."

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
                    color = Sky200,
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
fun CreditsScreen(openDependencies: () -> Unit = {}) {
    SettingsScreen("Credits") {
        SettingList {
            LazyColumn {
                item {
                    CreditItem(
                        name = "OpenAI Whisper",
                        thanksFor = "the voice recognition model",
                        link = "https://github.com/openai/whisper",
                        license = "MIT",
                        copyright = "Copyright (c) 2022 OpenAI"
                    )

                    CreditItem(
                        name = "TensorFlow Lite",
                        thanksFor = "machine learning inference",
                        link = "https://mvnrepository.com/artifact/org.tensorflow/tensorflow-lite",
                        license = "Apache-2.0",
                        copyright = "Copyright (c) 2023 TensorFlow Authors"
                    )

                    CreditItem(
                        name = "PocketFFT",
                        thanksFor = "FFT to convert audio to model input",
                        link = "https://gitlab.mpcdf.mpg.de/mtr/pocketfft/-/blob/master/LICENSE.md",
                        license = "BSD-3-Clause",
                        copyright = "Copyright (c) 2010-2019 Max-Planck-Society"
                    )

                    CreditItem(
                        name = "WebRTC",
                        thanksFor = "voice activity detection to stop recognition on silence",
                        link = "https://webrtc.org/",
                        license = "BSD-3-Clause",
                        copyright = "Copyright (c) 2011, The WebRTC project authors"
                    )

                    CreditItem(
                        name = "android-vad",
                        thanksFor = "Android bindings to WebRTC voice activity detection",
                        link = "https://github.com/gkonovalov/android-vad",
                        license = "MIT",
                        copyright = "Copyright (c) 2023 Georgiy Konovalov"
                    )

                    CreditItem(
                        name = "OkHttp",
                        thanksFor = "HTTP client, used for downloading models",
                        link = "https://square.github.io/okhttp/",
                        license = "Apache-2.0",
                        copyright = "Copyright (c) 2023 Square, Inc"
                    )

                    Button(
                        onClick = openDependencies, modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp)
                    ) {
                        Text("View Other Dependencies")
                    }
                }

                item {
                    Text("The authors, contributors or copyright holders listed above are not affiliated with this product and do not endorse or promote this product. Reference to the authors, contributors or copyright holders is solely for attribution purposes. Mention of their names does not imply approval or endorsement.", style = Typography.bodyMedium, modifier = Modifier.padding(8.dp))
                }
            }
        }
    }
}


@Composable
fun DependenciesScreen() {
    SettingsScreen("Dependencies") {
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