package com.furkan.support

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receive
import io.ktor.server.response.respond

class SupportController(
    private val service: SupportService,
    private val config: SupportConfig
) {

    suspend fun getAllSupports(call: ApplicationCall) {
        val page = call.request.queryParameters["page"]?.toIntOrNull()?.coerceAtLeast(1) ?: 1
        val size = (call.request.queryParameters["size"]?.toIntOrNull() ?: config.defaultPageSize)
            .coerceIn(1, config.maxPageSize)
        val email = call.request.queryParameters["email"]
        val deviceId = call.request.queryParameters["deviceId"]

        val (items, total) = service.getSupports(page, size, email, deviceId)
        val totalPages = if (total == 0L) 1 else ((total + size - 1) / size).toInt()

        call.respond(
            PaginatedSupportResponse(
                data = items,
                page = page,
                size = size,
                totalItems = total,
                totalPages = totalPages
            )
        )
    }

    suspend fun getSupportById(call: ApplicationCall) {
        val id = call.parameters["id"]?.toIntOrNull()
        if (id == null) {
            call.respond(HttpStatusCode.BadRequest, SupportErrorResponse(error = "Gecersiz id"))
            return
        }

        val support = service.getSupport(id)
        if (support == null) {
            call.respond(HttpStatusCode.NotFound, SupportErrorResponse(error = "Kayit bulunamadi"))
        } else {
            call.respond(support)
        }
    }

    suspend fun createSupport(call: ApplicationCall) {
        val request = try {
            call.receive<SupportRequest>()
        } catch (e: Exception) {
            call.respond(HttpStatusCode.BadRequest, SupportErrorResponse(error = "Gecersiz istek govdesi"))
            return
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

        val created = service.createSupport(
            deviceId = deviceId,
            email = email,
            description = description,
            location = request.location,
            latitude = request.latitude,
            longitude = request.longitude
        )

        if (created != null) {
            call.respond(HttpStatusCode.Created, created)
        } else {
            call.respond(
                HttpStatusCode.InternalServerError,
                SupportErrorResponse(error = "Destek kaydi olusturulamadi")
            )
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
            call.respond(HttpStatusCode.NotFound, SupportErrorResponse(error = "Kayit bulunamadi"))
        }
    }
}
