package com.furkan.support

import io.ktor.server.auth.authenticate
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
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
 * - `GET    {basePath}`         sayfali liste (page, size, email, deviceId)
 * - `GET    {basePath}/{id}`    tek kayit
 * - `POST   {basePath}`         yeni kayit
 * - `DELETE {basePath}/{id}`    kayit sil
 */
fun Route.supportRoutes(config: SupportConfig) {
    val controller = SupportController(
        service = SupportService(SupportRepository(config.database, config.table)),
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
    get("/{id}") { controller.getSupportById(call) }
    post { controller.createSupport(call) }
    delete("/{id}") { controller.deleteSupport(call) }
}
