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
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchaseHistoryParams
import com.android.billingclient.api.QueryPurchasesParams
import com.android.billingclient.api.queryProductDetails
import com.android.billingclient.api.queryPurchaseHistory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.futo.voiceinput.IS_ALREADY_PAID
import org.futo.voiceinput.dataStore

const val PRODUCT_ID = "one_time_license"
class PlayBilling(private val context: Context, private val coroutine: CoroutineScope) : BillingImpl {
    private val purchasesUpdatedListener =
        PurchasesUpdatedListener { billingResult, purchases ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
                for (purchase in purchases) {
                    if(purchase.products.contains(PRODUCT_ID)) {
                        handlePurchasedLicense(purchase)
                    }
                }
            } else {
                Toast.makeText(context, "PlayBilling - Update listener failed", Toast.LENGTH_SHORT).show()
                // Handle any other error codes.
            }
        }

    private var billingClient = BillingClient.newBuilder(context)
        .setListener(purchasesUpdatedListener)
        .enablePendingPurchases()
        .build()


    private fun handlePurchasedLicense(purchase: Purchase) {
        val params = AcknowledgePurchaseParams.newBuilder().setPurchaseToken(purchase.purchaseToken).build()
        billingClient.acknowledgePurchase(params) {
            if(it.responseCode == BillingClient.BillingResponseCode.OK) {
                coroutine.launch {
                    context.dataStore.edit { it[IS_ALREADY_PAID] = true }
                }
            } else {
                Toast.makeText(context, "PlayBilling - Failed to acknowledge purchase", Toast.LENGTH_SHORT).show()
                // Handle error
            }
        }

        coroutine.launch {
            context.dataStore.edit { it[IS_ALREADY_PAID] = true }
        }
    }

    override fun checkAlreadyOwnsProduct() {
        if(billingClient.connectionState != BillingClient.ConnectionState.CONNECTED) {
            Toast.makeText(context, "PlayBilling - Not connected to Billing", Toast.LENGTH_SHORT).show()
            return startConnection { checkAlreadyOwnsProduct() }
        }

        coroutine.launch {
            val purchaseHistoryParams = QueryPurchaseHistoryParams.newBuilder()
                .setProductType(BillingClient.ProductType.INAPP).build()
            val purchaseHistory = withContext(Dispatchers.IO) {
                billingClient.queryPurchaseHistory(purchaseHistoryParams)
            }

            val ownsProduct = purchaseHistory.purchaseHistoryRecordList?.any {
                it.products.contains(PRODUCT_ID)
            } ?: false

            if (ownsProduct) {
                context.dataStore.edit { it[IS_ALREADY_PAID] = true }
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
                Toast.makeText(context, "PlayBilling - Disconnected from billing service", Toast.LENGTH_SHORT).show()
                // Try to restart the connection on the next request to
                // Google Play by calling the startConnection() method.
            }
        })
    }

    override fun onResume() {
        if(billingClient.connectionState != BillingClient.ConnectionState.CONNECTED) {
            //Toast.makeText(context, "PlayBilling - Not connected to Billing", Toast.LENGTH_SHORT).show()
            return startConnection { onResume() }
        }

        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.INAPP)
            .build()
        billingClient.queryPurchasesAsync(params) { result, purchases ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                for (purchase in purchases) {
                    if (purchase.products.contains(PRODUCT_ID)) {
                        handlePurchasedLicense(purchase)
                    }
                }
            } else {
                Toast.makeText(
                    context,
                    "PlayBilling - Query purchases responded with non-OK",
                    Toast.LENGTH_SHORT
                ).show()
                // Handle any other error codes.
            }
        }

        checkAlreadyOwnsProduct()
    }

    override fun launchBillingFlow() {
        if(billingClient.connectionState != BillingClient.ConnectionState.CONNECTED) {
            println("Launch billing flow - not connected")
            Toast.makeText(context, "PlayBilling - Not connected to Billing", Toast.LENGTH_SHORT).show()
            return startConnection { launchBillingFlow() }
        }

        println("lunaching coroutine scope")

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

    override fun getName(): String {
        return "Google Play"
    }
}