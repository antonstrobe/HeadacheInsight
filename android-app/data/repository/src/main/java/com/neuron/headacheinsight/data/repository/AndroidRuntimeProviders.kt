package com.neuron.headacheinsight.data.repository

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import com.neuron.headacheinsight.core.common.DeviceContextProvider
import com.neuron.headacheinsight.core.common.DeviceContextSnapshot
import com.neuron.headacheinsight.core.common.NetworkSnapshot
import com.neuron.headacheinsight.core.common.NetworkSnapshotProvider
import com.neuron.headacheinsight.core.common.TimeProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AndroidNetworkSnapshotProvider @Inject constructor(
    @ApplicationContext private val context: Context,
) : NetworkSnapshotProvider {
    override fun currentSnapshot(): NetworkSnapshot {
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = manager.activeNetwork
        val capabilities = manager.getNetworkCapabilities(network)
        val transport = when {
            capabilities == null -> "none"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "wifi"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "cellular"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "ethernet"
            else -> "other"
        }
        return NetworkSnapshot(
            connected = capabilities != null,
            validated = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true,
            transport = transport,
        )
    }
}

@Singleton
class AndroidDeviceContextProvider @Inject constructor(
    private val timeProvider: TimeProvider,
    private val networkSnapshotProvider: NetworkSnapshotProvider,
) : DeviceContextProvider {
    override fun snapshot(): DeviceContextSnapshot = DeviceContextSnapshot(
        locale = timeProvider.localeTag(),
        timezone = timeProvider.timeZoneId(),
        network = networkSnapshotProvider.currentSnapshot(),
        deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}",
        osVersion = "Android ${Build.VERSION.RELEASE}",
    )
}
