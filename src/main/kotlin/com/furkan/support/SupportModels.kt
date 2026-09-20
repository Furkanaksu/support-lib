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

/** Listelerde donen ozet. Cevaplarin kendisi degil, sayisi yer alir. */
@Serializable
data class SupportResponse(
    val id: Int,
    val deviceId: String,
    val email: String,
    val description: String,
    val location: String?,
    val latitude: Double?,
    val longitude: Double?,
    val status: SupportStatus,
    val replyCount: Int,
    val createdDate: String,
    val updatedDate: String? = null
)

/** Tek kayit ucunda donen detay: talep + tum cevaplar. */
@Serializable
data class SupportDetailResponse(
    val id: Int,
    val deviceId: String,
    val email: String,
    val description: String,
    val location: String?,
    val latitude: Double?,
    val longitude: Double?,
    val status: SupportStatus,
    val createdDate: String,
    val updatedDate: String? = null,
    val replies: List<SupportReplyResponse>
)

@Serializable
data class SupportReplyResponse(
    val id: Int,
    val supportId: Int,
    val message: String,
    val author: SupportAuthor,
    val createdDate: String
)

/** Cevap yazma govdesi. author verilmezse STAFF kabul edilir. */
@Serializable
data class SupportReplyRequest(
    val message: String? = null,
    val author: String? = null
)

/** Durum degistirme govdesi: OPEN | ANSWERED | CLOSED. */
@Serializable
data class SupportStatusRequest(
    val status: String? = null
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
