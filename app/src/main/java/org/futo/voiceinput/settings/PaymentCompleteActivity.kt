package org.futo.voiceinput.settings

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch
import org.futo.voiceinput.IS_ALREADY_PAID
import org.futo.voiceinput.IS_PAYMENT_PENDING
import org.futo.voiceinput.ValueFromSettings
import org.futo.voiceinput.dataStore
import org.futo.voiceinput.payments.StatePayment
import org.futo.voiceinput.startAppActivity
import org.futo.voiceinput.ui.theme.WhisperVoiceInputTheme

class PaymentCompleteActivity : ComponentActivity() {
    private fun updateContent() {
        setContent {
            WhisperVoiceInputTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    PaymentThankYouScreen(onExit = {
                        startAppActivity(SettingsActivity::class.java, clearTop = true)
                        finish()
                    })
                }
            }
        }
    }

    private fun onPaid() {
        lifecycleScope.launch {
            dataStore.edit {
                it[IS_ALREADY_PAID] = true
                it[IS_PAYMENT_PENDING] = false
            }

            repeatOnLifecycle(Lifecycle.State.STARTED) {
                updateContent()
            }
        }
    }

    private fun onInvalidKey() {
        lifecycleScope.launch {
            if(ValueFromSettings(IS_ALREADY_PAID, false).get(this@PaymentCompleteActivity)) {
                finish()
            } else {
                setContent {
                    WhisperVoiceInputTheme {
                        Surface(
                            modifier = Modifier.fillMaxSize(),
                            color = MaterialTheme.colorScheme.background
                        ) {
                            Text("Invalid license format", modifier = Modifier.padding(8.dp))
                        }
                    }
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val targetData = intent.dataString
        if(targetData?.startsWith("futo-voice-input://license/") == true) {
            if(StatePayment.instance.setPaymentLicenseUrl(targetData)) {
                onPaid()
            } else {
                onInvalidKey()
            }
        } else {
            Log.e("PaymentCompleteActivity", "futo-voice-input launched with invalid targetData $targetData")
            onInvalidKey()
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
