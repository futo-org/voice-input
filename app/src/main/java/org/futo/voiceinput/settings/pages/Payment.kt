package org.futo.voiceinput.settings.pages

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Alignment.Companion.Center
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import androidx.navigation.NavOptions
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.launch
import org.futo.voiceinput.BuildConfig
import org.futo.voiceinput.R
import org.futo.voiceinput.payments.BillingManager
import org.futo.voiceinput.settings.FORCE_SHOW_NOTICE
import org.futo.voiceinput.settings.HAS_SEEN_PAID_NOTICE
import org.futo.voiceinput.settings.IS_ALREADY_PAID
import org.futo.voiceinput.settings.IS_PAYMENT_PENDING
import org.futo.voiceinput.settings.NOTICE_REMINDER_TIME
import org.futo.voiceinput.settings.PaymentActivity
import org.futo.voiceinput.settings.ScreenTitle
import org.futo.voiceinput.settings.ScrollableList
import org.futo.voiceinput.settings.SettingsViewModel
import org.futo.voiceinput.settings.setSetting
import org.futo.voiceinput.settings.useDataStore
import org.futo.voiceinput.settings.useNumberOfDaysInstalled
import org.futo.voiceinput.startAppActivity
import org.futo.voiceinput.theme.Typography
import kotlin.math.absoluteValue


@Composable
fun ParagraphText(it: String, modifier: Modifier = Modifier) {
    Text(it, modifier = modifier.padding(16.dp, 8.dp), style = Typography.bodyMedium,
        color = LocalContentColor.current)
}

@Composable
fun IconText(icon: Painter, title: String, body: String) {
    Row(modifier = Modifier.padding(8.dp)) {
        Icon(icon, contentDescription = null, modifier = Modifier
            .align(Alignment.Top)
            .padding(8.dp, 10.dp)
            .size(with(LocalDensity.current) { Typography.titleMedium.fontSize.toDp() }))
        Column(modifier = Modifier.padding(6.dp)) {
            Text(title, style = Typography.titleMedium)
            Spacer(modifier = Modifier.height(4.dp))
            Text(body, style = Typography.bodySmall, color = LocalContentColor.current.copy(alpha = 0.8f))
        }
    }
}


@Composable
fun PaymentText(verbose: Boolean) {
    val numDaysInstalled = useNumberOfDaysInstalled()

    // Doesn't make sense to say "You've been using for ... days" if it's less than seven days
    if(numDaysInstalled.value >= 7) {
        ParagraphText(stringResource(R.string.payment_text_1, numDaysInstalled.value))
    } else {
        ParagraphText(stringResource(R.string.payment_text_1_alt))
    }

    if(verbose) {
        IconText(
            icon = painterResource(id = R.drawable.activity),
            title = stringResource(R.string.sustainable_development_title),
            body = stringResource(R.string.sustainable_development_body)
        )

        IconText(
            icon = painterResource(id = R.drawable.unlock),
            title = stringResource(R.string.commitment_to_privacy_title),
            body = stringResource(R.string.commitment_to_privacy_body)
        )

        ParagraphText(stringResource(R.string.payment_dev_notice))
    } else {
        ParagraphText(stringResource(R.string.payment_text_2))
    }
}

suspend fun pushNoticeReminderTime(context: Context, days: Float) {
    // If the user types in a crazy high number, the long can't store such a large value and it won't suppress the reminder
    // 21x the age of the universe ought to be enough for a payment notice reminder
    // Also take the absolute value in the case of a negative number
    val clampedDays = if (days.absoluteValue >= 1.06751991E14f) {
        1.06751991E14f
    } else {
        days.absoluteValue
    }

    context.setSetting(NOTICE_REMINDER_TIME,
        System.currentTimeMillis() / 1000L + (clampedDays * 60.0 * 60.0 * 24.0).toLong())
}

const val TRIAL_PERIOD_DAYS = 30

