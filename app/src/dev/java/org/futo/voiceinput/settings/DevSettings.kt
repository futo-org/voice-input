package org.futo.voiceinput.settings

import android.app.Activity
import android.content.Intent
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
import com.android.billingclient.api.QueryPurchasesParams
import kotlinx.coroutines.runBlocking
import org.futo.voiceinput.ENGLISH_MODELS
import org.futo.voiceinput.MULTILINGUAL_MODELS
import org.futo.voiceinput.downloader.DownloadActivity
import org.futo.voiceinput.payments.PRODUCT_ID
import org.futo.voiceinput.payments.PlayBilling
import org.futo.voiceinput.settings.pages.SettingsSeparator
import org.futo.voiceinput.settings.pages.TRIAL_PERIOD_DAYS

@Composable
fun DevOnlySettings() {
    val daysInstalled = useNumberOfDaysInstalled()

    SettingsSeparator("Payment testing [Developer Build only]")
    SettingToggleDataStore(
        "Show payment notice despite not being past $TRIAL_PERIOD_DAYS days",
        FORCE_SHOW_NOTICE,
        subtitle = "You are currently at ${daysInstalled.value} days",
    )
    SettingToggleDataStore(
        "Is paid?",
        IS_ALREADY_PAID
    )

    val reminder = useDataStore(NOTICE_REMINDER_TIME)
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
    SettingItem(title = "Force legacy tflite", subtitle="Delete GGML models and download tflite", onClick = {

        runBlocking {
            context.setSetting(DISMISS_MIGRATION_TIP, false)
            context.setSetting(MODELS_MIGRATED, false)
        }

        context.filesDir.listFiles()?.forEach { it.delete() }

        val intent = Intent(context, DownloadActivity::class.java)
        intent.putStringArrayListExtra("models", ArrayList(listOf(ENGLISH_MODELS[1], MULTILINGUAL_MODELS[1]).map { model ->
            arrayListOf(
                model.legacy.decoder_file,
                model.legacy.encoder_xatn_file,
                model.legacy.vocab_file,
            )
        }.flatten()))

        if(context !is Activity) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        context.startActivity(intent)
    }) { }

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

        navigator.navigateUp()
    }

    val numTimesPressed = remember { mutableStateOf(0) }

    Button(onClick = {
        numTimesPressed.value += 1;
        if (numTimesPressed.value > 5) {
            consumeProduct()
            navigator.navigateUp()
        }
    }, modifier = Modifier.padding(32.dp, 64.dp)) {
        Text("Remove product from purchase history [DANGER!]")
    }
}