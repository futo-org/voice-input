package org.futo.voiceinput

import android.accessibilityservice.AccessibilityService
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Last line of defense for the dictation-drop bug: the ACTION_RECOGNIZE_SPEECH contract gives the
 * recognizer no way to know whether the keyboard's insert actually landed, and with some
 * keyboard/app combinations it silently doesn't (the keyboard writes into an InputConnection that
 * the editor has already invalidated - see RecognizeActivity for the full story). This service,
 * when the user enables it in Accessibility settings, checks the focused text field shortly after
 * every dictation and inserts the transcription directly if it never arrived. The insertion goes
 * through the accessibility API, which does not care about InputConnection lifetimes or window
 * focus races, so it works even in apps whose fields drop focus during dictation.
 *
 * Entirely passive when the keyboard's own insert succeeds: the repair runs only if the focused
 * field does not already contain the transcription.
 */
class VoiceRepairAccessibilityService : AccessibilityService() {
    companion object {
        private const val FIRST_CHECK_DELAY_MS = 800L
        private const val RETRY_INTERVAL_MS = 500L
        private const val MAX_ATTEMPTS = 4

        /** How long after a dictation a repair is still considered relevant. */
        private const val PENDING_EXPIRY_MS = 6000L

        private var instance: VoiceRepairAccessibilityService? = null

        /**
         * Called from RecognizerView right before the result is sent. No-op unless the user has
         * enabled the service.
         */
        fun scheduleRepair(text: String) {
            if (text.isBlank()) return
            instance?.schedule(text)
        }
    }

    private lateinit var handler: Handler
    private var pendingText: String? = null
    private var pendingSince: Long = 0L
    private var attempts: Int = 0

    override fun onServiceConnected() {
        super.onServiceConnected()
        handler = Handler(mainLooper)
        instance = this
    }

    override fun onDestroy() {
        if (instance === this) {
            instance = null
        }
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {}

    private fun schedule(text: String) {
        handler.removeCallbacksAndMessages(null)
        pendingText = text
        pendingSince = SystemClock.uptimeMillis()
        attempts = 0
        handler.postDelayed({ attemptRepair() }, FIRST_CHECK_DELAY_MS)
    }

    private fun attemptRepair() {
        val text = pendingText ?: return
        if (SystemClock.uptimeMillis() - pendingSince > PENDING_EXPIRY_MS) {
            pendingText = null
            return
        }
        attempts += 1

        val target = findTargetField()
        if (target == null) {
            // Field not found (e.g. the app cleared its focus and has several candidate fields) -
            // try again briefly in case focus is still being restored, then give up quietly. The
            // transcription history still has the text.
            if (attempts < MAX_ATTEMPTS) {
                handler.postDelayed({ attemptRepair() }, RETRY_INTERVAL_MS)
            } else {
                pendingText = null
            }
            return
        }

        val existing = existingText(target)
        if (normalized(existing).contains(normalized(text))) {
            // The keyboard's own insert won the race - nothing to repair.
            pendingText = null
            return
        }

        val repaired = if (existing.isBlank()) {
            text
        } else {
            existing.trimEnd() + " " + text
        }

        val args = Bundle()
        args.putCharSequence(
            AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
            repaired
        )
        target.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
        pendingText = null
    }

    /**
     * The focused editable field if there is one; otherwise, if the active window contains exactly
     * one editable field (the common chat-app layout), that field.
     */
    private fun findTargetField(): AccessibilityNodeInfo? {
        val root = rootInActiveWindow ?: return null

        val focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        if (focused != null && focused.isEditable) {
            return focused
        }

        val editables = ArrayList<AccessibilityNodeInfo>()
        collectEditable(root, editables)
        return if (editables.size == 1) editables[0] else null
    }

    private fun collectEditable(node: AccessibilityNodeInfo, out: MutableList<AccessibilityNodeInfo>) {
        if (node.isEditable && node.isVisibleToUser) {
            out.add(node)
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            collectEditable(child, out)
        }
    }

    private fun existingText(node: AccessibilityNodeInfo): String {
        // An empty field reports its hint as text; treat that as empty or the hint would be
        // prepended to the repaired transcription.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && node.isShowingHintText) {
            return ""
        }
        return node.text?.toString() ?: ""
    }

    private fun normalized(s: String): String {
        return s.lowercase().replace(Regex("\\s+"), " ").trim()
    }
}
