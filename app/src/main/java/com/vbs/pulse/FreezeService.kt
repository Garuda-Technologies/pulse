package com.vbs.pulse

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * This is how Greenify freezes apps without root: an accessibility service opens
 * the target's Settings page and presses "Force stop" for you.
 */
class FreezeService : AccessibilityService() {

    companion object {
        var instance: FreezeService? = null
        var pending: String? = null
        private var step = 0
        private var ticks = 0
    }

    override fun onServiceConnected() {
        instance = this
    }

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }

    override fun onInterrupt() {}

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (pending == null) return
        val pkg = event?.packageName?.toString() ?: return
        if (!pkg.contains("settings")) return

        val root = rootInActiveWindow ?: return
        ticks++
        if (ticks > 60) {
            reset()
            return
        }

        when (step) {
            0 -> {
                val node = clickable(find(root, "Force stop") ?: find(root, "FORCE STOP"))
                if (node != null && node.isEnabled) {
                    node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    step = 1
                }
            }
            1 -> {
                val ok = clickable(find(root, "OK") ?: find(root, "Ok"))
                if (ok != null) {
                    ok.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    reset()
                }
            }
        }
    }

    private fun reset() {
        pending = null
        step = 0
        ticks = 0
    }

    private fun find(root: AccessibilityNodeInfo, text: String): AccessibilityNodeInfo? {
        val list = root.findAccessibilityNodeInfosByText(text) ?: return null
        return list.firstOrNull { it.isClickable } ?: list.firstOrNull()
    }

    private fun clickable(start: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        var n = start
        var guard = 0
        while (n != null && guard < 6) {
            if (n.isClickable) return n
            n = n.parent
            guard++
        }
        return start
    }
}
