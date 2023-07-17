package org.futo.voiceinput.payments

import android.content.Context
import kotlinx.coroutines.CoroutineScope

class BillingManager(val context: Context, val coroutineScope: CoroutineScope) {
    private var billings: ArrayList<BillingImpl> = ArrayList()

    init {
        if(PlayBilling.isAllowed()) {
            billings.add(PlayBilling(context, coroutineScope))
        }

        if(PayPalBilling.isAllowed()) {
            billings.add(PayPalBilling(context))
        }
    }

    fun checkAlreadyOwnsProduct() {
        billings.forEach { it.checkAlreadyOwnsProduct() }
    }

    fun startConnection(onReady: (BillingImpl) -> Unit) {
        billings.forEach { b -> b.startConnection{ onReady(b) } }
    }

    fun onResume() {
        billings.forEach { it.onResume() }
    }

    fun getBillings(): List<BillingImpl> {
        return this.billings
    }
}