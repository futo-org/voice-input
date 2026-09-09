// TODO: Does langauge selector even work here?
package org.futo.voiceinput

import android.content.Context
import android.content.res.Configuration
import android.inputmethodservice.InputMethodService
import android.os.Build
import android.text.InputType
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.view.inputmethod.InputMethodSubtype
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleCoroutineScope
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.coroutineScope
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.findViewTreeViewModelStoreOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.findViewTreeSavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import org.futo.voiceinput.settings.pages.ConditionalUnpaidNoticeInVoiceInputWindow
import org.futo.voiceinput.shared.ModelDoesNotExistException
import org.futo.voiceinput.shared.RecognizerView
import org.futo.voiceinput.shared.RecognizerViewListener
import org.futo.voiceinput.shared.ui.MicrophoneDeviceState
import org.futo.voiceinput.theme.UixThemeAuto
import org.futo.voiceinput.updates.scheduleUpdateCheckingJob

val SupportsNavbarExtension = Build.VERSION.SDK_INT >= 28

@Composable
fun navBarHeight(): Dp = with(LocalDensity.current) {
    if(SupportsNavbarExtension) {
        WindowInsets.systemBars.getBottom(this).toDp()
    } else {
        0.dp
    }
}

@Composable
fun RecognizerInputMethodWindow(switchBack: (() -> Unit)? = null, allowClick: Boolean = false, onPauseVAD: (Boolean) -> Unit = { }, onFinish: () -> Unit = { }, content: @Composable () -> Unit) {
    UixThemeAuto(false) {
        Surface(
            modifier = Modifier
                .recognizerSurfaceClickable(disabled = !allowClick, onPauseVAD = onPauseVAD, onFinish = onFinish)
                .fillMaxWidth()
                .wrapContentHeight(),
            color = MaterialTheme.colorScheme.surface
        ) {
            val icon = painterResource(id = R.drawable.futo_o)
            val bgIconTint = MaterialTheme.colorScheme.outline

            Column(
                modifier = Modifier.padding(0.dp, 0.dp, 0.dp, 64.dp).drawBehind {
                    with(icon) {
                        translate(left = -icon.intrinsicSize.width/2, top = -icon.intrinsicSize.height/2) {
                            translate(left = size.width / 3, top = size.height / 2) {
                                scale(scaleX = 1.3f, scaleY = 1.3f) {
                                    draw(icon.intrinsicSize, colorFilter = ColorFilter.tint(bgIconTint))
                                }

                            }
                        }
                    }
                }
            ) {

                val context = LocalContext.current
                Box(modifier = Modifier.fillMaxWidth()) {
                    Box(modifier = Modifier.align(Alignment.CenterStart)) {
                        ConditionalUnpaidNoticeInVoiceInputWindow(switchBack)
                    }

                    Box(modifier = Modifier.align(Alignment.CenterEnd)) {
                        if (switchBack != null) {
                            IconButton(
                                onClick = switchBack
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = stringResource(R.string.cancel),
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        } else {
                            Spacer(modifier = Modifier.height(32.dp))
                        }
                    }
                }

                Box(modifier = Modifier.padding(12.dp)) {
                    
                }

                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    content()
                }
                Spacer(Modifier.height(navBarHeight()))
            }
        }
    }
}


