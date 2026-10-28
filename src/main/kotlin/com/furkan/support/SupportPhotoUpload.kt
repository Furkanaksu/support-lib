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

/** Multipart govdeden cikan cevap alanlari + (varsa) fotograf. */
internal data class MultipartCevap(
    val request: SupportReplyRequest,
    val photoBytes: ByteArray?,
    val photoFileName: String?,
    val photoContentType: String?
)

internal sealed interface MultipartCevapSonuc {
    data class Ok(val cevap: MultipartCevap) : MultipartCevapSonuc
    data class Hata(val hata: MultipartHata) : MultipartCevapSonuc
}

/** Bir multipart govdenin ham hali: metin alanlari + `photo` parcasi. */
private class MultipartGovde(
    val alanlar: Map<String, String>,
    val bytes: ByteArray?,
    val fileName: String?,
    val contentType: String?
)

/**
 * `multipart/form-data` govdesini okur.
 *
 * Beklenen alanlar JSON govdesiyle ayni adlari tasir: deviceId, email, description,
 * location, latitude, longitude. Fotograf `photo` adli dosya parcasinda gelir.
 */
internal suspend fun ApplicationCall.multipartTalepOku(config: SupportConfig): MultipartSonuc {
    val govde = when (val okunan = multipartOku(config)) {
        is GovdeSonuc.Hata -> return MultipartSonuc.Hata(okunan.hata)
        is GovdeSonuc.Ok -> okunan.govde
    }
    val a = govde.alanlar
    return MultipartSonuc.Ok(
        MultipartTalep(
            request = SupportRequest(
                deviceId = a["deviceId"],
                email = a["email"],
                description = a["description"],
                location = a["location"],
                latitude = a["latitude"]?.trim()?.toDoubleOrNull(),
                longitude = a["longitude"]?.trim()?.toDoubleOrNull(),
                photoUrl = a["photoUrl"]
            ),
            photoBytes = govde.bytes,
            photoFileName = govde.fileName,
            photoContentType = govde.contentType
        )
    )
}

/** Cevap icin multipart govde: `message` (fotograf varsa bos olabilir) + `photo` dosyasi. */
internal suspend fun ApplicationCall.multipartCevapOku(config: SupportConfig): MultipartCevapSonuc {
    val govde = when (val okunan = multipartOku(config)) {
        is GovdeSonuc.Hata -> return MultipartCevapSonuc.Hata(okunan.hata)
        is GovdeSonuc.Ok -> okunan.govde
    }
    return MultipartCevapSonuc.Ok(
        MultipartCevap(
            request = SupportReplyRequest(
                message = govde.alanlar["message"],
                photoUrl = govde.alanlar["photoUrl"]
            ),
            photoBytes = govde.bytes,
            photoFileName = govde.fileName,
            photoContentType = govde.contentType
        )
    )
}

private sealed interface GovdeSonuc {
    class Ok(val govde: MultipartGovde) : GovdeSonuc
    class Hata(val hata: MultipartHata) : GovdeSonuc
}

/**
 * Govdeyi okur: metin alanlari + `photo` dosyasi.
 *
 * Dosya, boyut siniri asilirsa OKUNMAYA DEVAM EDILMEZ: bellege tum dosyayi almadan
 * kesilir, boylece buyuk bir yukleme sunucuyu yormaz.
 */
private suspend fun ApplicationCall.multipartOku(config: SupportConfig): GovdeSonuc {
    val alanlar = mutableMapOf<String, String>()
    var bytes: ByteArray? = null
    var fileName: String? = null
    var contentType: String? = null
    var hata: MultipartHata? = null

    try {
        receiveMultipart().forEachPart { part ->
            when (part) {
                is PartData.FormItem -> part.name?.let { alanlar[it] = part.value }

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
        return GovdeSonuc.Hata(MultipartHata.Bozuk)
    }

    hata?.let { return GovdeSonuc.Hata(it) }
    return GovdeSonuc.Ok(MultipartGovde(alanlar, bytes, fileName, contentType))
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
