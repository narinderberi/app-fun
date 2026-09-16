package com.yourdomain.throttlingapp

import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

class VpnPrepareActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val intent = VpnService.prepare(this)
        if (intent != null) {
            // Permission needed: trigger the system VPN permission dialog
            startActivityForResult(intent, REQUEST_CODE_VPN)
        } else {
            // Already prepared: start service directly and close
            startVpnService()
            finish()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CODE_VPN && resultCode == RESULT_OK) {
            startVpnService()
        }
        finish()
    }

    private fun startVpnService() {
        val serviceIntent = Intent(this, ThrottlingVpnService::class.java)
        startForegroundService(serviceIntent)
    }

    companion object {
        private const val REQUEST_CODE_VPN = 1001
    }
}