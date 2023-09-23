package org.futo.voiceinput.payments

import android.content.Context
import android.util.Log
import com.futo.futopay.PaymentState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.futo.voiceinput.BuildConfig
import org.futo.voiceinput.EXT_LICENSE_KEY
import org.futo.voiceinput.EXT_PENDING_PURCHASE_ID
import org.futo.voiceinput.EXT_PENDING_PURCHASE_LAST_CHECK
import org.futo.voiceinput.IS_ALREADY_PAID
import org.futo.voiceinput.IS_PAYMENT_PENDING
import org.futo.voiceinput.ValueFromSettings
import org.futo.voiceinput.startAppActivity

@Suppress("KotlinConstantConditions")
const val TEST_MODE: Boolean = BuildConfig.FLAVOR == "dev"

const val PROD_KEY = "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA3JuRj4dv1mzB9mnTrGXdM/DlbDbNkrKvyEvR27Gy9ZEb3p3WhNk/BMPkA8L0ebrhq55N1o0IgF0qi/vFE0wnHuFcAIUIZEz6ZGLCgDaILursXpfKFVdj+ZidXCFrFgNW232EXhhXYF8pvSyLtEsjaYas2C/X68wL7nLt9Iw/0YC3ZmyislaV3BCdmSnZapdzmoRRHNV1hvhhBdF35pt9Mn0Tv3KP9/0+bM5CCv7kK2LI7NFmsSjXrTKZLDol1cOED9IC6SxUh32Dl67pyspdbMrZNa5GRWRuV+IM/w4FaE8W5aa45znvScJmngwzAq5W/8TsAlEDSbd5zQIvFQok0wIDAQAB"
const val TEST_KEY = "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA5ljP3KJGUSp+G7uEwzDJwjlqxtjDqTbm20+2TDE293yKjQ8ZlUDcjrtk763PTIylzQUkTpAY/A+n6Uf6JRuIWU9vVrxZpHQ+EVrUxTQaF8kL7Yb+pTFsnCJf/rwuxApQ2mYmoCrxKN0DumSGzm+2oE/ZtIWn4orLzHcTOaDoP/Kls2d0a2h3659P1Ycl49jwydFoVOLx8hb7tZX7VNR+DjjJV3B65S+mjvE1F3CjyColM9IRYPRUASc2VVra2DEhe64a+JmYJcM4tpu0oVuQqIuto3aqTrgYTpzFjJD3Nyo+TXJozfcnQRXL0/DCGCdhopx0GYeUc7AvEJoLXDrP4wIDAQAB"

class StatePayment : PaymentState(if(TEST_MODE) TEST_KEY else PROD_KEY) {
    override val isTesting: Boolean
        get() = TEST_MODE

    override fun savePaymentKey(licenseKey: String, licenseActivation: String) {
    }

    override fun getPaymentKey(): Pair<String, String> {
        return Pair("", "")
    }

    companion object {
        private var _instance : StatePayment? = null;
        val instance : StatePayment
            get(){
                if(_instance == null)
                    _instance = StatePayment();
                return _instance!!;
            };
    }
}

const val pollPeriod: Long = 60L * 60L * 24L
class PayPalBilling(val context: Context, private val coroutineScope: CoroutineScope) : BillingImpl {
    companion object {
        fun isAllowed(): Boolean {
            return true
        }

        suspend fun pollPendingStatus(context: Context) {
            val lastCheck = ValueFromSettings(EXT_PENDING_PURCHASE_LAST_CHECK, 0)
            val purchaseId = ValueFromSettings(EXT_PENDING_PURCHASE_ID, "")
            val isPurchased = ValueFromSettings(IS_ALREADY_PAID, false)
            val isPending = ValueFromSettings(IS_PAYMENT_PENDING, false)
            val licenseKey = ValueFromSettings(EXT_LICENSE_KEY, "")

            if(isPending.get(context) && purchaseId.get(context).isNotEmpty()) {
                val secondsSinceLastCheck = (System.currentTimeMillis() / 1000L) - lastCheck.get(context)

                if(secondsSinceLastCheck > pollPeriod || TEST_MODE) {
                    Log.d("PayPalBilling", "About to poll server for pending payment status...")
                    lastCheck.set(context, System.currentTimeMillis() / 1000L)

                    try {
                        val status = StatePayment.instance.getPaymentStatus(purchaseId.get(context))
                        when (status.status) {
                            -1 -> {
                                // still pending
                                Log.d("PayPalBilling", "Poll result - still pending")
                            }
                            0 -> {
                                // failed
                                isPurchased.set(context, false)
                                isPending.set(context, false)
                                Log.d("PayPalBilling", "Poll result - payment failed")
                            }
                            1 -> {
                                // success
                                isPurchased.set(context, true)
                                isPending.set(context, false)

                                if(status.purchaseId != null) {
                                    licenseKey.set(context, status.purchaseId!!)
                                } else {
                                    licenseKey.set(context, purchaseId.get(context))
                                }
                                Log.d("PayPalBilling", "Poll result - payment successful, id ${purchaseId.get(context)} ${status.purchaseId}")
                            }
                            else -> {
                                Log.e("PayPalBilling", "Invalid status response ${status.status}")
                            }
                        }
                    } catch(e: Exception) {
                        e.printStackTrace()
                        Log.e("PayPalBilling", "Polling failed with exception")
                    }
                } else {
                    Log.d("PayPalBilling", "Not polling for now, because $secondsSinceLastCheck does not exceed period of $pollPeriod")
                }
            } else {
                Log.d("PayPalBilling", "Not polling, because there is no pending payment.")
            }
        }
    }

    override fun checkAlreadyOwnsProduct() {
        // The following checks if the payment failed or succeeded if the payment was still pending
        coroutineScope.launch {
            withContext(Dispatchers.IO) {
                pollPendingStatus(context)
            }
        }
    }

    override fun startConnection(onReady: () -> Unit) {
        onReady()
    }

    override fun onResume() {
    }

    override fun launchBillingFlow() {
        context.startAppActivity(BillingActivity::class.java)
    }

    override fun supportsCheckingIfAlreadyOwnsProduct(): Boolean {
        return false
    }

    override fun getName(): String {
        return ""
    }
}