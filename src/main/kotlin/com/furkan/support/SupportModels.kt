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
    val longitude: Double? = null,
    /**
     * Fotografi baska bir yere kendi yukleyen istemciler adresi burada gonderir.
     * Fotografi API'ye yuklemek isteyenler multipart/form-data kullanir (bkz. README).
     */
    val photoUrl: String? = null
)

/**
 * Listelerde donen ozet. Cevaplarin kendisi degil, sayisi ve son cevabin bilgisi yer alir.
 * Istemci "yeni cevap var mi" sorusunu [lastReplyAuthor] + [lastReplyDate] ile yanitlar:
 * son cevap STAFF'tan ve daha once gorulen tarihten farkliysa okunmamistir.
 */
@Serializable
data class SupportResponse(
    val id: Int,
    val deviceId: String,
    val email: String,
    val description: String,
    val location: String?,
    val latitude: Double?,
    val longitude: Double?,
    val photoUrl: String? = null,
    val status: SupportStatus,
    val replyCount: Int,
    val createdDate: String,
    val updatedDate: String? = null,
    val lastReplyAuthor: SupportAuthor? = null,
    val lastReplyDate: String? = null
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
    val photoUrl: String? = null,
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

/** Cevap yazma govdesi. Yazari uc belirler (admin ucu STAFF, kullanici ucu USER). */
@Serializable
data class SupportReplyRequest(
    val message: String? = null
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
