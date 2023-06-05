package org.futo.voiceinput

import android.content.Context
import android.inputmethodservice.InputMethodService
import android.os.Build
import android.text.InputType
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodSubtype
import androidx.activity.ComponentActivity
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.findViewTreeViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.findViewTreeSavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner


@Composable
fun InputView(switchBack: (() -> Unit)?) {
    MaterialTheme {
        Text("Hello world!")

        if(switchBack != null) {
            Button(onClick = switchBack) {
                Text("Go back")
            }
        }
    }
}

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

    override fun onCreate() {
        super.onCreate()
        mSavedStateRegistryController.performRestore(null)
        handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
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
    }

    private lateinit var composeView: ComposeView
    override fun onCreateInputView(): View {
        // The input view is the main view where the user inputs text via keyclicks, handwriting,
        // gestures, or in this case there is a voice input menu.
        composeView = ComposeView(this).apply {
            val switchBack = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                if(shouldOfferSwitchingToNextInputMethod()) {
                    fun() {
                        switchToNextInputMethod(false)
                    }
                } else {
                    null
                }
            } else {
                null
            }


            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setParentCompositionContext(null)

            setContent {
                InputView(switchBack = switchBack)
            }
            this@VoiceInputMethodService.setOwners()
        }

        return composeView
    }

    override fun onCreateCandidatesView(): View? {
        // The candidates view shows potential word corrections or suggestions for the user to select.
        // Return null, as the voice input does not need this.
        return null
    }

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
    }

    override fun onCurrentInputMethodSubtypeChanged(newSubtype: InputMethodSubtype) {
        super.onCurrentInputMethodSubtypeChanged(newSubtype)
    }

    override fun onFinishInput() {
        super.onFinishInput()
    }

    override fun onDestroy() {
        super.onDestroy()

        handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
    }
}