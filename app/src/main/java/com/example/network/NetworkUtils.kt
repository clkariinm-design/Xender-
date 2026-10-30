package com.example.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.Locale

object NetworkUtils {

    const val DEFAULT_PORT = 8988

    /**
     * Resolves the primary local IPv4 address of the device (Wi-Fi or Hotspot AP interface).
     */
    fun getLocalIpAddress(): String {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces() ?: return "127.0.0.1"
            val candidates = mutableListOf<String>()

            for (networkInterface in interfaces) {
                // Ignore loopback or down interfaces
                if (networkInterface.isLoopback || !networkInterface.isUp) continue

                val name = networkInterface.name.lowercase(Locale.US)
                val addresses = networkInterface.inetAddresses

                for (inetAddress in addresses) {
                    if (!inetAddress.isLoopbackAddress && inetAddress is Inet4Address) {
                        val hostAddress = inetAddress.hostAddress ?: continue
                        // Prioritize wireless interfaces (wlan, ap, p2p)
                        if (name.contains("wlan") || name.contains("ap") || name.contains("p2p") || name.contains("swlan")) {
                            return hostAddress
                        }
                        candidates.add(hostAddress)
                    }
                }
            }

            if (candidates.isNotEmpty()) {
                return candidates.first()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return "127.0.0.1"
    }

    /**
     * Checks whether device is connected to a local Wi-Fi network.
     */
    fun isWifiConnected(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val network = cm.activeNetwork ?: return false
        val capabilities = cm.getNetworkCapabilities(network) ?: return false
        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }

    /**
     * Formats bytes to human-readable size (e.g. 14.5 MB).
     */
    fun formatFileSize(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt().coerceIn(0, units.size - 1)
        val value = bytes / Math.pow(1024.0, digitGroups.toDouble())
        return String.format(Locale.US, if (digitGroups == 0) "%.0f %s" else "%.1f %s", value, units[digitGroups])
    }

    /**
     * Formats speed in bytes/sec to human-readable speed (e.g. 18.2 MB/s).
     */
    fun formatSpeed(bytesPerSec: Long): String {
        if (bytesPerSec <= 0) return "0 B/s"
        return "${formatFileSize(bytesPerSec)}/s"
    }
}
