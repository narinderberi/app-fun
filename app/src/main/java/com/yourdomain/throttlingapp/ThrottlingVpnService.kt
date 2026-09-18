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
import android.util.Log
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

    // Protocol Constants
    private val PROTOCOL_UDP = 17
    private val PORT_QUIC = 443

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
            .setSession("Oppo Security")

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
                    
                    // Filter and handle UDP datagrams
                    val isAllowed = processOutboundPacket(buffer, length)
                    if (!isAllowed) {
                        // Drop packet (e.g., QUIC Port 443 blocked to force TCP fallback)
                        continue
                    }

                    // Enforce byte-rate limit on allowed TCP/UDP packets
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

    /**
     * Inspects IPv4 headers to parse UDP protocols and enforce QUIC/UDP policies.
     * Returns true if packet should be processed; false to drop.
     */
    private fun processOutboundPacket(buffer: ByteArray, length: Int): Boolean {
        if (length < 20) return true // Too short to parse IPv4 header, pass through

        // IPv4 Header Length (IHL is bits 0-3 of byte 0, value in 32-bit words)
        val ihl = (buffer[0].toInt() and 0x0F) * 4
        
        // Protocol byte is located at offset 9 in IPv4 header
        val protocol = buffer[9].toInt() and 0xFF

        if (protocol == PROTOCOL_UDP) {
            if (length < ihl + 8) return true // Invalid UDP packet length

            // Destination Port is located at bytes 2-3 inside the UDP header
            val destPort = ((buffer[ihl + 2].toInt() and 0xFF) shl 8) or (buffer[ihl + 3].toInt() and 0xFF)

            // Block QUIC (UDP Port 443) to force YouTube/Instagram/Chrome to downgrade to TCP (HTTPS)
            if (destPort == PORT_QUIC) {
                Log.i(TAG, "QUIC packet detected on port 443 -> Dropping to force TCP fallback.")
                return false
            }

            Log.d(TAG, "UDP Packet allowed: Port $destPort ($length bytes)")
        }

        return true
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
            .setContentTitle("Oppo Security")
            .setContentText("Keeping your Oppo device secure")
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
        private const val TAG = "ThrottlingVpnService"
    }
}