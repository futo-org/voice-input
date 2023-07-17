package org.futo.voiceinput.payments

import android.content.Context
import org.futo.voiceinput.openURI

class PayPalBilling(val context: Context) : BillingImpl {
    companion object {
        fun isAllowed(): Boolean {
            return true
        }
    }

    override fun checkAlreadyOwnsProduct() {
    }

    override fun startConnection(onReady: () -> Unit) {
    }

    override fun onResume() {
    }

    override fun launchBillingFlow() {
        context.openURI("https://example.com", true) // TODO: Link to actual PayPal page
    }

    override fun supportsCheckingIfAlreadyOwnsProduct(): Boolean {
        return false
    }

    override fun getName(): String {
        return "PayPal"
    }
}