package org.futo.voiceinput.settings

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Alignment.Companion.CenterHorizontally
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.launch
import org.futo.voiceinput.BuildConfig
import org.futo.voiceinput.FORCE_SHOW_NOTICE
import org.futo.voiceinput.IS_ALREADY_PAID
import org.futo.voiceinput.NOTICE_REMINDER_TIME
import org.futo.voiceinput.Screen
import org.futo.voiceinput.dataStore
import org.futo.voiceinput.payments.BillingManager
import org.futo.voiceinput.startAppActivity
import org.futo.voiceinput.ui.theme.Slate200
import org.futo.voiceinput.ui.theme.Typography
import kotlin.math.absoluteValue

@Composable
fun PaymentText() {
    val numDaysInstalled = useNumberOfDaysInstalled()
    val localText = @Composable { it: String -> Text(it, modifier = Modifier.padding(8.dp), style = Typography.bodyMedium, color = MaterialTheme.colorScheme.onBackground)}

    localText("You've been using FUTO Voice Input for ${numDaysInstalled.value} days. If you find this app useful, please consider paying to support future development of FUTO software.")
    localText("FUTO is dedicated to making good software that doesn't abuse you. This app will never serve you ads or collect your personal data.")
}

suspend fun pushNoticeReminderTime(context: Context, days: Float) {
    // If the user types in a crazy high number, the long can't store such a large value and it won't suppress the reminder
    // 21x the age of the universe ought to be enough for a payment notice reminder
    // Also take the absolute value in the case of a negative number
    val clampedDays = if(days.absoluteValue >= 1.06751991E14f) {
        1.06751991E14f
    }else {
        days.absoluteValue
    }

    context.dataStore.edit { preferences ->
        preferences[NOTICE_REMINDER_TIME] = System.currentTimeMillis() / 1000L + (clampedDays * 60.0 * 60.0 * 24.0).toLong()
    }
}

const val TRIAL_PERIOD_DAYS = 30

@Composable
fun UnpaidNoticeCondition(force: Boolean = LocalInspectionMode.current, showOnlyIfReminder: Boolean = false, inner: @Composable () -> Unit) {
    val numDaysInstalled = useNumberOfDaysInstalled()
    val forceShowNotice = useDataStore(FORCE_SHOW_NOTICE, default = false)
    val isAlreadyPaid = useDataStore(IS_ALREADY_PAID, default = false)
    val pushReminderTime = useDataStore(NOTICE_REMINDER_TIME, default = 0L)
    val currentTime = System.currentTimeMillis() / 1000L

    val reminderTimeIsUp = (currentTime >= pushReminderTime.value)

    val displayCondition =
        // The trial period time is over
        (forceShowNotice.value || (numDaysInstalled.value >= TRIAL_PERIOD_DAYS))
                // and the current time is past the reminder time (or it's not past if showOnlyIfReminder)
                && ((!showOnlyIfReminder && reminderTimeIsUp) || (showOnlyIfReminder && !reminderTimeIsUp))
                // and we have not already paid
                && (!isAlreadyPaid.value)

    if(force || displayCondition) {
        inner()
    }
}

@Composable
@Preview
fun ConditionalUnpaidNoticeInVoiceInputWindow(onClose: (() -> Unit)? = null) {
    val context = LocalContext.current

    UnpaidNoticeCondition {
        TextButton(onClick = {
            context.startAppActivity(PaymentActivity::class.java)
            if(onClose != null) onClose()
        }) {
            Text("Unpaid", color = Slate200)
        }
    }
}


@Composable
@Preview
fun UnpaidNotice(onPay: () -> Unit = { }, onAlreadyPaid: () -> Unit = { }) {
    Surface(color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier
        .fillMaxWidth()
        .padding(8.dp), shape = RoundedCornerShape(4.dp)
    ) {
        Column(modifier = Modifier.padding(8.dp, 0.dp)) {
            Text(
                "Unpaid FUTO Voice Input",
                modifier = Modifier.padding(8.dp),
                style = Typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground
            )

            PaymentText()

            Row(modifier = Modifier
                .padding(8.dp)
                .align(CenterHorizontally)) {

                Box(modifier = Modifier.weight(1.0f)) {
                    Button(onClick = onPay, modifier = Modifier.align(Alignment.Center)) {
                        Text("Pay Now")
                    }
                }

                Box(modifier = Modifier.weight(1.0f)) {
                    Button(
                        onClick = onAlreadyPaid, colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondary,
                            contentColor = MaterialTheme.colorScheme.onSecondary
                        ), modifier = Modifier.align(Alignment.Center)
                    ) {
                        Text("I already paid")
                    }
                }
            }
        }
    }
}


