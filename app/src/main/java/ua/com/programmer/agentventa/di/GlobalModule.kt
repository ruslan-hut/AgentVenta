package ua.com.programmer.agentventa.di

import android.content.Context
import com.bumptech.glide.Glide
import com.bumptech.glide.RequestManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.Dispatchers
import ua.com.programmer.agentventa.geo.GeocodeHelperImpl
import ua.com.programmer.agentventa.dao.AppDatabase
import ua.com.programmer.agentventa.data.DataBase
import ua.com.programmer.agentventa.geo.GeocodeHelper
import ua.com.programmer.agentventa.settings.AppSettings
import ua.com.programmer.agentventa.utility.Utils
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
class GlobalModule {

    @Provides
    @Singleton
    fun provideAppSettings(@ApplicationContext context: Context): AppSettings {
        return AppSettings.getInstance(context)
    }

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): DataBase {
        return DataBase.getInstance(context)
    }

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return AppDatabase.getDatabase(context)
    }

    @Provides
    @Singleton
    fun provideUtils(): Utils {
        return Utils()
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
}

@Module
@InstallIn(SingletonComponent::class)
object CoroutineModule {
    @Provides
    @IoDispatcher
    fun provideIoDispatcher() = Dispatchers.IO

    annotation class IoDispatcher
}