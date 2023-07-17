package org.futo.voiceinput.payments

import android.content.Context
import kotlinx.coroutines.CoroutineScope

class PlayBilling(private val context: Context, private val coroutine: CoroutineScope) : BillingImpl {
    companion object {
        fun isAllowed(): Boolean {
            return false
        }
    }

    override fun checkAlreadyOwnsProduct() {
        throw RuntimeException("PlayBilling is not available on this build")
    }

    override fun startConnection(onReady: () -> Unit) {
        throw RuntimeException("PlayBilling is not available on this build")
    }

    override fun onResume() {
        throw RuntimeException("PlayBilling is not available on this build")
    }

    override fun launchBillingFlow() {
        throw RuntimeException("PlayBilling is not available on this build")
    }

    override fun supportsCheckingIfAlreadyOwnsProduct(): Boolean {
        throw RuntimeException("PlayBilling is not available on this build")
    }

    override fun getName(): String {
        throw RuntimeException("PlayBilling is not available on this build")
    }

}