@Composable
@Preview
fun ConditionalUnpaidNoticeWithNav(navController: NavController = rememberNavController()) {
    val isAlreadyPaid = useDataStore(IS_ALREADY_PAID, default = false)

    UnpaidNoticeCondition {
        UnpaidNotice(onPay = {
            navController.navigate("payment")
        }, onAlreadyPaid = {
            isAlreadyPaid.setValue(true)
        })
    }
}


@Composable
fun PaymentScreen(settingsViewModel: SettingsViewModel = viewModel(), navController: NavHostController = rememberNavController(), onExit: () -> Unit = { }, billing: BillingManager) {
    val isAlreadyPaid = useDataStore(IS_ALREADY_PAID, default = false)
    val pushReminderTime = useDataStore(NOTICE_REMINDER_TIME, default = 0L)
    val currentTime = System.currentTimeMillis() / 1000L

    val reminderTimeIsUp = (currentTime >= pushReminderTime.value)

    val onAlreadyPaid = {
        isAlreadyPaid.setValue(true)
    }

    LaunchedEffect(isAlreadyPaid.value) {
        if(isAlreadyPaid.value) {
            onExit()
        }
    }

    Screen("Payment") {
        ScrollableList {
            PaymentText()

            val context = LocalContext.current
            Column(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier
                    .padding(8.dp)
                    .align(CenterHorizontally)) {
                    billing.getBillings().forEach {
                        Button(onClick = {
                            it.launchBillingFlow()
                        }, modifier = Modifier
                            .padding(8.dp)
                            .align(CenterHorizontally)) {
                            Text("Pay via ${it.getName()}")
                        }
                    }
                }

                if (BuildConfig.FLAVOR != "playStore") {
                    Button(
                        onClick = { onAlreadyPaid() }, colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondary,
                            contentColor = MaterialTheme.colorScheme.onSecondary
                        ), modifier = Modifier.align(CenterHorizontally)
                    ) {
                        Text("I already paid")
                    }
                }

                if(reminderTimeIsUp) {
                    val lastValidRemindValue = remember { mutableStateOf(5.0f) }
                    val remindDays = remember { mutableStateOf("5") }
                    Row(
                        modifier = Modifier
                            .align(CenterHorizontally)
                            .padding(16.dp)
                    ) {
                        val coroutineScope = rememberCoroutineScope()
                        Button(
                            onClick = {
                                coroutineScope.launch {
                                    pushNoticeReminderTime(context, lastValidRemindValue.value)
                                }
                                onExit()
                            }, colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.secondary,
                                contentColor = MaterialTheme.colorScheme.onSecondary
                            )
                        ) {
                            Text("Remind me in ")
                            Surface(color = MaterialTheme.colorScheme.surface) {
                                BasicTextField(
                                    value = remindDays.value,
                                    onValueChange = {
                                        remindDays.value = it

                                        it.toFloatOrNull()?.let { lastValidRemindValue.value = it }
                                    },
                                    modifier = Modifier
                                        .width(32.dp)
                                        .background(MaterialTheme.colorScheme.surface),
                                    textStyle = Typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                                    cursorBrush = SolidColor(MaterialTheme.colorScheme.onSurface),
                                    keyboardOptions = KeyboardOptions.Default.copy(keyboardType = KeyboardType.Number)
                                )
                            }
                            Text(" days")
                        }
                    }
                }


                if (BuildConfig.FLAVOR == "dev") {
                    Text(
                        "You are on the Developer release, so you are seeing all payment methods and options",
                        style = Typography.labelSmall,
                        modifier = Modifier.padding(8.dp)
                    )
                }
            }
        }
    }
}