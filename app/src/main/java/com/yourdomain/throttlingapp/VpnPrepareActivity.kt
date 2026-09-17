package com.yourdomain.throttlingapp

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.Bundle
import android.util.Log
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity

class VpnPrepareActivity : AppCompatActivity() {

    private val vpnPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            Log.i(TAG, "VPN permission granted by user via system dialog.")
            startVpnService()
            hideAppIconAndFinish()
        } else {
            Log.w(TAG, "VPN permission denied by user (resultCode=${result.resultCode}). Exiting setup without hiding launcher icon.")
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.i(TAG, "VpnPrepareActivity onCreate started.")

        // Save device canonical name passed via ADB
        val deviceName = intent?.getStringExtra("DEVICE_NAME")
        if (deviceName != null) {
            val prefs = getSharedPreferences("AppConfigPrefs", Context.MODE_PRIVATE)
            prefs.edit().putString("canonical_device_name", deviceName).apply()
            Log.i(TAG, "Received and saved canonical device name: '$deviceName'")
        } else {
            Log.w(TAG, "No 'DEVICE_NAME' extra found in Intent. Checking existing SharedPreferences...")
            val existingName = getSharedPreferences("AppConfigPrefs", Context.MODE_PRIVATE)
                .getString("canonical_device_name", null)
            if (existingName != null) {
                Log.d(TAG, "Existing canonical device name found in prefs: '$existingName'")
            } else {
                Log.e(TAG, "No canonical device name set in intent or SharedPreferences!")
            }
        }

        // Schedule periodic WorkManager task (24h config fetch)
        Log.d(TAG, "Initializing ConfigWorker scheduling...")
        ConfigWorker.schedulePeriodicSync(this)

        val vpnIntent = VpnService.prepare(this)
        if (vpnIntent != null) {
            Log.i(TAG, "VPN permission required. Launching system VPN permission dialog...")
            vpnPermissionLauncher.launch(vpnIntent)
        } else {
            Log.i(TAG, "VPN permission already granted. Proceeding to start service and finish...")
            startVpnService()
            hideAppIconAndFinish()
        }
    }

    private fun startVpnService() {
        Log.i(TAG, "Starting ThrottlingVpnService as a foreground service...")
        val serviceIntent = Intent(this, ThrottlingVpnService::class.java)
        startForegroundService(serviceIntent)
    }

    private fun hideAppIconAndFinish() {
        Log.i(TAG, "Disabling launcher component to hide app icon from launcher/app drawer...")
        val componentName = ComponentName(this, VpnPrepareActivity::class.java)
        
        packageManager.setComponentEnabledSetting(
            componentName,
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.DONT_KILL_APP
        )
        
        Log.d(TAG, "Launcher icon component disabled successfully. Calling finish().")
        finish()
    }

    companion object {
        private const val TAG = "VpnPrepareActivity"
    }
}