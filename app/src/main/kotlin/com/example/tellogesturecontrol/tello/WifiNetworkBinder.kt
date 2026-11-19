package com.example.tellogesturecontrol.tello

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiManager
import android.provider.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Monitors Wi-Fi connectivity and binds the app process to the Wi-Fi [Network]
 * so UDP traffic to `192.168.10.1` succeeds even when Tello's AP has no internet
 * and cellular data is active.
 */
class WifiNetworkBinder(private val context: Context) {

    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val wifiManager =
        context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

    private val _isWifiAvailable = MutableStateFlow(false)
    val isWifiAvailable: StateFlow<Boolean> = _isWifiAvailable.asStateFlow()

    private val _isBoundToWifi = MutableStateFlow(false)
    val isBoundToWifi: StateFlow<Boolean> = _isBoundToWifi.asStateFlow()

    private val _currentSsid = MutableStateFlow<String?>(null)
    val currentSsid: StateFlow<String?> = _currentSsid.asStateFlow()

    private var currentWifiNetwork: Network? = null
    private var registered = false

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            currentWifiNetwork = network
            _isWifiAvailable.value = true
            refreshSsid()
            bindToCurrentWifi()
        }

        override fun onLost(network: Network) {
            if (currentWifiNetwork == network) {
                currentWifiNetwork = null
                _isWifiAvailable.value = false
                _isBoundToWifi.value = false
                _currentSsid.value = null
                connectivityManager.bindProcessToNetwork(null)
            }
        }

        override fun onCapabilitiesChanged(
            network: Network,
            networkCapabilities: NetworkCapabilities
        ) {
            refreshSsid()
        }
    }

    fun start() {
        if (registered) return
        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .build()
        connectivityManager.registerNetworkCallback(request, networkCallback)
        registered = true
        refreshSsid()
    }

    fun stop() {
        if (!registered) return
        try {
            connectivityManager.unregisterNetworkCallback(networkCallback)
        } catch (_: Exception) {
        }
        connectivityManager.bindProcessToNetwork(null)
        _isBoundToWifi.value = false
        registered = false
    }

    fun bindToCurrentWifi(): Boolean {
        val network = currentWifiNetwork ?: findActiveWifiNetwork()
        return if (network != null) {
            currentWifiNetwork = network
            val bound = connectivityManager.bindProcessToNetwork(network)
            _isBoundToWifi.value = bound
            _isWifiAvailable.value = true
            refreshSsid()
            bound
        } else {
            _isBoundToWifi.value = false
            false
        }
    }

    fun unbind() {
        connectivityManager.bindProcessToNetwork(null)
        _isBoundToWifi.value = false
    }

    @Suppress("DEPRECATION")
    fun refreshSsid() {
        try {
            val info = wifiManager.connectionInfo
            val rawSsid = info?.ssid?.trim('"')
            _currentSsid.value = if (rawSsid.isNullOrBlank() || rawSsid == "<unknown ssid>") {
                null
            } else {
                rawSsid
            }
        } catch (_: SecurityException) {
            _currentSsid.value = null
        }
    }

    private fun findActiveWifiNetwork(): Network? {
        @Suppress("DEPRECATION")
        for (network in connectivityManager.allNetworks) {
            val caps = connectivityManager.getNetworkCapabilities(network) ?: continue
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                return network
            }
        }
        return null
    }

    fun openWifiSettings() {
        val intent = Intent(Settings.ACTION_WIFI_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}
