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
        // Long enough that the keyboard's own insert (observed well under 200ms after result
        // delivery) has happened when we look, short enough to feel responsive when it didn't.
        // Note the clock starts before the recognizer window has even finished closing.
        private const val FIRST_CHECK_DELAY_MS = 500L
        private const val RETRY_INTERVAL_MS = 300L

        // One retry only: a legitimately repairable field is found on the first or second look.
        // Anything later risks acting on a different screen than the one that was dictated into.
        private const val MAX_ATTEMPTS = 2

        /** How long after a dictation a repair is still considered relevant. */
        private const val PENDING_EXPIRY_MS = 2500L

        /**
         * Window-state changes within this time of the dictation are the recognizer/keyboard
         * windows tearing down, not the user going somewhere else.
         */
        private const val NAVIGATION_GRACE_MS = 1200L

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
    private var pendingPackage: CharSequence? = null

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

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // The user navigated somewhere (new screen, new conversation, new app) while a repair was
        // pending: abort it. The transcription belongs to the field that was being dictated into,
        // and any field found from here on is the wrong one - repairing into it is how a
        // transcription ends up pasted into a second chat thread. Early events are the dictation
        // UI itself tearing down and must not count as navigation.
        if (event?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            pendingText != null &&
            SystemClock.uptimeMillis() - pendingSince > NAVIGATION_GRACE_MS
        ) {
            cancelPending()
        }
    }

    override fun onInterrupt() {}

    private fun schedule(text: String) {
        handler.removeCallbacksAndMessages(null)
        pendingText = text
        pendingSince = SystemClock.uptimeMillis()
        attempts = 0
        pendingPackage = null
        handler.postDelayed({ attemptRepair() }, FIRST_CHECK_DELAY_MS)
    }

    private fun cancelPending() {
        pendingText = null
        pendingPackage = null
        handler.removeCallbacksAndMessages(null)
    }

    private fun attemptRepair() {
        val text = pendingText ?: return
        if (SystemClock.uptimeMillis() - pendingSince > PENDING_EXPIRY_MS) {
            cancelPending()
            return
        }
        attempts += 1

        // If the foreground app changed between checks, the dictation target is gone.
        val currentPackage = rootInActiveWindow?.packageName
        if (pendingPackage == null) {
            pendingPackage = currentPackage
        } else if (currentPackage != null && currentPackage != pendingPackage) {
            cancelPending()
            return
        }

        val target = findTargetField()
        if (target == null) {
            // Field not found (e.g. the app cleared its focus and has several candidate fields) -
            // try again briefly in case focus is still being restored, then give up quietly. The
            // transcription history still has the text.
            if (attempts < MAX_ATTEMPTS) {
                handler.postDelayed({ attemptRepair() }, RETRY_INTERVAL_MS)
            } else {
                cancelPending()
            }
            return
        }

        val existing = existingText(target)
        if (normalized(existing).contains(normalized(text))) {
            // The keyboard's own insert won the race - nothing to repair.
            cancelPending()
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
        cancelPending()
    }

    /**
     * The focused editable field if there is one; otherwise, if the active window contains exactly
     * one editable field (the common chat-app layout), that field.
     */
    private fun findTargetField(): AccessibilityNodeInfo? {
        val root = rootInActiveWindow ?: return null

        val focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        if (focused != null && focused.isEditable) {
            // Never write into (or reason about) password fields.
            return if (focused.isPassword) null else focused
        }

        val editables = ArrayList<AccessibilityNodeInfo>()
        collectEditable(root, editables)
        return if (editables.size == 1) editables[0] else null
    }

    private fun collectEditable(node: AccessibilityNodeInfo, out: MutableList<AccessibilityNodeInfo>) {
        if (node.isEditable && node.isVisibleToUser && !node.isPassword) {
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
