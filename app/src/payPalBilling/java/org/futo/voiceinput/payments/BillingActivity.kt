package org.futo.voiceinput.payments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.datastore.preferences.core.edit
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.futo.futopay.PaymentManager
import com.futo.futopay.PaymentStatusListener
import com.futo.futopay.UIDialogs
import kotlinx.coroutines.launch
import org.futo.voiceinput.R
import org.futo.voiceinput.settings.EXT_PENDING_PURCHASE_ID
import org.futo.voiceinput.settings.EXT_PENDING_PURCHASE_LAST_CHECK
import org.futo.voiceinput.settings.IS_ALREADY_PAID
import org.futo.voiceinput.settings.IS_PAYMENT_PENDING
import org.futo.voiceinput.settings.dataStore

const val GJ_PRODUCT_ID_VOICE_INPUT = "voiceinput"

class BuyFragment : Fragment() {
    private var _view: BuyView? = null;
    private var onCancel: () -> Unit = { }
    fun setCancelListener(newOnCancel: () -> Unit) {
        this.onCancel = newOnCancel
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = BuyView(this, inflater);
        _view = view;
        return view;
    }


    override fun onStart() {
        super.onStart()
        _view?.buy()
    }

    private class BuyView(val fragment: BuyFragment, val inflater: LayoutInflater): LinearLayout(inflater.context) {
        private val _overlayPaying: FrameLayout;
        private val _paymentManager: PaymentManager;

        private val _listener: PaymentStatusListener = object : PaymentStatusListener {
            override fun onSuccess(purchaseId: String?) {
                if(purchaseId != null) {
                    fragment.lifecycleScope.launch {
                        context.dataStore.edit {
                            it[IS_ALREADY_PAID.key] = true
                            it[IS_PAYMENT_PENDING.key] = true
                            it[EXT_PENDING_PURCHASE_ID.key] = purchaseId
                            it[EXT_PENDING_PURCHASE_LAST_CHECK.key] = System.currentTimeMillis() / 1000L
                        }

                        fragment.onCancel()
                    }
                } else {
                    UIDialogs.showDialog(context, R.drawable.ic_check, "Payment succeeded", "Thanks for your purchase, a key will be sent to your email after your payment has been received!", 0, UIDialogs.Action("Ok", {}, UIDialogs.ActionStyle.PRIMARY));
                }
            }

            override fun onCancel() {
                fragment.onCancel()
            }

            override fun onFailure(error: Throwable) {
                UIDialogs.showGeneralErrorDialog(context, "Payment failed", error);
            }
        }

        init {
            inflater.inflate(R.layout.fragment_buy, this);
            _overlayPaying = findViewById(R.id.overlay_paying);

            _paymentManager = PaymentManager(StatePayment.instance, fragment, _overlayPaying, _listener);
        }

        fun buy() {
            _paymentManager.startPayment(StatePayment.instance, fragment.lifecycleScope, GJ_PRODUCT_ID_VOICE_INPUT);
        }

        fun paid() {
            /*
            val licenseInput = SlideUpMenuTextInput(context, "License");
            val productLicenseDialog = SlideUpMenuOverlay(context, findViewById<FrameLayout>(R.id.overlay_paid), "Enter license key", "Ok", true, licenseInput);
            productLicenseDialog.onOK.subscribe {
                val licenseText = licenseInput.text;
                if (licenseText.isNullOrEmpty()) {
                    UIDialogs.showDialogOk(context, R.drawable.ic_error_pred, "Invalid license key");
                    return@subscribe;
                }

                _fragment.lifecycleScope.launch(Dispatchers.IO) {

                    try{
                        val activationResult = StatePayment.instance.setPaymentLicense(licenseText);

                        withContext(Dispatchers.Main) {
                            if(activationResult) {
                                licenseInput.deactivate();
                                licenseInput.clear();
                                productLicenseDialog.hide(true);

                                UIDialogs.showDialogOk(context, R.drawable.ic_check, "Your license key has been set!\nAn app restart might be required.");
                                _fragment.close(true);
                            }
                            else
                            {
                                UIDialogs.showDialogOk(context, R.drawable.ic_error_pred, "Invalid license key");
                            }
                        }
                    }
                    catch(ex: Throwable) {
                        Logger.e("BuyFragment", "Failed to activate key", ex);
                        withContext(Dispatchers.Main) {
                            UIDialogs.showGeneralErrorDialog(context, "Failed to activate key", ex);
                        }
                    }
                }
            };
            productLicenseDialog.show();
            */
        }

    }

    companion object {
        fun newInstance() = BuyFragment().apply {}
    }
}

class BillingActivity : AppCompatActivity() {
    lateinit var rootView : LinearLayout

    lateinit var buyFragment: BuyFragment

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_billing)

        buyFragment = BuyFragment.newInstance()
        buyFragment.setCancelListener {
            finish()
        }

        supportFragmentManager.beginTransaction().show(buyFragment).replace(R.id.rootView, buyFragment).commitNow()
    }
}