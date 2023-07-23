package org.futo.voiceinput.settings

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.rememberNavController
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.ConsumeParams
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.QueryPurchasesParams
import org.futo.voiceinput.FORCE_SHOW_NOTICE
import org.futo.voiceinput.IS_ALREADY_PAID
import org.futo.voiceinput.NOTICE_REMINDER_TIME
import org.futo.voiceinput.payments.PRODUCT_ID
import org.futo.voiceinput.payments.PlayBilling
import org.futo.voiceinput.ui.theme.Typography

@Composable
fun DevOnlySettings() {
    val daysInstalled = useNumberOfDaysInstalled()

    Text("Payment testing [Developer Build only]", style = Typography.labelLarge)
    SettingToggle(
        "Show payment notice despite not being past $TRIAL_PERIOD_DAYS days",
        FORCE_SHOW_NOTICE,
        subtitle = "You are currently at ${daysInstalled.value} days",
        default = false
    )
    SettingToggle(
        "Is paid?",
        IS_ALREADY_PAID,
        default = false
    )

    val reminder = useDataStore(NOTICE_REMINDER_TIME, default = 0L)
    val currTime = System.currentTimeMillis() / 1000L

    val subtitleValue = if (reminder.value > currTime) {
        val diffDays = (reminder.value - currTime) / 60.0 / 60.0 / 24.0
        "Reminding in ${"%.2f".format(diffDays)} days"
    } else {
        "Reminder unset"
    }
    SettingToggleRaw(
        "Reminder Time",
        reminder.value > currTime,
        {
            if (!it) {
                reminder.setValue(0L)
            }
        },
        subtitleValue,
        reminder.value <= currTime,
        { }
    )

    val context = LocalContext.current
    val navigator = rememberNavController()
    val consumeProduct = {
        val activity = context as SettingsActivity
        val billing = activity.billing

        val playStoreBilling = billing.getBillings().first { it is PlayBilling } as PlayBilling
        val billingClient = playStoreBilling.billingClient

        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.INAPP)
            .build()
        billingClient.queryPurchasesAsync(params) { result, purchases ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                val purchasesOnlyContainingLicense = purchases.filter {
                    it.products.contains(
                        PRODUCT_ID
                    )
                }

                purchasesOnlyContainingLicense.forEach {
                    val consumeParams = ConsumeParams.newBuilder()
                        .setPurchaseToken(it.purchaseToken)
                        .build()
                    billingClient.consumeAsync(consumeParams) { result, str ->
                        if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                            println("Failed to consume a purchase")
                        }
                    }
                }
            }
        }

        navigator.popBackStack()
    }

    val numTimesPressed = remember { mutableStateOf(0) }

    Button(onClick = {
        numTimesPressed.value += 1;
        if (numTimesPressed.value > 5) {
            consumeProduct()
            navigator.popBackStack()
        }
    }, modifier = Modifier.padding(32.dp, 64.dp)) {
        Text("Remove product from purchase history [DANGER!]")
    }
}