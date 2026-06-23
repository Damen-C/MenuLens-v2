package com.menulens.app.data

import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.http.Header
import retrofit2.http.Body
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import okhttp3.ResponseBody

interface ScanApiService {
    @Multipart
    @POST("v1/scan_menu")
    suspend fun scanMenu(
        @Header("Authorization") authorization: String?,
        @Part image: MultipartBody.Part,
        @Part("target_lang") targetLang: RequestBody,
        @Part("device_id") deviceId: RequestBody,
        @Part("app_version") appVersion: RequestBody,
        @Part("timezone") timezone: RequestBody,
        @Part("request_id") requestId: RequestBody
    ): ScanMenuResponseDto

    @POST("v1/generate_dish_image")
    suspend fun generateDishImage(
        @Header("Authorization") authorization: String?,
        @Body request: GenerateDishImageRequestDto
    ): ResponseBody
}