@Composable
fun UnpaidNoticeCondition(
    force: Boolean = LocalInspectionMode.current,
    showOnlyIfReminder: Boolean = false,
    inner: @Composable () -> Unit
) {
    val numDaysInstalled = useNumberOfDaysInstalled()
    val forceShowNotice = useDataStore(FORCE_SHOW_NOTICE)
    val isAlreadyPaid = useDataStore(IS_ALREADY_PAID)
    val pushReminderTime = useDataStore(NOTICE_REMINDER_TIME)
    val currentTime = System.currentTimeMillis() / 1000L

    val reminderTimeIsUp = (currentTime >= pushReminderTime.value)

    val displayCondition = if(showOnlyIfReminder) {
        // Either the reminder time is not up, or we're not past the trial period
        (!isAlreadyPaid.value) && ((!reminderTimeIsUp) || (!forceShowNotice.value && numDaysInstalled.value < TRIAL_PERIOD_DAYS))
    } else {
        // The trial period time is over
        (forceShowNotice.value || (numDaysInstalled.value >= TRIAL_PERIOD_DAYS))
                // and the current time is past the reminder time
                && reminderTimeIsUp
                // and we have not already paid
                && (!isAlreadyPaid.value)
    }
    if (force || displayCondition) {
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
            if (onClose != null) onClose()
        }) {
            Text(stringResource(R.string.unpaid_indicator), color = MaterialTheme.colorScheme.onSurface)
        }
    }
}


@Composable
fun MediumTitle(text: String) {
    Text(
        text,
        modifier = Modifier.padding(8.dp),
        style = Typography.titleMedium,
        color = LocalContentColor.current
    )
}

@Composable
fun PaymentSurface(isPrimary: Boolean, title: String, onClick: (() -> Unit)? = null, content: @Composable () -> Unit) {
    val containerColor = if (isPrimary) {
        MaterialTheme.colorScheme.surfaceVariant
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    }

    val contentColor = if (isPrimary) {
        MaterialTheme.colorScheme.onSurfaceVariant
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Center) {
        Surface(
            color = containerColor,
            border = BorderStroke(2.dp, contentColor.copy(alpha = 0.33f)),
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier
                .padding(16.dp)
                .widthIn(Dp.Unspecified, 400.dp)
                .let {
                    if (onClick != null) {
                        it.clickable { onClick() }
                    } else {
                        it
                    }
                }
        ) {
            Column(modifier = Modifier.padding(8.dp)) {
                CompositionLocalProvider(LocalContentColor provides contentColor) {
                    MediumTitle(title)
                    content()
                }
            }
        }
    }
}


@Composable
@Preview
fun UnpaidNotice(onPay: () -> Unit = { }, onAlreadyPaid: () -> Unit = { }) {
    PaymentSurface(isPrimary = true, title = stringResource(R.string.unpaid_futo_voice_input), onClick = onPay) {
        PaymentText(false)

        Row(
            modifier = Modifier
                .padding(8.dp)
                .fillMaxWidth()
        ) {

            Box(modifier = Modifier.weight(1.0f)) {
                Button(onClick = onPay, modifier = Modifier.align(Center)) {
                    Text(stringResource(R.string.pay_now))
                }
            }

            Box(modifier = Modifier.weight(1.0f)) {
                Button(
                    onClick = onPay, colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondary,
                        contentColor = MaterialTheme.colorScheme.onSecondary
                    ), modifier = Modifier.align(Center)
                ) {
                    Text(stringResource(R.string.i_already_paid))
                }
            }
        }
    }

}


@Composable
@Preview
fun ConditionalUnpaidNoticeWithNav(navController: NavController = rememberNavController()) {
    val isAlreadyPaid = useDataStore(IS_ALREADY_PAID)

    UnpaidNoticeCondition {
        UnpaidNotice(onPay = {
            navController.navigate("pleasePay")
        }, onAlreadyPaid = {
            isAlreadyPaid.setValue(true)
        })
    }
}

