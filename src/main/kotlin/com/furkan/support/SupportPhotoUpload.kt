package com.furkan.support

import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
import io.ktor.http.content.streamProvider
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receiveMultipart
import java.util.UUID

/** Multipart govdeden cikan talep alanlari + (varsa) fotograf. */
internal data class MultipartTalep(
    val request: SupportRequest,
    val photoBytes: ByteArray?,
    val photoFileName: String?,
    val photoContentType: String?
)

/** Multipart okunurken olusan, istemciye donulecek hatalar. */
internal sealed interface MultipartHata {
    data class CokBuyuk(val limit: Long) : MultipartHata
    data class DesteklenmeyenTur(val gelen: String?) : MultipartHata
    data object FotografKapali : MultipartHata
    data object Bozuk : MultipartHata
}

internal sealed interface MultipartSonuc {
    data class Ok(val talep: MultipartTalep) : MultipartSonuc
    data class Hata(val hata: MultipartHata) : MultipartSonuc
}

/**
 * `multipart/form-data` govdesini okur.
 *
 * Beklenen alanlar JSON govdesiyle ayni adlari tasir: deviceId, email, description,
 * location, latitude, longitude. Fotograf `photo` adli dosya parcasinda gelir.
 *
 * Dosya, boyut siniri asilirsa OKUNMAYA DEVAM EDILMEZ: bellege tum dosyayi almadan
 * kesilir, boylece buyuk bir yukleme sunucuyu yormaz.
 */
internal suspend fun ApplicationCall.multipartTalepOku(config: SupportConfig): MultipartSonuc {
    var deviceId: String? = null
    var email: String? = null
    var description: String? = null
    var location: String? = null
    var latitude: Double? = null
    var longitude: Double? = null
    var photoUrl: String? = null

    var bytes: ByteArray? = null
    var fileName: String? = null
    var contentType: String? = null
    var hata: MultipartHata? = null

    try {
        receiveMultipart().forEachPart { part ->
            when (part) {
                is PartData.FormItem -> when (part.name) {
                    "deviceId" -> deviceId = part.value
                    "email" -> email = part.value
                    "description" -> description = part.value
                    "location" -> location = part.value
                    "latitude" -> latitude = part.value.trim().toDoubleOrNull()
                    "longitude" -> longitude = part.value.trim().toDoubleOrNull()
                    "photoUrl" -> photoUrl = part.value
                }

                is PartData.FileItem -> if (part.name == "photo" && hata == null) {
                    if (config.photoStorage == null) {
                        hata = MultipartHata.FotografKapali
                    } else {
                        val tur = part.contentType?.let { "${it.contentType}/${it.contentSubtype}" }
                        if (tur == null || tur !in config.allowedPhotoTypes) {
                            hata = MultipartHata.DesteklenmeyenTur(tur)
                        } else {
                            val okunan = part.streamProvider().use { akis ->
                                akis.readNBytes((config.maxPhotoBytes + 1).toInt())
                            }
                            if (okunan.size > config.maxPhotoBytes) {
                                hata = MultipartHata.CokBuyuk(config.maxPhotoBytes)
                            } else {
                                bytes = okunan
                                contentType = tur
                                fileName = part.originalFileName
                            }
                        }
                    }
                }

                else -> Unit
            }
            part.dispose()
        }
    } catch (e: Exception) {
        return MultipartSonuc.Hata(MultipartHata.Bozuk)
    }

    hata?.let { return MultipartSonuc.Hata(it) }

    return MultipartSonuc.Ok(
        MultipartTalep(
            request = SupportRequest(
                deviceId = deviceId,
                email = email,
                description = description,
                location = location,
                latitude = latitude,
                longitude = longitude,
                photoUrl = photoUrl
            ),
            photoBytes = bytes,
            photoFileName = fileName,
            photoContentType = contentType
        )
    )
}

/** Depoya verilecek benzersiz ad. Istemcinin gonderdigi ad guvenilmez, sadece uzantisi alinir. */
internal fun fotografAdiUret(originalFileName: String?, contentType: String): String {
    val uzanti = when (contentType) {
        "image/png" -> "png"
        "image/webp" -> "webp"
        else -> "jpg"
    }
    val id = UUID.randomUUID().toString().take(8)
    return "support-${System.currentTimeMillis()}-$id.$uzanti"
}
