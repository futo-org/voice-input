package org.futo.voiceinput.settings

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch
import org.futo.voiceinput.IS_ALREADY_PAID
import org.futo.voiceinput.IS_PAYMENT_PENDING
import org.futo.voiceinput.dataStore
import org.futo.voiceinput.startAppActivity
import org.futo.voiceinput.ui.theme.WhisperVoiceInputTheme

class PaymentCompleteActivity : ComponentActivity() {
    private fun updateContent() {
        setContent {
            WhisperVoiceInputTheme {
                // A surface container using the 'background' color from the theme
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    PaymentThankYouScreen(onExit = {
                        startAppActivity(SettingsActivity::class.java)
                        finish()
                    })
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        lifecycleScope.launch {
            dataStore.edit { it[IS_ALREADY_PAID] = true }
            dataStore.edit { it[IS_PAYMENT_PENDING] = false }

            repeatOnLifecycle(Lifecycle.State.STARTED) {
                updateContent()
            }
        }
    }

    override fun onStart() {
        super.onStart()
    }

    override fun onResume() {
        super.onResume()
    }

    override fun onRestart() {
        super.onRestart()
    }
}
