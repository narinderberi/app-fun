package com.yourdomain.throttlingapp

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.core.app.NotificationCompat
import com.google.common.util.concurrent.RateLimiter
import org.json.JSONArray
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException

class ThrottlingVpnService : VpnService(), Runnable {

    private var vpnInterface: ParcelFileDescriptor? = null
    private var vpnThread: Thread? = null
    @Volatile private var isRunning = false
    private var currentSpeedLimitBytesPerSec = 32768.0 // Default 32 KB/s

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, createNotification())

        // 1. Read configuration cached by ConfigWorker
        val prefs = getSharedPreferences("AppConfigPrefs", Context.MODE_PRIVATE)
        val configString = prefs.getString("cached_config_json", "[]") ?: "[]"
        val appsArray = JSONArray(configString)

        // Reset previous interface before rebuilding
        cleanup()

        // 2. Build VPN interface dynamically based on remote config
        val builder = Builder()
            .addAddress("10.0.0.2", 24)
            .addRoute("0.0.0.0", 0)
            .setSession("Xiaomi Security")

        var lowestSpeedKbps = Double.MAX_VALUE
        var appAdded = false

        for (i in 0 until appsArray.length()) {
            val appObj = appsArray.getJSONObject(i)
            val packageName = appObj.getString("package_name")
            val speedKbps = appObj.optDouble("speed_limit_kbps", 32.0)

            try {
                builder.addAllowedApplication(packageName)
                appAdded = true
                if (speedKbps < lowestSpeedKbps) {
                    lowestSpeedKbps = speedKbps
                }
            } catch (e: PackageManager.NameNotFoundException) {
                e.printStackTrace()
            }
        }

        // Fallback if no target apps exist or are installed locally
        if (!appAdded) {
            try {
                builder.addAllowedApplication("com.instagram.android")
            } catch (e: PackageManager.NameNotFoundException) {
                e.printStackTrace()
            }
            lowestSpeedKbps = 32.0
        }

        // Calculate rate limit in bytes per second (1 KB = 1024 bytes)
        currentSpeedLimitBytesPerSec = lowestSpeedKbps * 1024.0

        try {
            vpnInterface = builder.establish()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 3. Restart processing thread with updated parameters
        if (vpnThread != null && vpnThread!!.isAlive) {
            isRunning = false
            vpnThread?.interrupt()
        }

        isRunning = true
        vpnThread = Thread(this, "VPN-Packet-Shaper").apply { start() }

        return START_STICKY
    }

    override fun run() {
        val descriptor = vpnInterface?.fileDescriptor ?: return
        val inputStream = FileInputStream(descriptor)
        val outputStream = FileOutputStream(descriptor)

        // Dynamic rate limiter built from remotely fetched config
        val rateLimiter = RateLimiter.create(currentSpeedLimitBytesPerSec)
        val buffer = ByteArray(32768)

        try {
            while (isRunning && !Thread.currentThread().isInterrupted) {
                val length = inputStream.read(buffer)
                if (length > 0) {
                    rateLimiter.acquire(length)

                    /* 
                     * NOTE: In a full TUN/TAP proxy pipeline, packets are parsed,
                     * forwarded via a socket bound with protect(socket), and
                     * response packets are written back to outputStream.
                     */
                }
            }
        } catch (e: IOException) {
            e.printStackTrace()
        } finally {
            cleanup()
        }
    }

    private fun createNotification(): Notification {
        val channelId = "vpn_service_channel"
        val manager = getSystemService(NotificationManager::class.java)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "System Protection Engine",
                NotificationManager.IMPORTANCE_LOW
            )
            manager.createNotificationChannel(channel)
        }

        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("Xiaomi Security")
            .setContentText("Keeping your Xiaomi device secure")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        isRunning = false
        vpnThread?.interrupt()
        cleanup()
        super.onDestroy()
    }

    private fun cleanup() {
        try {
            vpnInterface?.close()
            vpnInterface = null
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    companion object {
        private const val NOTIFICATION_ID = 1001
    }
}