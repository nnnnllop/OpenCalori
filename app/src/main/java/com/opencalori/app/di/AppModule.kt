package com.opencalori.app.di

import android.content.Context
import androidx.room.Room
import com.opencalori.app.data.local.AppDatabase
import com.opencalori.app.data.local.ProductDatabase
import com.opencalori.app.data.local.dao.MealDao
import com.opencalori.app.data.local.dao.ProductDao
import com.opencalori.app.data.local.dao.WeightDao
import com.opencalori.app.data.network.OpenAiClient
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.NAME)
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    fun provideMealDao(db: AppDatabase): MealDao = db.mealDao()

    @Provides
    fun provideWeightDao(db: AppDatabase): WeightDao = db.weightDao()

    @Provides
    @Singleton
    fun provideProductDatabase(@ApplicationContext context: Context): ProductDatabase =
        Room.databaseBuilder(context, ProductDatabase::class.java, "products.db")
            .createFromAsset(ProductDatabase.ASSET_PATH)
            .build()

    @Provides
    fun provideProductDao(db: ProductDatabase): ProductDao = db.productDao()

    @Provides
    @Singleton
    fun provideOpenAiClient(): OpenAiClient = OpenAiClient()
}
