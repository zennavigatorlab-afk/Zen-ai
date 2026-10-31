package com.example.phone

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.provider.Settings
import java.io.File

data class StorageTelemetry(
    val availableBytes: Long,
    val totalBytes: Long,
    val appDataBytes: Long,
    val formattedAvailable: String,
    val formattedTotal: String,
    val formattedAppData: String,
    val usedPercentage: Int
)

data class NetworkTelemetry(
    val isOnline: Boolean,
    val networkType: String
)

data class BatteryTelemetry(
    val percentage: Int,
    val isCharging: Boolean,
    val technology: String
)

data class DeviceTelemetry(
    val deviceModel: String,
    val androidVersion: String,
    val sdkInt: Int,
    val securityPatch: String
)

class PhoneActionManager(private val context: Context) {

    fun getStorageTelemetry(): StorageTelemetry {
        return try {
            val path = context.filesDir.absolutePath
            val stat = StatFs(path)
            val available = stat.availableBytes
            val total = stat.totalBytes
            val used = total - available
            val pct = if (total > 0) ((used.toDouble() / total.toDouble()) * 100).toInt() else 0

            var appSize = 0L
            context.filesDir?.let { appSize += getFolderSize(it) }
            val dbFile = context.getDatabasePath("zen_ai_vault.db")
            if (dbFile.exists()) {
                appSize += dbFile.length()
            }

            StorageTelemetry(
                availableBytes = available,
                totalBytes = total,
                appDataBytes = appSize,
                formattedAvailable = formatBytes(available),
                formattedTotal = formatBytes(total),
                formattedAppData = formatBytes(appSize),
                usedPercentage = pct
            )
        } catch (_: Exception) {
            StorageTelemetry(0, 0, 0, "0 MB", "0 MB", "0 KB", 0)
        }
    }

    fun getNetworkTelemetry(): NetworkTelemetry {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val network = cm.activeNetwork ?: return NetworkTelemetry(false, "Offline")
            val caps = cm.getNetworkCapabilities(network) ?: return NetworkTelemetry(false, "Offline")
            val hasInternet = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            val type = when {
                caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Cellular"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
                else -> "Connected"
            }
            NetworkTelemetry(hasInternet, if (hasInternet) type else "No Internet")
        } catch (_: Exception) {
            NetworkTelemetry(false, "Unknown")
        }
    }

    fun getBatteryTelemetry(): BatteryTelemetry {
        return try {
            val ifilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            val batteryStatus = context.registerReceiver(null, ifilter)
            val level = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
            val status = batteryStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
            val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                    status == BatteryManager.BATTERY_STATUS_FULL
            val pct = if (level >= 0 && scale > 0) (level * 100 / scale) else 0
            val tech = batteryStatus?.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY) ?: "Li-ion"

            BatteryTelemetry(pct, isCharging, tech)
        } catch (_: Exception) {
            BatteryTelemetry(0, false, "Unknown")
        }
    }

    fun getDeviceTelemetry(): DeviceTelemetry {
        val patch = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Build.VERSION.SECURITY_PATCH
        } else "N/A"
        return DeviceTelemetry(
            deviceModel = "${Build.MANUFACTURER.replaceFirstChar { if (it.isLowerCase()) it.titlecase(java.util.Locale.getDefault()) else it.toString() }} ${Build.MODEL}",
            androidVersion = "Android ${Build.VERSION.RELEASE}",
            sdkInt = Build.VERSION.SDK_INT,
            securityPatch = patch
        )
    }

    fun openSystemSettings() {
        val intent = Intent(Settings.ACTION_SETTINGS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }

    fun openStorageSettings() {
        val intent = Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        try {
            context.startActivity(intent)
        } catch (_: Exception) {
            openSystemSettings()
        }
    }

    fun openBatterySettings() {
        val intent = Intent(Intent.ACTION_POWER_USAGE_SUMMARY).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        try {
            context.startActivity(intent)
        } catch (_: Exception) {
            openSystemSettings()
        }
    }

    fun shareDiagnosticsReport(report: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "ZEN AI Diagnostics Report")
            putExtra(Intent.EXTRA_TEXT, report)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(Intent.createChooser(intent, "Share Diagnostics").apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        })
    }

    private fun getFolderSize(dir: File): Long {
        var size = 0L
        val files = dir.listFiles() ?: return 0L
        for (f in files) {
            size += if (f.isDirectory) getFolderSize(f) else f.length()
        }
        return size
    }

    private fun formatBytes(bytes: Long): String {
        val kb = bytes / 1024.0
        val mb = kb / 1024.0
        val gb = mb / 1024.0
        return when {
            gb >= 1.0 -> String.format("%.2f GB", gb)
            mb >= 1.0 -> String.format("%.1f MB", mb)
            kb >= 1.0 -> String.format("%.1f KB", kb)
            else -> "$bytes B"
        }
    }
}
