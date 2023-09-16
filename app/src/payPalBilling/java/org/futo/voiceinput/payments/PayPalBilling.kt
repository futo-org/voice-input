package org.futo.voiceinput.payments

import android.content.Context
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.DialogFragment
import com.futo.futopay.PaymentManager
import com.futo.futopay.PaymentState
import org.futo.voiceinput.openURI
import org.futo.voiceinput.settings.SettingsActivity
import org.futo.voiceinput.startAppActivity


class StatePayment : PaymentState(VERIFICATION_PUBLIC_KEY) {
    override fun savePaymentKey(licenseKey: String, licenseActivation: String) {
    }

    override fun getPaymentKey(): Pair<String, String> {
        return Pair("", "")
    }

    companion object {
        private val VERIFICATION_PUBLIC_KEY = "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA3JuRj4dv1mzB9mnTrGXdM/DlbDbNkrKvyEvR27Gy9ZEb3p3WhNk/BMPkA8L0ebrhq55N1o0IgF0qi/vFE0wnHuFcAIUIZEz6ZGLCgDaILursXpfKFVdj+ZidXCFrFgNW232EXhhXYF8pvSyLtEsjaYas2C/X68wL7nLt9Iw/0YC3ZmyislaV3BCdmSnZapdzmoRRHNV1hvhhBdF35pt9Mn0Tv3KP9/0+bM5CCv7kK2LI7NFmsSjXrTKZLDol1cOED9IC6SxUh32Dl67pyspdbMrZNa5GRWRuV+IM/w4FaE8W5aa45znvScJmngwzAq5W/8TsAlEDSbd5zQIvFQok0wIDAQAB";
        private var _instance : StatePayment? = null;
        val instance : StatePayment
            get(){
                if(_instance == null)
                    _instance = StatePayment();
                return _instance!!;
            };
    }
}

class PayPalBilling(val context: Context) : BillingImpl {
    companion object {
        fun isAllowed(): Boolean {
            return true
        }
    }

    override fun checkAlreadyOwnsProduct() {
        // In the future, maybe we could get a unique device ID or fingerprint and check using k-anonymity
    }

    override fun startConnection(onReady: () -> Unit) {
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