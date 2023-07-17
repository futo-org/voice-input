package org.futo.voiceinput.payments

import android.content.Context
import android.content.Intent
import android.net.Uri

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
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com"))
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    override fun supportsCheckingIfAlreadyOwnsProduct(): Boolean {
        return false
    }

    override fun getName(): String {
        return "PayPal"
    }
}