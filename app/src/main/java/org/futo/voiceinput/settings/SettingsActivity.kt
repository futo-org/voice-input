package org.futo.voiceinput.settings

import android.Manifest
import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch
import org.futo.voiceinput.payments.BillingManager
import org.futo.voiceinput.ui.theme.WhisperVoiceInputTheme
import org.futo.voiceinput.updates.scheduleUpdateCheckingJob

class SettingsActivity : ComponentActivity() {
    internal lateinit var billing: BillingManager
    private fun updateContent() {
        setContent {
            WhisperVoiceInputTheme {
                // A surface container using the 'background' color from the theme
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    SetupOrMain(billing = billing)
                }
            }
        }
    }

    private val permission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            viewModel.onResume()
        }


    private val voiceIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
    private val runVoiceIntent =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            viewModel.onIntentResult(
                when (it.resultCode) {
                    RESULT_OK -> {
                        val result =
                            it.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                        if (result.isNullOrEmpty()) {
                            "Intent result is null or empty"
                        } else {
                            result[0]
                        }
                    }

                    RESULT_CANCELED -> "Intent was cancelled"
                    else -> "Unknown intent result"
                }
            )
        }

    internal fun requestPermission() {
        permission.launch(Manifest.permission.RECORD_AUDIO)
    }

    internal fun launchVoiceIntent() {
        runVoiceIntent.launch(voiceIntent)
    }

    private lateinit var viewModel: SettingsViewModel
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        billing = BillingManager(this, lifecycleScope)

        viewModel = viewModels<SettingsViewModel>().value

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect {
                    updateContent()
                }
            }
        }

        scheduleUpdateCheckingJob(applicationContext)
    }

    override fun onStart() {
        super.onStart()

        billing.startConnection {
            it.checkAlreadyOwnsProduct()
        }
    }

    override fun onResume() {
        super.onResume()

        billing.onResume()
        viewModel.onResume()
    }

    override fun onRestart() {
        super.onRestart()

        billing.onResume()
        viewModel.onResume()
    }
}
