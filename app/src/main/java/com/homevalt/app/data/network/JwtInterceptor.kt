package com.homevalt.app.data.network

import com.homevalt.app.data.preferences.EncryptedPrefs
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import okhttp3.Interceptor
import okhttp3.Response

class JwtInterceptor(private val encryptedPrefs: EncryptedPrefs) : Interceptor {

    companion object {
        private val _sessionExpiredFlow = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
        val sessionExpiredFlow = _sessionExpiredFlow.asSharedFlow()
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val token = encryptedPrefs.getToken()
        val request = if (token != null) {
            chain.request().newBuilder()
                .addHeader("Authorization", "Bearer $token")
                .build()
        } else {
            chain.request()
        }
        val response = chain.proceed(request)
        if (response.code == 401) {
            encryptedPrefs.clearToken()
            _sessionExpiredFlow.tryEmit(Unit)
        }
        return response
    }
}
