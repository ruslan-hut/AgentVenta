package ua.com.programmer.agentventa.http

import com.google.gson.JsonObject
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

interface HttpClientApi {

    @GET("check/{id}")
    suspend fun check(@Path("id") userId: String): Map<String,Any>

    @GET("get/{type}/{token}{more}")
    suspend fun get(@Path("type") type: String,
                    @Path("token") token: String,
                    @Path("more") more: String): Map<String,Any>

    @POST("post/{token}")
    suspend fun post(@Path("token") token: String, @Body data: JsonObject): Map<String,Any>

    @GET("document/{type}/{guid}/{token}")
    suspend fun getDocumentContent(@Path("type") type: String,
                                   @Path("guid") guid: String,
                                   @Path("token") token: String,): Map<String,Any>

    @GET("print/{guid}")
    suspend fun getPrintData(@Path("guid") guid: String): Map<String,Any>

}