package com.furkan.support

import kotlinx.serialization.Serializable

@Serializable
data class SupportRequest(
    val deviceId: String? = null,
    val email: String? = null,
    val description: String? = null,
    val location: String? = null,
    // Eski istemciler bu alanlari tirnakli gonderebiliyor: "41.0"
    @Serializable(with = LenientDoubleSerializer::class)
    val latitude: Double? = null,
    @Serializable(with = LenientDoubleSerializer::class)
    val longitude: Double? = null
)

@Serializable
data class SupportResponse(
    val id: Int,
    val deviceId: String,
    val email: String,
    val description: String,
    val location: String?,
    val latitude: Double?,
    val longitude: Double?,
    val createdDate: String
)

@Serializable
data class PaginatedSupportResponse(
    val data: List<SupportResponse>,
    val page: Int,
    val size: Int,
    val totalItems: Long,
    val totalPages: Int
)

@Serializable
data class SupportErrorResponse(
    val status: String = "fail",
    val error: String
)
