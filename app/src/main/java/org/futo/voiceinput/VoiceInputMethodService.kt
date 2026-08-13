package org.futo.voiceinput

import android.content.Context
import android.content.res.Configuration
import android.inputmethodservice.InputMethodService
import android.os.Build
import android.os.Handler
import android.os.Looper
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
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.findViewTreeSavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import org.futo.voiceinput.migration.scheduleModelMigrationJob
import org.futo.voiceinput.settings.pages.ConditionalUnpaidNoticeInVoiceInputWindow
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
fun RecognizerInputMethodWindow(switchBack: (() -> Unit)? = null, allowClick: Boolean = false, onPauseVAD: (Boolean) -> Unit = { }, onFinish: () -> Unit = { }, content: @Composable ColumnScope.() -> Unit) {
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

                content()
                Spacer(Modifier.height(navBarHeight()))
            }
        }
    }
}


@Preview
@Composable
fun RecognizeIMELoadingPreview() {
    RecognizerInputMethodWindow(switchBack = { }) {
        RecognizeLoadingCircle()
    }
}

@Preview
@Composable
fun PreviewRecognizeViewLoadedIME() {
    RecognizerInputMethodWindow(switchBack = { }) {
        InnerRecognize()
    }
}
@Preview
@Composable
fun PreviewRecognizeViewNoMicIME() {
    RecognizerInputMethodWindow(switchBack = { }) {
        RecognizeMicError(openSettings = { })
    }
}


val punctuationChars = setOf('!', '?', '.', ',')
class VoiceInputMethodService : InputMethodService(), LifecycleOwner, ViewModelStoreOwner,
    SavedStateRegistryOwner {
    companion object {
        private const val COMMIT_RETRY_INTERVAL_MS = 50L
        private const val COMMIT_TIMEOUT_MS = 1000L
    }

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
        mSavedStateRegistryController.performRestore(null)
        handleLifecycleEvent(Lifecycle.Event.ON_RESUME)

        scheduleUpdateCheckingJob(applicationContext)
        scheduleModelMigrationJob(applicationContext)
    }

    private val mainHandler = Handler(Looper.getMainLooper())

    /** Recognized text we have not managed to commit yet, if any. */
    private var pendingCommitText: String? = null

    private val recognizer = object : RecognizerView() {
        override val context: Context
            get() = this@VoiceInputMethodService
        override val lifecycleScope: LifecycleCoroutineScope
            get() = this@VoiceInputMethodService.lifecycle.coroutineScope

        private val currentContent: MutableState<@Composable () -> Unit> = mutableStateOf( { } )
        override fun setContent(content: @Composable () -> Unit) {
            currentContent.value = content
            composeView?.setContent { content() }
        }

        fun refreshContent() {
            composeView?.setContent { currentContent.value() }
        }

        override fun onCancel() {
            needsInitialization = true
            reset()
            pendingCommitText = null
            mainHandler.removeCallbacksAndMessages(null)
            switchBackToPreviousInputMethod()
        }

        var prevText: CharSequence? = null
        var nextText: CharSequence? = null
        override fun decodingStarted() {
            // Note: nothing emits RunState.StartedDecoding at the moment, so this never runs and
            // prevText/nextText stay null - the auto-space-after-punctuation path below is
            // currently dead.
            this@VoiceInputMethodService.currentInputConnection?.also {
                prevText = it.getTextBeforeCursor(1, 0)
                nextText = it.getTextAfterCursor(1, 0)
            }
        }

        override fun sendResult(result: String) {
            var modifiedResult = result

            // Insert space automatically if ended at punctuation
            // TODO: Could send text before cursor as whisper prompt

            if(!prevText.isNullOrBlank()) {
                val lastChar = prevText?.last()

                if (punctuationChars.contains(lastChar)) {
                    modifiedResult = " $result"
                }
            }

            /*
            if(!nextText.isNullOrBlank()) {
                val oldPunctuation = nextText?.first()
                val newPunctuation = result.last()

                if (punctuationChars.contains(oldPunctuation) && punctuationChars.contains(newPunctuation)) {
                    it.deleteSurroundingText(0, 1)
                }
            }
            */

            commitRecognizedText(modifiedResult)

            needsInitialization = true
            reset()
        }

        override fun sendPartialResult(result: String): Boolean {
            val ic = this@VoiceInputMethodService.currentInputConnection ?: return false
            ic.setComposingText(result, 1)
            return true
        }

        override fun requestPermission() {
            // We can't ask for permission from a service
            // TODO: We could launch an activity and request it that way

            permissionResultRejected()
        }

        @Composable
        override fun Window(onClose: () -> Unit, allowClick: Boolean, onPauseVAD: (Boolean) -> Unit, onFinish: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
            RecognizerInputMethodWindow(switchBack = onClose, onPauseVAD = onPauseVAD, onFinish = onFinish, allowClick = allowClick) {
                content()
            }
        }
    }

    private fun switchBackToPreviousInputMethod() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            switchToPreviousInputMethod()
        } else {
            window.window?.attributes?.token?.let {
                inputMethodManager.switchToLastInputMethod(it)
            }
        }
    }

    /**
     * Commit recognized text into the editor, then switch back to the previous keyboard.
     *
     * The editor's InputConnection is not necessarily usable the moment recognition finishes: the
     * target app may still be settling after the voice window came up, or input may have been
     * finished while we were decoding. commitText cannot tell us that - on the IME side it returns
     * as soon as the call is queued, and the app silently discards it if its InputConnection has
     * already been finished, which is how recognized text ends up disappearing without any error.
     *
     * So we do not rely on the return value. We only commit while we hold a connection that still
     * accepts a batch edit, retry briefly if we do not, and switch input methods only afterwards -
     * switching tears the connection down and would otherwise race the commit we just queued.
     */
    private fun commitRecognizedText(text: String, waitedMs: Long = 0L) {
        pendingCommitText = text

        val ic = currentInputConnection
        if (ic != null) {
            // beginBatchEdit returns false once the connection has been finished, which is the only
            // synchronous liveness signal an IME gets. Past the timeout we commit anyway rather
            // than throw the text away.
            val batched = ic.beginBatchEdit()
            if (batched || waitedMs >= COMMIT_TIMEOUT_MS) {
                pendingCommitText = null
                try {
                    // No finishComposingText() first: partial results are shown with
                    // setComposingText and commitText already replaces the composing region.
                    // Finishing it separately would leave the partial text behind and duplicate it.
                    ic.commitText(text, 1)
                } finally {
                    if (batched) ic.endBatchEdit()
                }

                mainHandler.post { switchBackToPreviousInputMethod() }
                return
            }
        }

        if (waitedMs >= COMMIT_TIMEOUT_MS) {
            println("Voice input: no usable InputConnection after ${waitedMs}ms, recognized text was not committed")
            pendingCommitText = null
            switchBackToPreviousInputMethod()
            return
        }

        mainHandler.postDelayed({
            if (pendingCommitText == text) {
                commitRecognizedText(text, waitedMs + COMMIT_RETRY_INTERVAL_MS)
            }
        }, COMMIT_RETRY_INTERVAL_MS)
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

        if(needsInitialization) {
            needsInitialization = false
            recognizer.reset()
            recognizer.init()
        } else {
            println("Continuing recording, likely due to landscape/portrait switch")
            recognizer.refreshContent()
        }
        // TODO: Idle state
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        println("Finish input view")
        recognizer.reset()

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

        pendingCommitText = null
        mainHandler.removeCallbacksAndMessages(null)

        println("Destroy")
        handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
    }
}