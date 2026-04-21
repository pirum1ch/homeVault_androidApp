package com.homevalt.app.data.network

import com.homevalt.app.data.network.dto.AuthResponse
import com.homevalt.app.data.network.dto.FileDto
import com.homevalt.app.data.network.dto.HealthResponse
import com.homevalt.app.data.network.dto.PageResponse
import com.homevalt.app.data.network.dto.LoginRequest
import com.homevalt.app.data.network.dto.UploadResponse
import okhttp3.MultipartBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Streaming

interface HomeVaultApiService {
    @POST("/api/auth/login")
    suspend fun login(@Body request: LoginRequest): Response<AuthResponse>

    @GET("/api/files")
    suspend fun listFiles(
        @Query("page") page: Int = 0,
        @Query("size") size: Int = 20
    ): Response<PageResponse<FileDto>>

    @GET("/api/files/{id}")
    suspend fun getFileDetail(@Path("id") id: String): Response<FileDto>

    @DELETE("/api/files/{id}")
    suspend fun deleteFile(@Path("id") id: String): Response<Unit>

    @Multipart
    @POST("/api/upload")
    suspend fun uploadFile(@Part file: MultipartBody.Part): Response<UploadResponse>

    @GET("/api/files/{id}/preview")
    @Streaming
    suspend fun downloadFile(
        @Path("id") id: String,
        @Query("download") download: Boolean = true
    ): Response<ResponseBody>

    @GET("/health/nas")
    suspend fun checkHealth(): Response<HealthResponse>
}
