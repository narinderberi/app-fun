import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.util.Log

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return

        if (action == Intent.ACTION_LOCKED_BOOT_COMPLETED ||
            action == Intent.ACTION_BOOT_COMPLETED ||
            action == Intent.ACTION_MY_PACKAGE_REPLACED) {

            // 1. Get the device-protected context for Direct Boot safety
            val targetContext = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && !context.isDeviceProtectedStorage) {
                context.createDeviceProtectedStorageContext()
            } else {
                context
            }

            // 2. Check if VPN was previously authorized by the user
            val vpnPrepareIntent = VpnService.prepare(targetContext)
            if (vpnPrepareIntent != null) {
                // VPN is not prepared yet; cannot start background service silently
                Log.e("BootReceiver", "VPN Service not prepared by user yet.")
                return
            }

            // 3. Start the service safely
            val vpnIntent = Intent(targetContext, ThrottlingVpnService::class.java)
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    targetContext.startForegroundService(vpnIntent)
                } else {
                    targetContext.startService(vpnIntent)
                }
            } catch (e: Exception) {
                // Catches ForegroundServiceStartNotAllowedException on Android 12+
                Log.e("BootReceiver", "Failed to start VPN service on boot", e)
            }
        }
    }
}