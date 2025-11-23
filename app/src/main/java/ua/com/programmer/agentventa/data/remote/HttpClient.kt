package ua.com.programmer.agentventa.data.remote

import okhttp3.Interceptor
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import ua.com.programmer.agentventa.data.remote.interceptor.TokenRefresh
import javax.inject.Inject

class HttpClient @Inject constructor(authenticator: TokenRefresh, authInterceptor: Interceptor) {

    private var baseUrl: String = ""
    private var retrofit: Retrofit? = null

    private val client = OkHttpClient.Builder()
        .addInterceptor(authInterceptor)
        .authenticator(authenticator)
        .build()

    fun build(url: String): Retrofit {
        if (retrofit != null && baseUrl == url) return retrofit as Retrofit
        baseUrl = url
        retrofit = Retrofit.Builder()
            .addConverterFactory(GsonConverterFactory.create())
            .baseUrl(baseUrl)
            .client(client)
            .build()
        return retrofit as Retrofit
    }

}