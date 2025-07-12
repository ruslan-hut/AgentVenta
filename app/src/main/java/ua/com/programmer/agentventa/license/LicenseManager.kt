package ua.com.programmer.agentventa.license

import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import ua.com.programmer.agentventa.BuildConfig
import ua.com.programmer.agentventa.dao.cloud.CUserAccount
import ua.com.programmer.agentventa.dao.entity.LogEvent
import ua.com.programmer.agentventa.utility.XMap
import java.net.ProtocolException
import java.net.SocketTimeoutException

class LicenseManager {

    private var api: LicenseApi? = null
    private var retrofit: Retrofit? = null
    private var sendLog: Boolean = false

    private fun build(): LicenseApi {
        if (api != null) return api as LicenseApi
        val okBuilder = OkHttpClient.Builder()
            .addInterceptor {chain ->

                val builder = chain.request().newBuilder()
                builder
                    .addHeader("X-Client-Name", "ua.com.programmer.agentventa")
                    .addHeader("X-Client-Version", BuildConfig.VERSION_NAME)

                builder.addHeader("Authorization", "Bearer ${BuildConfig.APPLICATION_ID}")

                val request = builder.build()
                chain.proceed(request)
            }

        if (BuildConfig.DEBUG) {
            val logInterceptor = HttpLoggingInterceptor()
            logInterceptor.level = HttpLoggingInterceptor.Level.BODY
            okBuilder.addInterceptor(logInterceptor)
        }
        val okHttp = okBuilder.build()

        retrofit = Retrofit.Builder()
            .addConverterFactory(GsonConverterFactory.create())
            .baseUrl(BuildConfig.APPLICATION_ID)
            .client(okHttp)
            .build()

        api = retrofit?.create(LicenseApi::class.java) as LicenseApi

        return api as LicenseApi
    }

    suspend fun getLicense(userAccount: CUserAccount) {
        val response = callApi {
            build().keyInfo(userAccount)
        }
        sendLog = response.getBoolean("send_log")
        val message = response.getString("message")
        if (message.isNotEmpty()) {
            Log.i("LicenseManager", message)
        }
    }

    suspend fun log(event: LogEvent) {
        if (!sendLog) return
        callApi {
            build().log(event)
        }
    }

    private suspend fun callApi(action: suspend () -> Map<String, Any>?): XMap {
        val response = try {
            action()
        } catch (e: HttpException) {
            readErrorMessage(e)
        } catch (e: ProtocolException) {
            if (e.message?.contains("HTTP 205") == true) {
                mapOf("message" to "")
            } else {
                mapOf("message" to e)
            }
        } catch (_: SocketTimeoutException) {
            mapOf("message" to "Таймаут з'єднання з сервером ліцензування")
        } catch (e: Exception) {
            mapOf("message" to e)
        }
        return XMap(response ?: mapOf("message" to "Немає відповіді від сервера ліцензування"))
    }

    private fun readErrorMessage(e: HttpException): Map<String,String> {
        val errorBody = e.response()?.errorBody()?.string()
        return if (errorBody != null) {
            val errorMap = XMap(errorBody)
            mapOf("message" to errorMap.getString("message"))
        } else {
            mapOf("message" to e.message())
        }
    }

}