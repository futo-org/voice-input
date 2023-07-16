package org.futo.voiceinput.payments

interface BillingImpl {
    fun checkAlreadyOwnsProduct()
    fun startConnection(onReady: () -> Unit = { })
    fun onResume()
    fun launchBillingFlow()

    fun getName(): String
}