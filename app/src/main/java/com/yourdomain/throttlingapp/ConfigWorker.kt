package com.yourdomain.throttlingapp

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.work.*
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit

class ConfigWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        Log.d(TAG, "Starting ConfigWorker execution...")

        val prefs = applicationContext.getSharedPreferences("AppConfigPrefs", Context.MODE_PRIVATE)
        val deviceName = prefs.getString("canonical_device_name", null)

        if (deviceName.isNullOrEmpty()) {
            Log.e(TAG, "Failed: 'canonical_device_name' is not set in SharedPreferences.")
            return Result.failure()
        }

        Log.i(TAG, "Configuring worker for canonical device name: '$deviceName'")

        val configUrl = "https://raw.githubusercontent.com/narinderberi/app-fun/refs/heads/main/config.json"

        return try {
            Log.d(TAG, "Fetching remote configuration from: $configUrl")
            val url = URL(configUrl)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 10000
            connection.readTimeout = 10000

            val responseCode = connection.responseCode
            Log.d(TAG, "HTTP response code: $responseCode")

            if (responseCode == HttpURLConnection.HTTP_OK) {
                val jsonString = connection.inputStream.bufferedReader().use { it.readText() }
                Log.d(TAG, "Successfully downloaded raw JSON payload (${jsonString.length} chars)")

                val rootJson = JSONObject(jsonString)
                
                // Log full root JSON payload
                Log.d(TAG, "Full rootJson structure: ${rootJson.toString(2)}")

                if (rootJson.has(deviceName)) {
                    val deviceConfig = rootJson.getJSONObject(deviceName)
                    val appsArray = deviceConfig.getJSONArray("throttled_apps")

                    Log.i(TAG, "Device entry found for '$deviceName'. Extracted ${appsArray.length()} throttled app rule(s).")
                    Log.d(TAG, "Target device config array: $appsArray")

                    // Save local config cache
                    prefs.edit().putString("cached_config_json", appsArray.toString()).apply()
                    Log.d(TAG, "Successfully updated 'cached_config_json' in SharedPreferences.")

                    // Restart VPN Service to apply updated routing & speed limits
                    Log.i(TAG, "Restarting ThrottlingVpnService to apply new rules...")
                    val vpnIntent = Intent(applicationContext, ThrottlingVpnService::class.java)
                    applicationContext.startForegroundService(vpnIntent)

                    Result.success()
                } else {
                    Log.w(TAG, "Key '$deviceName' not found in remote config JSON. Available top-level keys: ${rootJson.keys().asSequence().toList()}")
                    Result.failure()
                }
            } else {
                Log.e(TAG, "Failed to download config. Server returned HTTP error code: $responseCode")
                Result.retry()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception encountered during config fetch/processing: ${e.localizedMessage}", e)
            Result.retry()
        }
    }

    companion object {
        private const val TAG = "ConfigWorker"

        fun schedulePeriodicSync(context: Context) {
            Log.d(TAG, "Scheduling 24-hour periodic work request...")

            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val syncRequest = PeriodicWorkRequestBuilder<ConfigWorker>(24, TimeUnit.HOURS)
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "ConfigSyncWorker",
                ExistingPeriodicWorkPolicy.KEEP,
                syncRequest
            )

            Log.i(TAG, "Periodic ConfigSyncWorker successfully enqueued.")
        }
    }
}