val punctuationChars = setOf('!', '?', '.', ',')
class VoiceInputMethodService : InputMethodService(), LifecycleOwner, ViewModelStoreOwner,
    SavedStateRegistryOwner {
    private val mSavedStateRegistryController = SavedStateRegistryController.create(this)

    override val savedStateRegistry: SavedStateRegistry
        get() = mSavedStateRegistryController.savedStateRegistry

    private val mLifecycleRegistry = LifecycleRegistry(this)

    override val lifecycle
        get() = mLifecycleRegistry

    private val store = ViewModelStore()
    override val viewModelStore
        get() = store

    private fun handleLifecycleEvent(event: Lifecycle.Event) =
        mLifecycleRegistry.handleLifecycleEvent(event)

    private val inputMethodManager: InputMethodManager
        get() = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager

    override fun onCreate() {
        super.onCreate()

        if(recognizer == null) initRecognizerView()

        mSavedStateRegistryController.performRestore(null)
        handleLifecycleEvent(Lifecycle.Event.ON_RESUME)

        scheduleUpdateCheckingJob(applicationContext)
    }


    private var recognizer: RecognizerView? = null
    private var onPermissionGranted: () -> Unit = {}
    private var onPermissionRejected: () -> Unit = {}

    private var textComposed = ""
    private var pendingPreText = ""
    private var pendingPostText = ""
    private var composingStartPos = -100

    fun initRecognizerView() {
        val cancelSelf = {
            needsInitialization = true
            recognizer?.reset()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                if (!switchToPreviousInputMethod()) {
                    switchToNextInputMethod(false)
                }
            } else {
                if (!inputMethodManager.switchToLastInputMethod(window.window!!.attributes.token)) {
                    inputMethodManager.switchToNextInputMethod(
                        window.window!!.attributes.token,
                        false
                    )
                }
            }
        }
        val settings = loadRecognizerSettings()
        try {
            recognizer = RecognizerView(
                context = this@VoiceInputMethodService,
                listener = object : RecognizerViewListener {
                    override fun cancelled() {
                        cancelSelf()
                    }

                    override fun recordingStarted(device: MicrophoneDeviceState) {

                    }

                    override fun finished(result: String) {
                        currentInputConnection?.let {
                            it.commitText(result + pendingPostText, 1)
                        }
                        cancelled()
                    }

                    override fun partialResult(result: String) {
                        if(result == textComposed) return

                        currentInputConnection?.let {
                            if(pendingPreText.isNotEmpty()) {
                                it.commitText(pendingPreText, 1)
                                composingStartPos += pendingPreText.length
                                pendingPreText = ""
                            }
                            it.setComposingText(result, 1)
                            textComposed = result
                        }
                    }

                    override fun requestPermission(
                        onGranted: () -> Unit,
                        onRejected: () -> Unit
                    ): Boolean {
                        // TODO: Launch activity to request permission, steal this code from keyboard
                        return false
                    }

                    override fun openSettings() {

                    }

                },
                settings = loadRecognizerSettings(),
                lifecycleScope = lifecycleScope,
                modelManager = CrashLoggingApplication.getModelManager(this)!!,
            )
        } catch(e: ModelDoesNotExistException) {
            val models = listOf(
                settings.modelRunConfiguration.primaryModel
            ) + settings.modelRunConfiguration.languageSpecificModels.values

            startModelDownloadActivity(models)

            cancelSelf()
        }
    }

    private fun setOwners() {
        val decorView = window.window?.decorView
        if (decorView?.findViewTreeLifecycleOwner() == null) {
            decorView?.setViewTreeLifecycleOwner(this)
        }
        if (decorView?.findViewTreeViewModelStoreOwner() == null) {
            decorView?.setViewTreeViewModelStoreOwner(this)
        }
        if (decorView?.findViewTreeSavedStateRegistryOwner() == null) {
            decorView?.setViewTreeSavedStateRegistryOwner(this)
        }

        window.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private var composeView: ComposeView? = null

    override fun onCreateInputView(): View {
        // The input view is the main view where the user inputs text via keyclicks, handwriting,
        // gestures, or in this case there is a voice input menu.
        composeView = ComposeView(this).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setParentCompositionContext(null)

            this@VoiceInputMethodService.setOwners()
        }

        updateNavigationBarVisibility()
        return composeView!!
    }

    override fun onCreateCandidatesView(): View? {
        // The candidates view shows potential word corrections or suggestions for the user to select.
        // Return null, as the voice input does not need this.
        return null
    }

    private fun updateNavigationBarVisibility() {
        if(SupportsNavbarExtension) {
            window.window?.let { window ->
                WindowCompat.setDecorFitsSystemWindows(window, false)
            }
        }
    }

    private var needsInitialization = true
    override fun onStartInputView(info: EditorInfo, restarting: Boolean) {
        super.onStartInputView(info, restarting)

        when (info.inputType and InputType.TYPE_MASK_CLASS) {
            InputType.TYPE_CLASS_NUMBER -> {
                // number
            }
            InputType.TYPE_CLASS_DATETIME -> {
                // date time ??
            }
            InputType.TYPE_CLASS_PHONE -> {
                // phone number
                // could add whisper prompt like "My phone number is "
            }
            InputType.TYPE_CLASS_TEXT -> {
                // text :)
                if(info.inputType == InputType.TYPE_TEXT_VARIATION_PASSWORD) {
                    // ...
                }
            }
        }

        // Move cursor to end of selection if one is active
        currentInputConnection?.let { ic ->
            var txtBefore: String? = null
            var txtAfter: String? = null

            if(info.initialSelStart != info.initialSelEnd && (info.initialSelStart != -1) && (info.initialSelEnd > 0)) {
                ic.setSelection(info.initialSelEnd, info.initialSelEnd)
            }
            if(info.initialSelStart != -1) composingStartPos = info.initialSelStart

            txtBefore = ic.getTextBeforeCursor(1, 0)?.toString()
            txtAfter = ic.getTextAfterCursor(1, 0)?.toString()

            if(txtBefore?.lastOrNull()?.isWhitespace() == false) pendingPreText = " "
            if(txtAfter?.getOrNull(0)?.isLetter() == true) pendingPostText = " "
        }

        if(needsInitialization) {
            needsInitialization = false

            if(recognizer == null) initRecognizerView()
            recognizer?.reset()
            recognizer?.start()
        } else {
            println("Continue due to config change / screen rotation")
            // TODO: Screen rotation
        }
        // TODO: Idle state

        composeView!!.setContent {
            RecognizerInputMethodWindow(
                switchBack = { recognizer?.cancel() },
                onPauseVAD = { recognizer?.inhibitAutoStop(it) },
                onFinish = { recognizer?.finish() },
                allowClick = recognizer?.isFinishable?.value ?: false
            ) {
                recognizer?.Content()
            }
        }
    }

    override fun onUpdateSelection(
        oldSelStart: Int,
        oldSelEnd: Int,
        newSelStart: Int,
        newSelEnd: Int,
        candidatesStart: Int,
        candidatesEnd: Int
    ) {
        super.onUpdateSelection(
            oldSelStart,
            oldSelEnd,
            newSelStart,
            newSelEnd,
            candidatesStart,
            candidatesEnd
        )

        // User selected a word. Try to recover by some chance
        if(newSelStart != newSelEnd && composingStartPos > 0) {
            currentInputConnection?.let {
                it.setSelection(composingStartPos + textComposed.length, composingStartPos + textComposed.length)
                if(it.getTextBeforeCursor(textComposed.length, 0) == textComposed) {
                    it.setComposingRegion(composingStartPos, composingStartPos + textComposed.length)
                    // recovered
                }
            }
        }
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        println("Finish input view")
        recognizer?.reset()

        needsInitialization = true
    }

    override fun onCurrentInputMethodSubtypeChanged(newSubtype: InputMethodSubtype) {
        super.onCurrentInputMethodSubtypeChanged(newSubtype)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        updateNavigationBarVisibility()
    }

    override fun onDestroy() {
        super.onDestroy()

        println("Destroy")
        handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
    }
}