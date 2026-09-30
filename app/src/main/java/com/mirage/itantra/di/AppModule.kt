package com.mirage.itantra.di

import com.mirage.itantra.data.repository.MessageRepositoryImpl
import com.mirage.itantra.data.repository.SettingsRepositoryImpl
import com.mirage.itantra.domain.repository.MessageRepository
import com.mirage.itantra.domain.repository.SettingsRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module binding repository interfaces to implementations.
 *
 * This enables swapping implementations (e.g., for testing)
 * without changing dependent classes.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class AppModule {

    @Binds
    @Singleton
    abstract fun bindMessageRepository(
        impl: MessageRepositoryImpl
    ): MessageRepository

    @Binds
    @Singleton
    abstract fun bindSettingsRepository(
        impl: SettingsRepositoryImpl
    ): SettingsRepository

    @Binds
    @Singleton
    abstract fun bindTranslationEngine(
        impl: com.mirage.itantra.data.ml.MLKitTranslationEngine
    ): com.mirage.itantra.domain.usecase.ml.TranslationEngine
}
