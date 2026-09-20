package com.furkan.support

import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import org.jetbrains.exposed.sql.Database
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SupportRoutesTest {

    private fun config(basePath: String, dbName: String) = SupportConfig(
        database = Database.connect("jdbc:h2:mem:$dbName;DB_CLOSE_DELAY=-1", driver = "org.h2.Driver"),
        basePath = basePath,
        tableName = "support_tickets_$dbName"
    ).also { it.migrate() }

    @Test
    fun `kayit olusturur ve listeler`() = testApplication {
        val cfg = config("/support", "db1")
        application {
            install(ContentNegotiation) { json() }
            routing { supportRoutes(cfg) }
        }

        val created = client.post("/support") {
            contentType(ContentType.Application.Json)
            setBody("""{"deviceId":"device-1","email":"a@b.com","description":"yardim"}""")
        }
        assertEquals(HttpStatusCode.Created, created.status)

        val list = client.get("/support?page=1&size=10")
        assertEquals(HttpStatusCode.OK, list.status)
        assertTrue(list.bodyAsText().contains("device-1"))
    }

    @Test
    fun `base path parametriktir`() = testApplication {
        val cfg = config("/api/v2/destek", "db2")
        application {
            install(ContentNegotiation) { json() }
            routing { supportRoutes(cfg) }
        }

        assertEquals(HttpStatusCode.OK, client.get("/api/v2/destek").status)
        assertEquals(HttpStatusCode.NotFound, client.get("/support").status)
    }

    @Test
    fun `zorunlu alanlar dogrulanir`() = testApplication {
        val cfg = config("/support", "db3")
        application {
            install(ContentNegotiation) { json() }
            routing { supportRoutes(cfg) }
        }

        val response = client.post("/support") {
            contentType(ContentType.Application.Json)
            setBody("""{"email":"a@b.com","description":"yardim"}""")
        }
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }
}
