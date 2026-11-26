package com.ylowstone.tv.player.stabilizer

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Build

class NetworkSwitchGuard(
    context: Context,
    private val onNetworkSwitched: (networkLabel: String) -> Unit
) {
    private val connectivityManager =
        context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    @Volatile
    private var lastNetworkHandle: Long? = null

    @Volatile
    private var isRegistered: Boolean = false

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            val handle = network.networkHandle
            val previous = lastNetworkHandle
            lastNetworkHandle = handle

            if (previous != null && previous != handle) {
                onNetworkSwitched(resolveNetworkLabel(network))
            }
        }
    }

    fun start() {
        if (isRegistered) return
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                lastNetworkHandle = connectivityManager.activeNetwork?.networkHandle
                connectivityManager.registerDefaultNetworkCallback(callback)
                isRegistered = true
            }
        }
    }

    fun stop() {
        if (!isRegistered) return
        runCatching {
            connectivityManager.unregisterNetworkCallback(callback)
        }
        isRegistered = false
        lastNetworkHandle = null
    }

    private fun resolveNetworkLabel(network: Network): String {
        val caps = connectivityManager.getNetworkCapabilities(network) ?: return "Jaringan baru"
        return when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Seluler"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
            else -> "Jaringan baru"
        }
    }
}
