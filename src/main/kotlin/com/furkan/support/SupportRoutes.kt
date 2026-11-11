package com.furkan.support

import io.ktor.server.auth.authenticate
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.patch
import io.ktor.server.routing.post
import io.ktor.server.routing.route

/**
 * Kullanicinin uclari. Herkes yalnizca kendi cihazinin taleplerini gorur.
 *
 * ```
 * routing {
 *     supportRoutes(supportConfig)
 *     authenticate("admin") { supportAdminRoutes(supportConfig) }
 * }
 * ```
 *
 * Uc noktalar (basePath'e gore):
 * - `POST   {basePath}`                                  yeni talep (durum OPEN acilir)
 * - `GET    {basePath}/device/{deviceId}`                kendi talepleri (durum + son cevap)
 * - `GET    {basePath}/device/{deviceId}/{id}`           kendi talebinin detayi + cevaplar
 * - `POST   {basePath}/device/{deviceId}/{id}/replies`   kendi talebine cevap (USER -> OPEN)
 *
 * Talep baska cihaza aitse detay ve cevap uclari 404 doner; talebin varligi bile sizmaz.
 */
fun Route.supportRoutes(config: SupportConfig) {
    val controller = controllerOf(config)

    route(config.basePath) {
        if (config.requireAuth) {
            authenticate(config.authName) {
                userEndpoints(controller)
            }
        } else {
            userEndpoints(controller)
        }
    }
}

/**
 * Destek ekibinin uclari: herkesin taleplerini okur, cevaplar, kapatir, siler.
 *
 * Kutuphane bu uclari KORUMAZ; proje kendi yetkisinin icine monte eder:
 * `authenticate(ADMIN_AUTH) { supportAdminRoutes(config) }`.
 *
 * Uc noktalar (adminBasePath'e gore):
 * - `GET    {adminBasePath}`               sayfali liste (page, size, email, deviceId, status)
 * - `GET    {adminBasePath}/{id}`          detay: talep + tum cevaplar
 * - `POST   {adminBasePath}/{id}/replies`  ekip cevabi (STAFF -> ANSWERED)
 * - `PATCH  {adminBasePath}/{id}/status`   durum degistir (OPEN | ANSWERED | CLOSED)
 * - `DELETE {adminBasePath}/{id}`          talebi sil (cevaplari da silinir)
 * - `DELETE {adminBasePath}?olderThanDays=7[&dryRun=true]`
 *                                          son hareketi N gunden eski talepleri toplu sil
 *                                          (cevaplari ve fotograflari da); dryRun yalnizca sayar
 */
fun Route.supportAdminRoutes(config: SupportConfig) {
    val controller = controllerOf(config)

    route(config.adminBasePath) {
        get { controller.getAllSupports(call) }
        get("/{id}") { controller.getSupportById(call) }
        post("/{id}/replies") { controller.addReply(call, SupportAuthor.STAFF) }
        patch("/{id}/status") { controller.updateStatus(call) }
        delete { controller.deleteStaleSupports(call) }
        delete("/{id}") { controller.deleteSupport(call) }
    }
}

private fun controllerOf(config: SupportConfig) = SupportController(
    service = SupportService(SupportRepository(config.database, config.table, config.replyTable)),
    config = config
)

private fun Route.userEndpoints(controller: SupportController) {
    post { controller.createSupport(call) }
    get("/device/{deviceId}") { controller.getSupportsByDevice(call) }
    get("/device/{deviceId}/{id}") { controller.getSupportById(call) }
    post("/device/{deviceId}/{id}/replies") { controller.addReply(call, SupportAuthor.USER) }
}
