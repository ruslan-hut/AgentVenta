package ua.com.programmer.agentventa.di

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import com.bumptech.glide.Glide
import com.bumptech.glide.RequestManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.Dispatchers
import ua.com.programmer.agentventa.infrastructure.location.GeocodeHelperImpl
import ua.com.programmer.agentventa.data.local.database.AppDatabase
import ua.com.programmer.agentventa.infrastructure.location.GeocodeHelper
import ua.com.programmer.agentventa.infrastructure.license.LicenseManager
import ua.com.programmer.agentventa.infrastructure.logger.Logger
import ua.com.programmer.agentventa.presentation.common.viewmodel.BarcodeHandler
import ua.com.programmer.agentventa.presentation.common.viewmodel.GlideImageLoadingManager
import ua.com.programmer.agentventa.presentation.common.viewmodel.ImageLoadingManager
import ua.com.programmer.agentventa.utility.Utils
import ua.com.programmer.agentventa.utility.UtilsInterface
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
class GlobalModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return AppDatabase.getDatabase(context)
    }

    @Provides
    @Singleton
    fun providePreferences(@ApplicationContext context: Context): SharedPreferences {
        return PreferenceManager.getDefaultSharedPreferences(context)
    }

    @Provides
    @Singleton
    fun provideUtils(): UtilsInterface {
        return Utils()
    }

    @Provides
    @Singleton
    fun provideLicenseManager(): LicenseManager {
        return LicenseManager()
    }

    @Provides
    @Singleton
    fun provideGlide(@ApplicationContext context: Context): RequestManager {
        return Glide.with(context)
    }

    @Provides
    @Singleton
    fun provideGeocoder(@ApplicationContext context: Context): GeocodeHelper {
        return GeocodeHelperImpl(context)
    }

    @Provides
    @Singleton
    fun provideBarcodeHandler(): BarcodeHandler {
        return BarcodeHandler()
    }

    @Provides
    @Singleton
    fun provideImageLoadingManager(
        glide: RequestManager,
        logger: Logger
    ): ImageLoadingManager {
        return GlideImageLoadingManager(glide, logger)
    }
}

@Module
@InstallIn(SingletonComponent::class)
object CoroutineModule {
    @Provides
    @IoDispatcher
    fun provideIoDispatcher() = Dispatchers.IO

    annotation class IoDispatcher
}