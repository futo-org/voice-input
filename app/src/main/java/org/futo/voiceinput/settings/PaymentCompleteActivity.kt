package org.futo.voiceinput.settings

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
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
import org.futo.voiceinput.R
import org.futo.voiceinput.payments.StatePayment
import org.futo.voiceinput.settings.pages.PaymentThankYouScreen
import org.futo.voiceinput.settings.pages.ShareFeedbackOption
import org.futo.voiceinput.startAppActivity
import org.futo.voiceinput.theme.UixThemeAuto

class PaymentCompleteActivity : ComponentActivity() {
    private fun updateContent() {
        setContent {
            UixThemeAuto {
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

    private fun onPaid(license: String) {
        lifecycleScope.launch {
            dataStore.edit {
                it[IS_ALREADY_PAID.key] = true
                it[IS_PAYMENT_PENDING.key] = false
                it[EXT_LICENSE_KEY.key] = license
            }

            repeatOnLifecycle(Lifecycle.State.STARTED) {
                updateContent()
            }
        }
    }

    private fun onInvalidKey() {
        lifecycleScope.launch {
            if(applicationContext.getSetting(IS_ALREADY_PAID)) {
                finish()
            } else {
                setContent {
                    UixThemeAuto {
                        Surface(
                            modifier = Modifier.fillMaxSize(),
                            color = MaterialTheme.colorScheme.background
                        ) {
                            Column {
                                Text(
                                    getString(R.string.license_check_failed),
                                    modifier = Modifier.padding(8.dp)
                                )
                                ShareFeedbackOption()
                            }
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
                onPaid(targetData)
            } else {
                onInvalidKey()
            }
        } else {
            Log.e("PaymentCompleteActivity", "futo-voice-input launched with invalid targetData $targetData")
            finish()
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
