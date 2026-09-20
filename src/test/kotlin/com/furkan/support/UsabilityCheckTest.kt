package com.furkan.support

import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.UserIdPrincipal
import io.ktor.server.auth.basic
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import org.jetbrains.exposed.sql.Database
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UsabilityCheckTest {

    private fun config(
        dbName: String,
        requireAuth: Boolean = false,
        authName: String? = null
    ) = SupportConfig(
        database = Database.connect("jdbc:h2:mem:$dbName;DB_CLOSE_DELAY=-1", driver = "org.h2.Driver"),
        tableName = "support_tickets_$dbName",
        requireAuth = requireAuth,
        authName = authName
    ).also { it.migrate() }

    @Test
    fun `tek kayit ve silme uclari`() = testApplication {
        val cfg = config("u1")
        application {
            install(ContentNegotiation) { json() }
            routing { supportRoutes(cfg) }
        }

        client.post("/support") {
            contentType(ContentType.Application.Json)
            setBody("""{"deviceId":"d1","email":"a@b.com","description":"x"}""")
        }

        assertEquals(HttpStatusCode.OK, client.get("/support/1").status)
        assertEquals(HttpStatusCode.NotFound, client.get("/support/999").status)
        assertEquals(HttpStatusCode.BadRequest, client.get("/support/abc").status)
        assertEquals(HttpStatusCode.NoContent, client.delete("/support/1").status)
        assertEquals(HttpStatusCode.NotFound, client.delete("/support/1").status)
    }

    @Test
    fun `email ve deviceId filtresi`() = testApplication {
        val cfg = config("u2")
        application {
            install(ContentNegotiation) { json() }
            routing { supportRoutes(cfg) }
        }

        client.post("/support") {
            contentType(ContentType.Application.Json)
            setBody("""{"deviceId":"android-1","email":"ali@x.com","description":"x"}""")
        }
        client.post("/support") {
            contentType(ContentType.Application.Json)
            setBody("""{"deviceId":"ios-2","email":"veli@y.com","description":"y"}""")
        }

        val filtered = client.get("/support?email=ALI").bodyAsText()
        assertTrue(filtered.contains("ali@x.com"), "buyuk-kucuk harf duyarsiz filtre calismali")
        assertFalse(filtered.contains("veli@y.com"))
        assertTrue(filtered.contains("\"totalItems\":1"), "totalItems filtreye gore hesaplanmali: $filtered")

        val byDevice = client.get("/support?deviceId=ios").bodyAsText()
        assertTrue(byDevice.contains("ios-2"))
        assertFalse(byDevice.contains("android-1"))
    }

    @Test
    fun `sayfa boyutu maxPageSize ile sinirlanir`() = testApplication {
        val cfg = config("u3")
        application {
            install(ContentNegotiation) { json() }
            routing { supportRoutes(cfg) }
        }

        val body = client.get("/support?size=5000&page=0").bodyAsText()
        assertTrue(body.contains("\"size\":100"), "size maxPageSize'a kirpilmali: $body")
        assertTrue(body.contains("\"page\":1"), "page en az 1 olmali: $body")
    }

    @Test
    fun `requireAuth true ise kimlik dogrulama zorunlu`() = testApplication {
        val cfg = config("u4", requireAuth = true, authName = "auth-basic")
        application {
            install(ContentNegotiation) { json() }
            install(Authentication) {
                basic("auth-basic") {
                    validate { if (it.name == "admin") UserIdPrincipal(it.name) else null }
                }
            }
            routing { supportRoutes(cfg) }
        }

        assertEquals(HttpStatusCode.Unauthorized, client.get("/support").status)
        assertEquals(HttpStatusCode.Unauthorized, client.delete("/support/1").status)
    }
}
