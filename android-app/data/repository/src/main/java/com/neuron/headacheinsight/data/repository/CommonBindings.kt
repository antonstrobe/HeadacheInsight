package com.neuron.headacheinsight.data.repository

import android.content.Context
import com.neuron.headacheinsight.core.common.DefaultDispatchersProvider
import com.neuron.headacheinsight.core.common.DefaultTimeProvider
import com.neuron.headacheinsight.core.common.DeviceContextProvider
import com.neuron.headacheinsight.core.common.DispatchersProvider
import com.neuron.headacheinsight.core.common.NetworkSnapshotProvider
import com.neuron.headacheinsight.core.common.TimeProvider
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class CommonBindingsModule {
    @Binds
    abstract fun bindDispatchersProvider(impl: DefaultDispatchersProvider): DispatchersProvider

    @Binds
    abstract fun bindTimeProvider(impl: DefaultTimeProvider): TimeProvider

    @Binds
    abstract fun bindDeviceContextProvider(impl: AndroidDeviceContextProvider): DeviceContextProvider

    companion object {
        @Provides
        @Singleton
        fun provideNetworkSnapshotProvider(
            @ApplicationContext context: Context,
        ): NetworkSnapshotProvider = AndroidNetworkSnapshotProvider(context)
    }
}
