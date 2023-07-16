package org.futo.voiceinput.payments

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import org.futo.voiceinput.BuildConfig

class BillingManager(val context: Context, val coroutineScope: CoroutineScope) {
    private var billings: ArrayList<BillingImpl> = ArrayList()

    init {
        if(BuildConfig.FLAVOR == "playStore" || BuildConfig.FLAVOR == "dev") {
            billings.add(PlayBilling(context, coroutineScope))
        }

        if(BuildConfig.FLAVOR == "fDroid" || BuildConfig.FLAVOR == "dev") {
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