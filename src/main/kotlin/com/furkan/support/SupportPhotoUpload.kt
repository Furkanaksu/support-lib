package com.furkan.support

import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
import io.ktor.http.content.streamProvider
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receiveMultipart
import java.util.UUID

/** Fotograf dosya parcasinin kabul edilen adlari. "screenshots" eski Android istemcilerinin adidir. */
internal val FOTOGRAF_PARCA_ADLARI = setOf("photo", "screenshots")

/** Govdeden okunmus, henuz depoya yuklenmemis bir fotograf. */
internal class GelenFoto(
    val bytes: ByteArray,
    val fileName: String?,
    val contentType: String
)

/** Multipart govdeden cikan talep alanlari + (varsa) fotograflar. */
internal data class MultipartTalep(
    val request: SupportRequest,
    val photos: List<GelenFoto>
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

/** Multipart govdeden cikan cevap alanlari + (varsa) tek fotograf. */
internal data class MultipartCevap(
    val request: SupportReplyRequest,
    val photo: GelenFoto?
)

internal sealed interface MultipartCevapSonuc {
    data class Ok(val cevap: MultipartCevap) : MultipartCevapSonuc
    data class Hata(val hata: MultipartHata) : MultipartCevapSonuc
}

/** Bir multipart govdenin ham hali: metin alanlari + fotograf parcalari. */
private class MultipartGovde(
    val alanlar: Map<String, String>,
    val photos: List<GelenFoto>
)

private sealed interface GovdeSonuc {
    class Ok(val govde: MultipartGovde) : GovdeSonuc
    class Hata(val hata: MultipartHata) : GovdeSonuc
}

/**
 * `multipart/form-data` govdesini okur.
 *
 * Beklenen alanlar JSON govdesiyle ayni adlari tasir: deviceId, email, description,
 * location, latitude, longitude. Fotograflar `photo` (ya da eski istemcilerde `screenshots`)
 * adli dosya parcalarinda gelir; en fazla [SupportConfig.maxPhotos] tanesi alinir.
 */
internal suspend fun ApplicationCall.multipartTalepOku(config: SupportConfig): MultipartSonuc {
    val govde = when (val okunan = multipartOku(config, config.maxPhotos)) {
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
            photos = govde.photos
        )
    )
}

/** Cevap icin multipart govde: `message` (fotograf varsa bos olabilir) + tek `photo` dosyasi. */
internal suspend fun ApplicationCall.multipartCevapOku(config: SupportConfig): MultipartCevapSonuc {
    val govde = when (val okunan = multipartOku(config, limit = 1)) {
        is GovdeSonuc.Hata -> return MultipartCevapSonuc.Hata(okunan.hata)
        is GovdeSonuc.Ok -> okunan.govde
    }
    return MultipartCevapSonuc.Ok(
        MultipartCevap(
            request = SupportReplyRequest(
                message = govde.alanlar["message"],
                photoUrl = govde.alanlar["photoUrl"]
            ),
            photo = govde.photos.firstOrNull()
        )
    )
}

/**
 * Govdeyi okur: metin alanlari + en fazla [limit] fotograf. Fazlasi okunmadan atlanir.
 *
 * Dosya, boyut siniri asilirsa OKUNMAYA DEVAM EDILMEZ: bellege tum dosyayi almadan
 * kesilir, boylece buyuk bir yukleme sunucuyu yormaz.
 */
private suspend fun ApplicationCall.multipartOku(config: SupportConfig, limit: Int): GovdeSonuc {
    val alanlar = mutableMapOf<String, String>()
    val fotolar = mutableListOf<GelenFoto>()
    var hata: MultipartHata? = null

    try {
        receiveMultipart().forEachPart { part ->
            when (part) {
                is PartData.FormItem -> part.name?.let { alanlar[it] = part.value }

                is PartData.FileItem -> if (part.name in FOTOGRAF_PARCA_ADLARI && hata == null && fotolar.size < limit) {
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
                                fotolar += GelenFoto(okunan, part.originalFileName, tur)
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
    return GovdeSonuc.Ok(MultipartGovde(alanlar, fotolar))
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
