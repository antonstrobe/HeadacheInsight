package com.neuron.headacheinsight.core.common

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import java.util.Locale
import java.util.TimeZone
import javax.inject.Inject

interface DispatchersProvider {
    val io: CoroutineDispatcher
    val default: CoroutineDispatcher
    val main: CoroutineDispatcher
}

class DefaultDispatchersProvider @Inject constructor() : DispatchersProvider {
    override val io: CoroutineDispatcher = Dispatchers.IO
    override val default: CoroutineDispatcher = Dispatchers.Default
    override val main: CoroutineDispatcher = Dispatchers.Main
}

interface TimeProvider {
    fun now(): Instant
    fun timeZoneId(): String
    fun localeTag(): String
}

class DefaultTimeProvider @Inject constructor() : TimeProvider {
    override fun now(): Instant = Clock.System.now()

    override fun timeZoneId(): String = TimeZone.getDefault().id

    override fun localeTag(): String = Locale.getDefault().toLanguageTag()
}

data class NetworkSnapshot(
    val connected: Boolean,
    val validated: Boolean,
    val transport: String,
)

interface NetworkSnapshotProvider {
    fun currentSnapshot(): NetworkSnapshot
}

data class DeviceContextSnapshot(
    val locale: String,
    val timezone: String,
    val network: NetworkSnapshot,
    val deviceModel: String,
    val osVersion: String,
)

interface DeviceContextProvider {
    fun snapshot(): DeviceContextSnapshot
}

fun String.redactSensitive(): String = when {
    length <= 8 -> "***"
    else -> "${take(2)}***${takeLast(2)}"
}