@Composable
@Preview
fun PaymentThankYouScreen(onExit: () -> Unit = { }) {
    val hasSeenPaidNotice = useDataStore(HAS_SEEN_PAID_NOTICE)
    val isPending = useDataStore(IS_PAYMENT_PENDING)

    ScrollableList {
        ScreenTitle(
            if (isPending.value) {
                stringResource(R.string.payment_pending)
            } else {
                stringResource(R.string.thank_you)
            },
            showBack = false
        )
        ParagraphText(stringResource(R.string.thank_you_for_purchasing_voice_input))
        if (isPending.value) {
            ParagraphText(stringResource(R.string.payment_pending_body))
        }
        ParagraphText(stringResource(R.string.purchase_will_help_body))

        Box(modifier = Modifier.fillMaxWidth()) {
            Button(
                onClick = {
                    hasSeenPaidNotice.setValue(true)
                    onExit()
                },
                modifier = Modifier.align(Center)
            ) {
                Text(stringResource(R.string.continue_))
            }
        }
    }
}

@Composable
@Preview
fun PaymentFailedScreen(onExit: () -> Unit = { }) {
    val hasSeenPaidNotice = useDataStore(HAS_SEEN_PAID_NOTICE.key, default = true)

    val context = LocalContext.current

    ScrollableList {
        ScreenTitle(stringResource(R.string.payment_error), showBack = false)

        @Suppress("KotlinConstantConditions")
        ParagraphText( when(BuildConfig.FLAVOR) {
            "fDroid" -> stringResource(R.string.payment_failed_body_ex)
            "dev" -> stringResource(R.string.payment_failed_body_ex)
            "standalone" -> stringResource(R.string.payment_failed_body_ex)
            else -> stringResource(R.string.payment_failed_body)
        })
        ShareFeedbackOption(title = stringResource(R.string.contact_support))
        Box(modifier = Modifier.fillMaxWidth()) {
            val coroutineScope = rememberCoroutineScope()
            Button(
                onClick = {
                    // It would be rude to immediately annoy the user again about paying, so delay the notice forever
                    coroutineScope.launch {
                        pushNoticeReminderTime(context, Float.MAX_VALUE)
                    }

                    hasSeenPaidNotice.setValue(false)
                    onExit()
                },
                modifier = Modifier.align(Center)
            ) {
                Text(stringResource(R.string.continue_))
            }
        }
    }
}

