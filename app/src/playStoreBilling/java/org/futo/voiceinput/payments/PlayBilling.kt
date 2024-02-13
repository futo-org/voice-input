package org.futo.voiceinput.payments

import android.app.Activity
import android.content.Context
import android.widget.Toast
import androidx.datastore.preferences.core.edit
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.Purchase.PurchaseState
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.android.billingclient.api.queryProductDetails
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.futo.voiceinput.BuildConfig
import org.futo.voiceinput.settings.IS_ALREADY_PAID
import org.futo.voiceinput.settings.IS_PAYMENT_PENDING
import org.futo.voiceinput.settings.getSetting
import org.futo.voiceinput.settings.setSetting

const val PRODUCT_ID = "one_time_license"
class PlayBilling(private val context: Context, private val coroutine: CoroutineScope) : BillingImpl {
    companion object {
        fun isAllowed(): Boolean {
            return true
        }
    }

    private val purchasesUpdatedListener =
        PurchasesUpdatedListener { _, _ ->
            checkAlreadyOwnsProduct()
        }

    internal var billingClient = BillingClient.newBuilder(context)
        .setListener(purchasesUpdatedListener)
        .enablePendingPurchases()
        .build()

    private fun handlePurchasedLicense(purchase: Purchase) {
        if(purchase.isAcknowledged) return;

        val params = AcknowledgePurchaseParams.newBuilder().setPurchaseToken(purchase.purchaseToken).build()
        billingClient.acknowledgePurchase(params) {
            if(it.responseCode != BillingClient.BillingResponseCode.OK) {
                Toast.makeText(context, "PlayBilling - Failed to acknowledge purchase, will try again later", Toast.LENGTH_SHORT).show()
                // Handle error
            }
        }
    }

    override fun checkAlreadyOwnsProduct() {
        @Suppress("KotlinConstantConditions")
        if(BuildConfig.FLAVOR == "dev") return

        if(billingClient.connectionState != BillingClient.ConnectionState.CONNECTED) {
            return startConnection { checkAlreadyOwnsProduct() }
        }

        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.INAPP)
            .build()
        billingClient.queryPurchasesAsync(params) { result, purchases ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                val purchasesOnlyContainingLicense = purchases.filter { it.products.contains(PRODUCT_ID) }

                val lastSuccessfulPurchase = purchasesOnlyContainingLicense.lastOrNull {
                    it.purchaseState == PurchaseState.PURCHASED
                }

                val lastPendingPurchase = purchasesOnlyContainingLicense.lastOrNull {
                    it.purchaseState == PurchaseState.PENDING
                }

                val isPaid = lastSuccessfulPurchase != null || lastPendingPurchase != null
                val isPending = lastSuccessfulPurchase == null && lastPendingPurchase != null

                coroutine.launch {
                    val isPaidSetting = context.getSetting(IS_ALREADY_PAID)
                    val isPendingSettings = context.getSetting(IS_PAYMENT_PENDING)

                    if(isPaid != isPaidSetting
                        && (isPaid || isPendingSettings) // For now, only allow going paid -> unpaid if the payment was pending
                                                         // Otherwise, this would cancel out tapping "I already paid"
                    ) {
                        context.setSetting(IS_ALREADY_PAID, isPaid)
                    }

                    if(isPendingSettings != isPending) {
                        context.setSetting(IS_PAYMENT_PENDING, isPending)
                    }
                }

                purchasesOnlyContainingLicense.filter { !it.isAcknowledged }.forEach { handlePurchasedLicense(it) }
            } else {
                // Handle any other error codes.
            }
        }
    }

    override fun startConnection(onReady: () -> Unit) {
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                if (billingResult.responseCode ==  BillingClient.BillingResponseCode.OK) {
                    onReady()
                }
            }
            override fun onBillingServiceDisconnected() {
                //Toast.makeText(context, "PlayBilling - Disconnected from billing service", Toast.LENGTH_SHORT).show()
                // Try to restart the connection on the next request to
                // Google Play by calling the startConnection() method.
            }
        })
    }

    override fun onResume() {
        checkAlreadyOwnsProduct()
    }

    override fun launchBillingFlow() {
        if(billingClient.connectionState != BillingClient.ConnectionState.CONNECTED) {
            println("Launch billing flow - not connected")
            Toast.makeText(context, "PlayBilling - Not connected to Billing", Toast.LENGTH_SHORT).show()
            return startConnection { launchBillingFlow() }
        }

        coroutine.launch {
            val productList = ArrayList<QueryProductDetailsParams.Product>()
            productList.add(
                QueryProductDetailsParams.Product.newBuilder()
                    .setProductId(PRODUCT_ID)
                    .setProductType(BillingClient.ProductType.INAPP)
                    .build()
            )
            val params = QueryProductDetailsParams.newBuilder()
            params.setProductList(productList)

            val productDetailsResult = withContext(Dispatchers.IO) {
                billingClient.queryProductDetails(params.build())
            }

            if(productDetailsResult.productDetailsList == null) {
                Toast.makeText(context, "PlayBilling - Failed to get product details list", Toast.LENGTH_SHORT).show()
                return@launch
            }

            val detailsList = productDetailsResult.productDetailsList!!

            if(detailsList.isEmpty()) {
                Toast.makeText(context, "PlayBilling - The in-app purchase $PRODUCT_ID does not exist", Toast.LENGTH_SHORT).show()
                return@launch
            }
            val productDetails = detailsList[0]

            // An activity reference from which the billing flow will be launched.
            val activity: Activity = context as Activity

            val productDetailsParamsList = listOf(
                BillingFlowParams.ProductDetailsParams.newBuilder()
                    .setProductDetails(productDetails)
                    .build()
            )

            val billingFlowParams = BillingFlowParams.newBuilder()
                .setProductDetailsParamsList(productDetailsParamsList)
                .build()

            // Launch the billing flow
            val billingResult = billingClient.launchBillingFlow(activity, billingFlowParams)
            if(billingResult.responseCode != BillingClient.BillingResponseCode.OK) {
                Toast.makeText(context, "PlayBilling - Failed to launch billing flow", Toast.LENGTH_SHORT).show()
                return@launch
            }
        }
    }

    override fun supportsCheckingIfAlreadyOwnsProduct(): Boolean {
        return true
    }

    override fun getName(): String {
        return "Google Play"
    }
}