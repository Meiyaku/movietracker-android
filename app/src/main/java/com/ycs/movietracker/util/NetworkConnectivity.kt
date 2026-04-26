package com.ycs.movietracker.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Returns true when the device has an active network with internet capability.
 * Recomposes automatically as connectivity changes.
 */
@Composable
fun rememberIsOnline(): Boolean {
    val context = LocalContext.current
    val connectivityManager = remember(context) {
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    }

    // Default true: avoids a synchronous system-service call on the main thread during
    // composition. LaunchedEffect corrects it immediately on the IO dispatcher.
    var isOnline by remember { mutableStateOf(true) }

    LaunchedEffect(connectivityManager) {
        isOnline = withContext(Dispatchers.IO) {
            connectivityManager.getNetworkCapabilities(connectivityManager.activeNetwork)
                ?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        }
    }

    DisposableEffect(connectivityManager) {
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) { isOnline = true }
            override fun onLost(network: Network) {
                // Only go offline if no other network still has internet
                val caps = connectivityManager.getNetworkCapabilities(connectivityManager.activeNetwork)
                isOnline = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
            }
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                isOnline = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            }
        }

        connectivityManager.registerNetworkCallback(request, callback)
        onDispose { connectivityManager.unregisterNetworkCallback(callback) }
    }

    return isOnline
}
