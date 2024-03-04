package org.futo.voiceinput

import android.content.Context
import android.inputmethodservice.InputMethodService
import android.os.Build
import android.text.InputType
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.view.inputmethod.InputMethodSubtype
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
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


@Composable
fun RecognizerInputMethodWindow(switchBack: (() -> Unit)? = null, onFinish: () -> Unit = { }, content: @Composable ColumnScope.() -> Unit) {
    UixThemeAuto {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .clickable(
                    enabled = true,
                    onClickLabel = null,
                    onClick = onFinish,
                    role = null,
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ),
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
        InnerRecognize(onFinish = { })
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
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                switchToPreviousInputMethod()
            } else {
                inputMethodManager.switchToLastInputMethod(window.window!!.attributes.token)
            }
        }

        var prevText: CharSequence? = null
        var nextText: CharSequence? = null
        override fun decodingStarted() {
            this@VoiceInputMethodService.currentInputConnection.also {
                prevText = it.getTextBeforeCursor(1, 0)
                nextText = it.getTextAfterCursor(1, 0)
            }
        }

        override fun sendResult(result: String) {
            this@VoiceInputMethodService.currentInputConnection.also {
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

                it.commitText(modifiedResult, 1)
            }
            onCancel()
        }

        override fun sendPartialResult(result: String): Boolean {
            if(this@VoiceInputMethodService.currentInputConnection != null) {
                this@VoiceInputMethodService.currentInputConnection.setComposingText(result, 1)
                return true
            } else {
                return false
            }
        }

        override fun requestPermission() {
            // We can't ask for permission from a service
            // TODO: We could launch an activity and request it that way

            permissionResultRejected()
        }

        @Composable
        override fun Window(onClose: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
            RecognizerInputMethodWindow(switchBack = onClose, onFinish = { finishRecognizerIfRecording() }) {
                content()
            }
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

        return composeView!!
    }

    override fun onCreateCandidatesView(): View? {
        // The candidates view shows potential word corrections or suggestions for the user to select.
        // Return null, as the voice input does not need this.
        return null
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

    override fun onDestroy() {
        super.onDestroy()

        println("Destroy")
        handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
    }
}