package ua.com.programmer.agentventa.fiscal.checkbox

import okhttp3.ResponseBody
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Streaming

interface CheckboxApi {

    @POST("api/v1/cashier/signinPinCode")
    suspend fun cashierLogin(@Body pin: CashierPin): Map<String,Any>?

    @POST("api/v1/cashier/signout")
    suspend fun cashierLogout(): Map<String,Any>?

    @GET("api/v1/cash-registers/info")
    suspend fun checkStatus(): Map<String,Any>?

    @GET("api/v1/cashier/shift")
    suspend fun getCashierShift(): Map<String,Any>?

    @GET("api/v1/shifts/{id}")
    suspend fun getShift(@Path("id") id: String): Map<String,Any>?

    @POST("api/v1/shifts")
    suspend fun createShift(@Body shift: Shift): Map<String,Any>?

    @POST("api/v1/shifts/close")
    suspend fun closeShift(): Map<String,Any>?

    @POST("api/v1/receipts/sell")
    suspend fun createReceipt(@Body receipt: Receipt): Map<String,Any>?

    @POST("api/v1/receipts/service")
    suspend fun createServiceReceipt(@Body receipt: ServiceReceipt): Map<String,Any>?

    @GET("api/v1/receipts/{id}")
    suspend fun getReceipt(@Path("id") id: String): Map<String,Any>?

    @Streaming
    @GET("api/v1/receipts/{id}/png")
    suspend fun getReceiptPng(@Path("id") id: String): ResponseBody

    @Streaming
    @GET("api/v1/receipts/{id}/text")
    suspend fun getReceiptTxt(@Path("id") id: String, @Query("width") width: Int): ResponseBody

    @POST("api/v1/reports")
    suspend fun createXReport(): Map<String,Any>?

    @Streaming
    @GET("api/v1/reports/{id}/png")
    suspend fun getReportPng(@Path("id") id: String): ResponseBody

    @Streaming
    @GET("api/v1/reports/{id}/text")
    suspend fun getReportText(@Path("id") id: String, @Query("width") width: Int): ResponseBody
}