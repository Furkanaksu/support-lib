package com.furkan.support

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.contentType
import io.ktor.server.request.receive
import io.ktor.server.response.respond

class SupportController(
    private val service: SupportService,
    private val config: SupportConfig
) {

    suspend fun getAllSupports(call: ApplicationCall) {
        val rawStatus = call.request.queryParameters["status"]
        val status = SupportStatus.fromOrNull(rawStatus)
        if (rawStatus != null && status == null) {
            call.respond(HttpStatusCode.BadRequest, SupportErrorResponse(error = INVALID_STATUS))
            return
        }

        val page = page(call)
        val size = size(call)
        val (items, total) = service.getSupports(
            page = page,
            size = size,
            email = call.request.queryParameters["email"],
            deviceId = call.request.queryParameters["deviceId"],
            status = status
        )
        call.respond(paginate(items, total, page, size))
    }

    /** Kullanicinin kendi talepleri; durum ve cevap sayisi liste icinde doner. */
    suspend fun getSupportsByDevice(call: ApplicationCall) {
        val deviceId = call.parameters["deviceId"]
        if (deviceId.isNullOrBlank()) {
            call.respond(HttpStatusCode.BadRequest, SupportErrorResponse(error = "deviceId bos olamaz"))
            return
        }

        val rawStatus = call.request.queryParameters["status"]
        val status = SupportStatus.fromOrNull(rawStatus)
        if (rawStatus != null && status == null) {
            call.respond(HttpStatusCode.BadRequest, SupportErrorResponse(error = INVALID_STATUS))
            return
        }

        val page = page(call)
        val size = size(call)
        val (items, total) = service.getSupportsByDevice(deviceId, page, size, status)
        call.respond(paginate(items, total, page, size))
    }

    /** Detay: talep + tum cevaplar. */
    suspend fun getSupportById(call: ApplicationCall) {
        val id = call.parameters["id"]?.toIntOrNull()
        if (id == null) {
            call.respond(HttpStatusCode.BadRequest, SupportErrorResponse(error = "Gecersiz id"))
            return
        }

        val detail = service.getSupportDetail(id)
        if (detail == null) {
            call.respond(HttpStatusCode.NotFound, SupportErrorResponse(error = NOT_FOUND))
        } else {
            call.respond(detail)
        }
    }

    suspend fun createSupport(call: ApplicationCall) {
        // Iki govde sekli desteklenir: duz JSON (eski istemciler) ve multipart/form-data
        // (fotograf eklemek isteyenler). Multipart'ta alan adlari JSON'dakiyle aynidir.
        val multipart = call.request.contentType().match(ContentType.MultiPart.FormData)

        var photoBytes: ByteArray? = null
        var photoFileName: String? = null
        var photoContentType: String? = null

        val request = if (multipart) {
            when (val sonuc = call.multipartTalepOku(config)) {
                is MultipartSonuc.Hata -> {
                    call.multipartHatasiDon(sonuc.hata)
                    return
                }
                is MultipartSonuc.Ok -> {
                    photoBytes = sonuc.talep.photoBytes
                    photoFileName = sonuc.talep.photoFileName
                    photoContentType = sonuc.talep.photoContentType
                    sonuc.talep.request
                }
            }
        } else {
            try {
                call.receive<SupportRequest>()
            } catch (e: Exception) {
                call.respond(HttpStatusCode.BadRequest, SupportErrorResponse(error = INVALID_BODY))
                return
            }
        }

        val deviceId = request.deviceId
        val email = request.email
        val description = request.description

        if (deviceId.isNullOrBlank()) {
            call.respond(HttpStatusCode.BadRequest, SupportErrorResponse(error = "deviceId bos olamaz"))
            return
        }
        if (email.isNullOrBlank()) {
            call.respond(HttpStatusCode.BadRequest, SupportErrorResponse(error = "email bos olamaz"))
            return
        }
        if (description.isNullOrBlank()) {
            call.respond(HttpStatusCode.BadRequest, SupportErrorResponse(error = "description bos olamaz"))
            return
        }

        // Yukleme dogrulamadan SONRA yapilir: gecersiz istek icin depoya bosuna gidilmez.
        val photoUrl = if (photoBytes != null && photoContentType != null) {
            val depo = config.photoStorage
            if (depo == null) {
                call.multipartHatasiDon(MultipartHata.FotografKapali)
                return
            }
            try {
                depo.upload(photoBytes, fotografAdiUret(photoFileName, photoContentType), photoContentType)
            } catch (e: Exception) {
                call.respond(
                    HttpStatusCode.ServiceUnavailable,
                    SupportErrorResponse(error = "Fotograf yuklenemedi, talebi fotografsiz gonderebilirsiniz")
                )
                return
            }
        } else {
            request.photoUrl?.trim()?.takeIf { it.isNotEmpty() }
        }

        val created = service.createSupport(
            deviceId = deviceId,
            email = email,
            description = description,
            location = request.location,
            latitude = request.latitude,
            longitude = request.longitude,
            photoUrl = photoUrl
        )

        if (created != null) {
            // Projeye ozel yan etki (orn. admin e-postasi). Hatasi talebi bozmaz:
            // kayit zaten atildi, istemciye 201 donmeli.
            try {
                config.onCreate(created)
            } catch (e: Exception) {
                println("[support-lib] onCreate hata verdi: ${e.message}")
            }
            call.respond(HttpStatusCode.Created, created)
        } else {
            call.respond(
                HttpStatusCode.InternalServerError,
                SupportErrorResponse(error = "Destek kaydi olusturulamadi")
            )
        }
    }

    /** Talebe cevap yazar; author verilmezse STAFF kabul edilir ve durum ANSWERED olur. */
    suspend fun addReply(call: ApplicationCall) {
        val id = call.parameters["id"]?.toIntOrNull()
        if (id == null) {
            call.respond(HttpStatusCode.BadRequest, SupportErrorResponse(error = "Gecersiz id"))
            return
        }

        val request = try {
            call.receive<SupportReplyRequest>()
        } catch (e: Exception) {
            call.respond(HttpStatusCode.BadRequest, SupportErrorResponse(error = INVALID_BODY))
            return
        }

        val message = request.message
        if (message.isNullOrBlank()) {
            call.respond(HttpStatusCode.BadRequest, SupportErrorResponse(error = "message bos olamaz"))
            return
        }

        val author = when {
            request.author == null -> SupportAuthor.STAFF
            else -> SupportAuthor.fromOrNull(request.author)
        }
        if (author == null) {
            call.respond(HttpStatusCode.BadRequest, SupportErrorResponse(error = INVALID_AUTHOR))
            return
        }

        val reply = service.addReply(id, message, author)
        if (reply == null) {
            call.respond(HttpStatusCode.NotFound, SupportErrorResponse(error = NOT_FOUND))
        } else {
            call.respond(HttpStatusCode.Created, reply)
        }
    }

    /** Durum degistirir: kapatma ya da yeniden acma. */
    suspend fun updateStatus(call: ApplicationCall) {
        val id = call.parameters["id"]?.toIntOrNull()
        if (id == null) {
            call.respond(HttpStatusCode.BadRequest, SupportErrorResponse(error = "Gecersiz id"))
            return
        }

        val request = try {
            call.receive<SupportStatusRequest>()
        } catch (e: Exception) {
            call.respond(HttpStatusCode.BadRequest, SupportErrorResponse(error = INVALID_BODY))
            return
        }

        val status = SupportStatus.fromOrNull(request.status)
        if (status == null) {
            call.respond(HttpStatusCode.BadRequest, SupportErrorResponse(error = INVALID_STATUS))
            return
        }

        val updated = service.updateStatus(id, status)
        if (updated == null) {
            call.respond(HttpStatusCode.NotFound, SupportErrorResponse(error = NOT_FOUND))
        } else {
            call.respond(updated)
        }
    }

    suspend fun deleteSupport(call: ApplicationCall) {
        val id = call.parameters["id"]?.toIntOrNull()
        if (id == null) {
            call.respond(HttpStatusCode.BadRequest, SupportErrorResponse(error = "Gecersiz id"))
            return
        }

        if (service.deleteSupport(id)) {
            call.respond(HttpStatusCode.NoContent)
        } else {
            call.respond(HttpStatusCode.NotFound, SupportErrorResponse(error = NOT_FOUND))
        }
    }

    private fun page(call: ApplicationCall) =
        call.request.queryParameters["page"]?.toIntOrNull()?.coerceAtLeast(1) ?: 1

    private fun size(call: ApplicationCall) =
        (call.request.queryParameters["size"]?.toIntOrNull() ?: config.defaultPageSize)
            .coerceIn(1, config.maxPageSize)

    private fun paginate(items: List<SupportResponse>, total: Long, page: Int, size: Int) =
        PaginatedSupportResponse(
            data = items,
            page = page,
            size = size,
            totalItems = total,
            totalPages = if (total == 0L) 1 else ((total + size - 1) / size).toInt()
        )

    private companion object {
        const val NOT_FOUND = "Kayit bulunamadi"
        const val INVALID_BODY = "Gecersiz istek govdesi"
        const val INVALID_STATUS = "Gecersiz status. Gecerli degerler: OPEN, ANSWERED, CLOSED"
        const val INVALID_AUTHOR = "Gecersiz author. Gecerli degerler: USER, STAFF"
    }

    /** Multipart okuma hatalarini istemciye anlamli HTTP koduyla dondurur. */
    private suspend fun ApplicationCall.multipartHatasiDon(hata: MultipartHata) = when (hata) {
        is MultipartHata.CokBuyuk -> respond(
            HttpStatusCode.PayloadTooLarge,
            SupportErrorResponse(error = "Fotograf en fazla ${hata.limit / 1024 / 1024} MB olabilir")
        )

        is MultipartHata.DesteklenmeyenTur -> respond(
            HttpStatusCode.UnsupportedMediaType,
            SupportErrorResponse(
                error = "Desteklenmeyen dosya turu${hata.gelen?.let { " ($it)" } ?: ""}. " +
                    "Kabul edilenler: ${config.allowedPhotoTypes.joinToString(", ")}"
            )
        )

        MultipartHata.FotografKapali -> respond(
            HttpStatusCode.BadRequest,
            SupportErrorResponse(error = "Bu sunucuda fotograf yukleme kapali")
        )

        MultipartHata.Bozuk -> respond(
            HttpStatusCode.BadRequest,
            SupportErrorResponse(error = INVALID_BODY)
        )
    }
}
