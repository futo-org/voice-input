package org.futo.voiceinput.payments

import android.content.Context
import androidx.appcompat.app.AppCompatActivity

class BillingActivity : AppCompatActivity() {

}

class StatePayment {
    public fun setPaymentLicenseUrl(license: String): Boolean {
        // On the Play Store version, if someone opens a license, let's just assume it's valid
        // TODO?: Verify
        return true
    }

    companion object {
        val instance = StatePayment()
    }
}

class PayPalBilling(private val context: Context) : BillingImpl {
    companion object {
        fun isAllowed(): Boolean {
            return false
        }
    }

    override fun checkAlreadyOwnsProduct() {
        throw RuntimeException("PayPalBilling is not available on this build")
    }

    override fun startConnection(onReady: () -> Unit) {
        throw RuntimeException("PayPalBilling is not available on this build")
    }

    override fun onResume() {
        throw RuntimeException("PayPalBilling is not available on this build")
    }

    override fun launchBillingFlow() {
        throw RuntimeException("PayPalBilling is not available on this build")
    }

    override fun supportsCheckingIfAlreadyOwnsProduct(): Boolean {
        throw RuntimeException("PayPalBilling is not available on this build")
    }

    override fun getName(): String {
        throw RuntimeException("PayPalBilling is not available on this build")
    }
}