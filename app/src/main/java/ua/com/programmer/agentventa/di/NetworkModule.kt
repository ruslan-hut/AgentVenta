package ua.com.programmer.agentventa.di

import android.content.SharedPreferences
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.dnsoverhttps.DnsOverHttps
import retrofit2.Retrofit
import java.net.InetAddress
import java.util.concurrent.TimeUnit
import retrofit2.converter.gson.GsonConverterFactory
import ua.com.programmer.agentventa.data.remote.interceptor.HttpAuthInterceptor
import ua.com.programmer.agentventa.data.remote.api.DebugLogApi
import ua.com.programmer.agentventa.data.remote.api.HttpClientApi
import ua.com.programmer.agentventa.data.remote.api.RelayApi
import ua.com.programmer.agentventa.data.remote.TokenManager
import ua.com.programmer.agentventa.data.remote.TokenManagerImpl
import ua.com.programmer.agentventa.data.remote.interceptor.TokenRefresh
import ua.com.programmer.agentventa.infrastructure.logger.Logger
import ua.com.programmer.agentventa.domain.repository.UserAccountRepository
import ua.com.programmer.agentventa.domain.repository.DataExchangeRepository
import ua.com.programmer.agentventa.infrastructure.config.ApiKeyProvider
import ua.com.programmer.agentventa.infrastructure.config.CachingDns
import com.google.gson.Gson
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
class NetworkModule {

    /**
     * DNS-over-HTTPS resolver against Cloudflare's 1.1.1.1. The internal
     * OkHttp client is deliberately *not* shared with the app's other
     * clients (and does NOT use [CachingDns]) — that would create a
     * dependency cycle. Bootstrap addresses are IP literals, so this
     * resolver works when the system resolver is broken — which is the
     * whole point. Used only as a tier-2 fallback by [CachingDns].
     */
    @Provides
    @Singleton
    fun provideDnsOverHttps(): DnsOverHttps {
        val bootstrapClient = OkHttpClient.Builder()
            .connectTimeout(3, TimeUnit.SECONDS)
            .readTimeout(3, TimeUnit.SECONDS)
            .callTimeout(5, TimeUnit.SECONDS)
            .build()
        return DnsOverHttps.Builder()
            .client(bootstrapClient)
            .url("https://1.1.1.1/dns-query".toHttpUrl())
            .bootstrapDnsHosts(
                InetAddress.getByName("1.1.1.1"),
                InetAddress.getByName("1.0.0.1"),
                InetAddress.getByName("2606:4700:4700::1111"),
                InetAddress.getByName("2606:4700:4700::1001"),
            )
            .build()
    }

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
    fun provideOkHttpClient(
        authInterceptor: HttpAuthInterceptor,
        authenticator: TokenRefresh,
        cachingDns: CachingDns,
    ): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .authenticator(authenticator)
            .dns(cachingDns)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .callTimeout(60, TimeUnit.SECONDS)
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
    fun provideGson(): Gson {
        return Gson()
    }

    /**
     * OkHttpClient for the device-log upload endpoint. No HttpAuthInterceptor
     * (would overwrite the Bearer header), no TokenRefresh (not relevant). Short
     * timeouts so a stuck server doesn't block the uploader behind a giant
     * connect/read; we'd rather fail fast and retry on the next tickle.
     */
    @Provides
    @Singleton
    @DebugLogClient
    fun provideDebugLogOkHttpClient(cachingDns: CachingDns): OkHttpClient {
        return OkHttpClient.Builder()
            .dns(cachingDns)
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .callTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    @Provides
    @Singleton
    @DebugLogClient
    fun provideDebugLogRetrofit(
        @DebugLogClient okHttpClient: OkHttpClient,
        apiKeyProvider: ApiKeyProvider
    ): Retrofit {
        // Resolve base URL at provide-time. ApiKeyProvider exposes the host name
        // (no scheme); we build a https:// URL from it. If the host is empty,
        // fall back to a placeholder — the uploader will check apiKeyProvider
        // again and skip uploads when no host is configured.
        val host = apiKeyProvider.backendHost.ifBlank { "localhost" }
        val baseUrl = if (host.startsWith("http://") || host.startsWith("https://")) {
            if (host.endsWith("/")) host else "$host/"
        } else {
            "https://$host/"
        }
        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .addConverterFactory(GsonConverterFactory.create())
            .client(okHttpClient)
            .build()
    }

    @Provides
    @Singleton
    fun provideDebugLogApi(@DebugLogClient retrofit: Retrofit): DebugLogApi {
        return retrofit.create(DebugLogApi::class.java)
    }

    /**
     * OkHttpClient for the relay device-REST sync. No HttpAuthInterceptor /
     * TokenRefresh (auth is the per-call Bearer apiKey:deviceUuid header).
     * Longer timeouts than the debug-log client because catalog pulls and
     * base64 image uploads can be sizeable.
     */
    @Provides
    @Singleton
    @RelayClient
    fun provideRelayOkHttpClient(cachingDns: CachingDns): OkHttpClient {
        return OkHttpClient.Builder()
            .dns(cachingDns)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .callTimeout(120, TimeUnit.SECONDS)
            .build()
    }

    @Provides
    @Singleton
    @RelayClient
    fun provideRelayRetrofit(
        @RelayClient okHttpClient: OkHttpClient,
        apiKeyProvider: ApiKeyProvider
    ): Retrofit {
        // Base URL from the predefined backend host (same host as the WS relay).
        val host = apiKeyProvider.backendHost.ifBlank { "localhost" }
        val baseUrl = if (host.startsWith("http://") || host.startsWith("https://")) {
            if (host.endsWith("/")) host else "$host/"
        } else {
            "https://$host/"
        }
        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .addConverterFactory(GsonConverterFactory.create())
            .client(okHttpClient)
            .build()
    }

    @Provides
    @Singleton
    fun provideRelayApi(@RelayClient retrofit: Retrofit): RelayApi {
        return retrofit.create(RelayApi::class.java)
    }

}