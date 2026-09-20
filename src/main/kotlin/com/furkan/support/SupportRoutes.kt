package com.furkan.support

import io.ktor.server.auth.authenticate
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.patch
import io.ktor.server.routing.post
import io.ktor.server.routing.route

/**
 * Kutuphanenin tek giris noktasi.
 *
 * ```
 * routing {
 *     supportRoutes(supportConfig)
 * }
 * ```
 *
 * Uc noktalar (basePath'e gore):
 * - `GET    {basePath}`                    sayfali liste (page, size, email, deviceId, status)
 * - `POST   {basePath}`                    yeni talep (durum OPEN acilir)
 * - `GET    {basePath}/device/{deviceId}`  kullanicinin kendi talepleri (durum + cevap sayisi)
 * - `GET    {basePath}/{id}`               detay: talep + tum cevaplar
 * - `POST   {basePath}/{id}/replies`       cevap yaz (STAFF -> ANSWERED, USER -> OPEN)
 * - `PATCH  {basePath}/{id}/status`        durum degistir (OPEN | ANSWERED | CLOSED)
 * - `DELETE {basePath}/{id}`               talebi sil (cevaplari da silinir)
 */
fun Route.supportRoutes(config: SupportConfig) {
    val controller = SupportController(
        service = SupportService(SupportRepository(config.database, config.table, config.replyTable)),
        config = config
    )

    route(config.basePath) {
        if (config.requireAuth) {
            authenticate(config.authName) {
                supportEndpoints(controller)
            }
        } else {
            supportEndpoints(controller)
        }
    }
}

private fun Route.supportEndpoints(controller: SupportController) {
    get { controller.getAllSupports(call) }
    post { controller.createSupport(call) }
    get("/device/{deviceId}") { controller.getSupportsByDevice(call) }
    get("/{id}") { controller.getSupportById(call) }
    post("/{id}/replies") { controller.addReply(call) }
    patch("/{id}/status") { controller.updateStatus(call) }
    delete("/{id}") { controller.deleteSupport(call) }
}
