package com.homevalt.app.data.network.dto

import com.google.gson.annotations.SerializedName

data class NasConnectionRequest(
    @SerializedName("type") val type: String,
    @SerializedName("name") val name: String,
    @SerializedName("host") val host: String,
    @SerializedName("username") val username: String,
    @SerializedName("password") val password: String,
    @SerializedName("path") val path: String
)

data class NasConnectionResponse(
    @SerializedName("id") val id: String,
    @SerializedName("name") val name: String,
    @SerializedName("host") val host: String,
    @SerializedName("type") val type: String,
    @SerializedName("username") val username: String?,
    @SerializedName("path") val path: String,
    @SerializedName("active") val active: Boolean,
    @SerializedName("createdAt") val createdAt: String,
    @SerializedName("updatedAt") val updatedAt: String
)
