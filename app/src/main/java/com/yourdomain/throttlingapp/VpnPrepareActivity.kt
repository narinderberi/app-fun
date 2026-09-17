package com.yourdomain.throttlingapp

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity

class VpnPrepareActivity : AppCompatActivity() {

    private val vpnPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            startVpnService()
            hideAppIconAndFinish()
        } else {
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Save device canonical name passed via ADB
        intent?.getStringExtra("DEVICE_NAME")?.let { deviceName ->
            val prefs = getSharedPreferences("AppConfigPrefs", Context.MODE_PRIVATE)
            prefs.edit().putString("canonical_device_name", deviceName).apply()
        }

        // Schedule periodic WorkManager task (24h config fetch)
        ConfigWorker.schedulePeriodicSync(this)

        val intent = VpnService.prepare(this)
        if (intent != null) {
            vpnPermissionLauncher.launch(intent)
        } else {
            startVpnService()
            hideAppIconAndFinish()
        }
    }

    private fun startVpnService() {
        val serviceIntent = Intent(this, ThrottlingVpnService::class.java)
        startForegroundService(serviceIntent)
    }

    private fun hideAppIconAndFinish() {
        val componentName = ComponentName(this, VpnPrepareActivity::class.java)
        packageManager.setComponentEnabledSetting(
            componentName,
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.DONT_KILL_APP
        )
        finish()
    }
}