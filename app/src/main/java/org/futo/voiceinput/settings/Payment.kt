package org.futo.voiceinput.settings

import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Alignment.Companion.CenterHorizontally
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import org.futo.voiceinput.FORCE_SHOW_NOTICE
import org.futo.voiceinput.IS_ALREADY_PAID
import org.futo.voiceinput.Screen
import org.futo.voiceinput.startAppActivity
import org.futo.voiceinput.ui.theme.Slate200
import org.futo.voiceinput.ui.theme.Typography


val TRIAL_PERIOD_DAYS = 30

@Composable
fun UnpaidNoticeCondition(force: Boolean = LocalInspectionMode.current, inner: @Composable () -> Unit) {
    val numDaysInstalled = useNumberOfDaysInstalled()
    val forceShowNotice = useDataStore(FORCE_SHOW_NOTICE, default = false)
    val isAlreadyPaid = useDataStore(IS_ALREADY_PAID, default = false)

    if(force || forceShowNotice.value || numDaysInstalled.value >= TRIAL_PERIOD_DAYS) {
        if(force || !isAlreadyPaid.value) {
            inner()
        }
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
fun UnpaidNotice(numDaysInstalled: Int = 30, onPay: () -> Unit = { }, onAlreadyPaid: () -> Unit = { }) {
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
            Text(
                "You've been using FUTO Voice Input for $numDaysInstalled days. If you find this app useful, please consider paying to support future development.",
                modifier = Modifier.padding(8.dp),
                style = Typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                "FUTO is dedicated to making good software that doesn't abuse you. This app will never serve you ads or collect your personal data.",
                modifier = Modifier.padding(8.dp),
                style = Typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(modifier = Modifier
                .padding(8.dp)
                .align(Alignment.CenterHorizontally)) {

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
    val numDaysInstalled = useNumberOfDaysInstalled()
    val isAlreadyPaid = useDataStore(IS_ALREADY_PAID, default = false)

    UnpaidNoticeCondition {
        UnpaidNotice(numDaysInstalled.value, onPay = {
            navController.navigate("payment")
        }, onAlreadyPaid = {
            isAlreadyPaid.setValue(true)
        })
    }
}


@Composable
@Preview
fun PaymentScreen(settingsViewModel: SettingsViewModel = viewModel(), navController: NavHostController = rememberNavController(), onExit: () -> Unit = { }) {
    val numDaysInstalled = useNumberOfDaysInstalled()
    val isAlreadyPaid = useDataStore(IS_ALREADY_PAID, default = false)

    Screen("Payment") {
        Text(
            "You've been using FUTO Voice Input for ${numDaysInstalled.value} days. If you find this app useful, please consider paying to support future development.",
            modifier = Modifier.padding(8.dp),
            style = Typography.bodyMedium
        )
        Text(
            "FUTO is dedicated to making good software that doesn't abuse you. This app will never serve you ads or collect your personal data.",
            modifier = Modifier.padding(8.dp),
            style = Typography.bodyMedium
        )

        val context = LocalContext.current
        Column(modifier = Modifier.fillMaxWidth()) {
            Button(onClick = {
                val toast = Toast.makeText(context, "Payment not yet implemented", Toast.LENGTH_SHORT)
                toast.show()
            }, modifier = Modifier
                .align(CenterHorizontally)
                .padding(16.dp)) {
                Text("Pay via Google Play")
            }


            // TODO: Here we should instead have a "Remind me in (x) days"

            Button(
                onClick = {
                    isAlreadyPaid.setValue(true)
                    navController.popBackStack()
                    onExit()
                }, colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondary,
                    contentColor = MaterialTheme.colorScheme.onSecondary
                ), modifier = Modifier
                    .align(CenterHorizontally)
                    .padding(16.dp)
            ) {
                Text("I already paid")
            }
        }
    }
}