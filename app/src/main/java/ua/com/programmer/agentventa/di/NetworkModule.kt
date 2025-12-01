package ua.com.programmer.agentventa.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import ua.com.programmer.agentventa.data.remote.interceptor.HttpAuthInterceptor
import ua.com.programmer.agentventa.data.remote.api.HttpClientApi
import ua.com.programmer.agentventa.data.remote.TokenManager
import ua.com.programmer.agentventa.data.remote.TokenManagerImpl
import ua.com.programmer.agentventa.data.remote.interceptor.TokenRefresh
import ua.com.programmer.agentventa.infrastructure.logger.Logger
import ua.com.programmer.agentventa.domain.repository.UserAccountRepository
import ua.com.programmer.agentventa.domain.repository.WebSocketRepository
import ua.com.programmer.agentventa.data.repository.WebSocketRepositoryImpl
import ua.com.programmer.agentventa.domain.repository.SettingsSyncRepository
import ua.com.programmer.agentventa.data.repository.SettingsSyncRepositoryImpl
import ua.com.programmer.agentventa.domain.repository.DataExchangeRepository
import ua.com.programmer.agentventa.infrastructure.config.ApiKeyProvider
import com.google.gson.Gson
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
class NetworkModule {

    @Provides
    @Singleton
    fun provideInterceptor(): HttpAuthInterceptor {
        return HttpAuthInterceptor()
    }

    @Provides
    @Singleton
    fun provideAuthenticator(): TokenRefresh {
        return TokenRefresh()
    }

    @Provides
    @Singleton
    fun provideTokenManager(
        userAccountRepository: UserAccountRepository,
        logger: Logger
    ): TokenManager {
        return TokenManagerImpl(userAccountRepository, logger)
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(authInterceptor: HttpAuthInterceptor, authenticator: TokenRefresh): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .authenticator(authenticator)
            .build()
    }

    @Provides
    @Singleton
    fun provideRetrofitBuilder(okHttpClient: OkHttpClient): Retrofit.Builder {
        return Retrofit.Builder()
            .addConverterFactory(GsonConverterFactory.create())
            .client(okHttpClient)
    }

    @Provides
    @Singleton
    fun provideApiService(retrofit: Retrofit): HttpClientApi {
        return retrofit.create(HttpClientApi::class.java)
    }

    @Provides
    @Singleton
    fun provideApiKeyProvider(): ApiKeyProvider {
        return ApiKeyProvider()
    }

    @Provides
    @Singleton
    fun provideWebSocketRepository(
        okHttpClient: OkHttpClient,
        logger: Logger,
        apiKeyProvider: ApiKeyProvider,
        dataExchangeRepository: DataExchangeRepository,
        userAccountRepository: ua.com.programmer.agentventa.domain.repository.UserAccountRepository
    ): WebSocketRepository {
        return WebSocketRepositoryImpl(
            okHttpClient = okHttpClient,
            logger = logger,
            apiKeyProvider = apiKeyProvider,
            dataExchangeRepository = dataExchangeRepository,
            userAccountRepository = userAccountRepository
        )
    }

    @Provides
    @Singleton
    fun provideSettingsSyncRepository(
        webSocketRepository: WebSocketRepository,
        logger: Logger
    ): SettingsSyncRepository {
        return SettingsSyncRepositoryImpl(
            webSocketRepository = webSocketRepository,
            gson = Gson(),
            logger = logger
        )
    }

}