package com.homevalt.app.data.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class NetworkMonitor(context: Context) {
    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val _isWifiConnected = MutableStateFlow(false)
    val isWifiConnected: StateFlow<Boolean> = _isWifiConnected.asStateFlow()

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
            _isWifiConnected.value = caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
        }
        override fun onLost(network: Network) {
            _isWifiConnected.value = false
        }
    }

    init {
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        connectivityManager.registerNetworkCallback(request, networkCallback)
        val caps = connectivityManager.getNetworkCapabilities(connectivityManager.activeNetwork)
        _isWifiConnected.value = caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
    }

    fun isCurrentlyWifiConnected(): Boolean = _isWifiConnected.value
}
