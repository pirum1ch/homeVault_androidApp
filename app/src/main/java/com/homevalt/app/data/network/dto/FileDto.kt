package com.homevalt.app.data.network.dto

import com.google.gson.annotations.SerializedName

data class FileDto(
    @SerializedName("id") val id: String,
    @SerializedName("originalName") val name: String,
    @SerializedName("size") val size: Long,
    @SerializedName("mimeType") val mimeType: String,
    @SerializedName("status") val status: String,
    @SerializedName("createdAt") val createdAt: String = "",
    @SerializedName("nasPath") val nasPath: String? = null
) {
    val isStoredOnNas: Boolean get() = status == "STORED_ON_NAS"
}
