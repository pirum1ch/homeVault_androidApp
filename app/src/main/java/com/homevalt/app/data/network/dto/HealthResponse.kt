package com.homevalt.app.data.network.dto

import com.google.gson.annotations.SerializedName

data class HealthResponse(
    @SerializedName("status") val status: String
)
