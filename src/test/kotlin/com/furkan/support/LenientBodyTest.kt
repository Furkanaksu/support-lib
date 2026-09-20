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

/** Eski istemci govdeleriyle geriye donuk uyumluluk. */
class LenientBodyTest {

    private fun config(dbName: String) = SupportConfig(
        database = Database.connect("jdbc:h2:mem:$dbName;DB_CLOSE_DELAY=-1", driver = "org.h2.Driver"),
        tableName = "support_tickets_$dbName"
    ).also { it.migrate() }

    private fun io.ktor.server.testing.ApplicationTestBuilder.setup(cfg: SupportConfig) {
        application {
            install(ContentNegotiation) { json() }
            routing { supportRoutes(cfg) }
        }
    }

    @Test
    fun `tirnakli koordinatlar kabul edilir`() = testApplication {
        val cfg = config("l1")
        setup(cfg)

        val response = client.post("/support") {
            contentType(ContentType.Application.Json)
            setBody(
                """{"deviceId":"d1","email":"a@b.com","description":"x",
                   "location":"Istanbul","latitude":"41.0082","longitude":"28.9784"}"""
            )
        }
        assertEquals(HttpStatusCode.Created, response.status)
        val body = response.bodyAsText()
        assertTrue(body.contains("\"latitude\":41.0082"), "sayi olarak donmeli: $body")
        assertTrue(body.contains("\"longitude\":28.9784"), body)
    }

    @Test
    fun `sayisal koordinatlar da kabul edilir`() = testApplication {
        val cfg = config("l2")
        setup(cfg)

        val response = client.post("/support") {
            contentType(ContentType.Application.Json)
            setBody("""{"deviceId":"d2","email":"a@b.com","description":"x","latitude":41.0,"longitude":29.0}""")
        }
        assertEquals(HttpStatusCode.Created, response.status)
        assertTrue(response.bodyAsText().contains("\"latitude\":41.0"))
    }

    @Test
    fun `bos string ve null koordinat null olur`() = testApplication {
        val cfg = config("l3")
        setup(cfg)

        val empty = client.post("/support") {
            contentType(ContentType.Application.Json)
            setBody("""{"deviceId":"d3","email":"a@b.com","description":"x","latitude":"","longitude":null}""")
        }
        assertEquals(HttpStatusCode.Created, empty.status)
        assertTrue(empty.bodyAsText().contains("\"latitude\":null"), empty.bodyAsText())

        val missing = client.post("/support") {
            contentType(ContentType.Application.Json)
            setBody("""{"deviceId":"d3","email":"a@b.com","description":"x"}""")
        }
        assertEquals(HttpStatusCode.Created, missing.status)

        assertTrue(client.get("/support").bodyAsText().contains("\"totalItems\":2"))
    }
}
