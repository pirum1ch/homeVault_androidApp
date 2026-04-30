package com.homevalt.app.data.repository

import com.homevalt.app.data.network.JwtInterceptor
import com.homevalt.app.data.network.RetrofitClient
import com.homevalt.app.data.network.dto.NasConnectionRequest
import com.homevalt.app.data.network.dto.NasConnectionResponse

class NasRepository(
    private val networkSwitcher: NetworkSwitcher,
    private val jwtInterceptor: JwtInterceptor
) {
    private suspend fun api() = RetrofitClient.create(networkSwitcher.getActiveBaseUrl(), jwtInterceptor)

    suspend fun getWebDavConnection(): Result<NasConnectionResponse?> = try {
        val response = api().getNasConnections()
        if (response.isSuccessful) {
            Result.success(response.body()?.firstOrNull { it.type.equals("WEBDAV", ignoreCase = true) })
        } else {
            Result.failure(Exception("${response.code()} ${response.message()}"))
        }
    } catch (e: Exception) {
        Result.failure(e)
    }

    suspend fun createConnection(request: NasConnectionRequest): Result<NasConnectionResponse> = try {
        val response = api().createNasConnection(request)
        val body = response.body()
        if (response.isSuccessful && body != null) Result.success(body)
        else Result.failure(Exception("${response.code()} ${response.message()}"))
    } catch (e: Exception) {
        Result.failure(e)
    }

    suspend fun updateConnection(id: String, request: NasConnectionRequest): Result<NasConnectionResponse> = try {
        val response = api().updateNasConnection(id, request)
        val body = response.body()
        if (response.isSuccessful && body != null) Result.success(body)
        else Result.failure(Exception("${response.code()} ${response.message()}"))
    } catch (e: Exception) {
        Result.failure(e)
    }

    suspend fun activateConnection(id: String): Result<Unit> = try {
        val response = api().activateNasConnection(id)
        if (response.isSuccessful) Result.success(Unit)
        else Result.failure(Exception("${response.code()} ${response.message()}"))
    } catch (e: Exception) {
        Result.failure(e)
    }
}