@Composable
fun PaymentScreen(
    settingsViewModel: SettingsViewModel = viewModel(),
    navController: NavHostController = rememberNavController(),
    onExit: () -> Unit = { },
    billing: BillingManager
) {
    val isAlreadyPaid = useDataStore(IS_ALREADY_PAID)
    val pushReminderTime = useDataStore(NOTICE_REMINDER_TIME)
    val numDaysInstalled = useNumberOfDaysInstalled()
    val forceShowNotice = useDataStore(FORCE_SHOW_NOTICE)
    val currentTime = System.currentTimeMillis() / 1000L

    val reminderTimeIsUp = (currentTime >= pushReminderTime.value) && ((numDaysInstalled.value >= TRIAL_PERIOD_DAYS) || forceShowNotice.value)

    val onAlreadyPaid = {
        isAlreadyPaid.setValue(true)
        navController.navigateUp()
        navController.navigate("paid", NavOptions.Builder().setLaunchSingleTop(true).build())
    }

    LaunchedEffect(Unit) {
        billing.checkAlreadyOwnsProduct()
    }

    ScrollableList {
        ScreenTitle(stringResource(R.string.payment_title), showBack = true, navController = navController)


        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Center) {
            Icon(painterResource(id = R.drawable.ic_launcher_foreground), contentDescription = null, tint = MaterialTheme.colorScheme.onBackground, modifier = Modifier.size(128.dp))
        }


        PaymentSurface(isPrimary = true, title = stringResource(R.string.pay_for_futo_voice_input)) {
            PaymentText(true)

            Column(
                modifier = Modifier
                    .padding(8.dp)
                    .fillMaxWidth()
            ) {
                billing.getBillings().forEach {
                    Button(
                        onClick = {
                            it.launchBillingFlow()
                        }, modifier = Modifier
                            .padding(8.dp)
                            .fillMaxWidth()
                    ) {
                        val name = it.getName()
                        val text = if(name.isEmpty()) {
                            stringResource(R.string.pay)
                        } else {
                            stringResource(R.string.pay_via_x, name)
                        }

                        Text(text)
                    }
                }
            }
        }

        PaymentSurface(isPrimary = false, title = stringResource(R.string.already_paid_title)) {
            ParagraphText(it = stringResource(R.string.already_paid_body))

            val counter = remember { mutableStateOf(0) }
            Button(
                onClick = {
                    counter.value += 1
                    if(counter.value == 2) {
                        onAlreadyPaid()
                    }
                }, colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                ), modifier = Modifier
                    .padding(8.dp)
                    .fillMaxWidth()
            ) {
                Text(stringResource(
                    when(counter.value) {
                        0 -> R.string.i_already_paid
                        else -> R.string.i_already_paid_2
                    })
                )
            }
        }

        val context = LocalContext.current
        if (reminderTimeIsUp) {
            PaymentSurface(isPrimary = false, title = stringResource(R.string.remind_later)) {
                ParagraphText(stringResource(R.string.remind_later_body))

                val lastValidRemindValue = remember { mutableStateOf(5.0f) }
                val remindDays = remember { mutableStateOf("5") }
                val coroutineScope = rememberCoroutineScope()
                Button(
                    onClick = {
                        coroutineScope.launch {
                            pushNoticeReminderTime(context, lastValidRemindValue.value)
                        }
                        onExit()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp)
                ) {
                    Text(stringResource(R.string.remind_me_in_x))
                    BasicTextField(
                        value = remindDays.value,
                        onValueChange = {
                            remindDays.value = it

                            it.toFloatOrNull()
                                ?.let { lastValidRemindValue.value = it }
                        },
                        modifier = Modifier
                            .width(32.dp)
                            .border(Dp.Hairline, LocalContentColor.current)
                            .padding(4.dp),
                        textStyle = Typography.bodyMedium.copy(color = LocalContentColor.current),
                        cursorBrush = SolidColor(LocalContentColor.current),
                        keyboardOptions = KeyboardOptions.Default.copy(keyboardType = KeyboardType.Number)
                    )
                    Text(stringResource(R.string.in_x_days))
                }
            }
        }
    }
}


@Composable
fun PaymentScreenSwitch(
    settingsViewModel: SettingsViewModel = viewModel(),
    navController: NavHostController = rememberNavController(),
    onExit: () -> Unit = { },
    billing: BillingManager,
    startDestination: String = "pleasePay"
) {
    val isAlreadyPaid = useDataStore(IS_ALREADY_PAID)
    val hasSeenNotice = useDataStore(HAS_SEEN_PAID_NOTICE)
    val paymentDest = if (!isAlreadyPaid.value && hasSeenNotice.value) {
        "error"
    } else if (isAlreadyPaid.value && !hasSeenNotice.value) {
        "paid"
    } else {
        "pleasePay"
    }

    LaunchedEffect(paymentDest) {
        if (paymentDest != "pleasePay") {
            navController.navigate(
                paymentDest,
                NavOptions.Builder().setLaunchSingleTop(true).build()
            )
        }
    }

    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable("pleasePay") {
            PaymentScreen(settingsViewModel, navController, onExit, billing)
        }

        composable("paid") {
            PaymentThankYouScreen(onExit)
        }

        composable("error") {
            PaymentFailedScreen(onExit)
        }
    }
}