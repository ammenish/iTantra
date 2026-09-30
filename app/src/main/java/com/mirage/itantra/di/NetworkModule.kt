package com.mirage.itantra.di

import com.mirage.itantra.data.network.wifidirect.WifiDirectTransport
import com.mirage.itantra.domain.network.P2pTransport
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class NetworkModule {

    @Binds
    abstract fun bindP2pTransport(
        impl: WifiDirectTransport
    ): P2pTransport
}
