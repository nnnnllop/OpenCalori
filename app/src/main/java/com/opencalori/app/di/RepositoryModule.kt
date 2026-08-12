package com.opencalori.app.di

import com.opencalori.app.data.backup.BackupManager
import com.opencalori.app.data.backup.BackupRepository
import com.opencalori.app.data.image.AndroidImageProcessor
import com.opencalori.app.data.image.ImageProcessor
import com.opencalori.app.data.preferences.ApiKeyStore
import com.opencalori.app.data.preferences.UserPreferencesStore
import com.opencalori.app.data.repository.AiRepositoryImpl
import com.opencalori.app.data.repository.MealRepositoryImpl
import com.opencalori.app.data.repository.ProductRepositoryImpl
import com.opencalori.app.domain.repository.AiRepository
import com.opencalori.app.domain.repository.ApiConfigStore
import com.opencalori.app.domain.repository.MealRepository
import com.opencalori.app.domain.repository.ProductRepository
import com.opencalori.app.domain.repository.UserPreferences
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import java.time.Clock
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class IoDispatcher

@Module
@InstallIn(SingletonComponent::class)
object DispatcherModule {

    @Provides
    @IoDispatcher
    fun provideIoDispatcher(): CoroutineDispatcher = Dispatchers.IO

    /** Injected so "today" can be pinned in tests instead of drifting with the wall clock. */
    @Provides
    fun provideClock(): Clock = Clock.systemDefaultZone()
}

/**
 * ViewModels depend on the interfaces in `domain.repository`, never on these
 * implementations - that is what makes the whole UI layer unit-testable with fakes.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindMealRepository(impl: MealRepositoryImpl): MealRepository

    @Binds
    @Singleton
    abstract fun bindProductRepository(impl: ProductRepositoryImpl): ProductRepository

    @Binds
    @Singleton
    abstract fun bindAiRepository(impl: AiRepositoryImpl): AiRepository

    @Binds
    @Singleton
    abstract fun bindUserPreferences(impl: UserPreferencesStore): UserPreferences

    @Binds
    @Singleton
    abstract fun bindApiConfigStore(impl: ApiKeyStore): ApiConfigStore

    @Binds
    @Singleton
    abstract fun bindImageProcessor(impl: AndroidImageProcessor): ImageProcessor

    @Binds
    @Singleton
    abstract fun bindBackupManager(impl: BackupRepository): BackupManager
}
