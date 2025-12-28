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

    /**
     * Provides OkHttpClient for WebSocket connections.
     * This client does NOT include HttpAuthInterceptor to preserve
     * the Bearer token format required by the relay server.
     * WebSocket authentication uses: Bearer <API_KEY>:<DEVICE_UUID>
     */
    @Provides
    @Singleton
    @WebSocketClient
    fun provideWebSocketOkHttpClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .pingInterval(30, java.util.concurrent.TimeUnit.SECONDS)
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
        @WebSocketClient okHttpClient: OkHttpClient,
        logger: Logger,
        apiKeyProvider: ApiKeyProvider,
        dataExchangeRepository: DataExchangeRepository,
        userAccountRepository: UserAccountRepository
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
    fun provideGson(): Gson {
        return Gson()
    }

    @Provides
    @Singleton
    fun provideSettingsSyncRepository(
        webSocketRepository: WebSocketRepository,
        logger: Logger,
        gson: Gson
    ): SettingsSyncRepository {
        return SettingsSyncRepositoryImpl(
            webSocketRepository = webSocketRepository,
            gson = gson,
            logger = logger
        )
    }

}