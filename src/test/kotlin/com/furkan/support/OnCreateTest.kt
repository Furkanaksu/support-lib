package com.furkan.support

import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.Database
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * `onCreate`, projeye ozel yan etkiler icin: admin e-postasi, Slack bildirimi, log.
 * Kutuphane bunlari bilmez; sadece "yeni talep geldi" der.
 */
class OnCreateTest {

    private val json = Json { ignoreUnknownKeys = true }

    private val gorulenler = mutableListOf<SupportResponse>()

    private fun config(dbName: String, onCreate: (SupportResponse) -> Unit) = SupportConfig(
        database = Database.connect("jdbc:h2:mem:$dbName;DB_CLOSE_DELAY=-1", driver = "org.h2.Driver"),
        tableName = "${dbName}_support",
        onCreate = onCreate
    ).also { it.migrate() }

    private fun ApplicationTestBuilder.setup(config: SupportConfig) {
        application {
            install(ContentNegotiation) { json() }
            routing { supportRoutes(config) }
        }
    }

    private suspend fun ApplicationTestBuilder.talepAc(
        deviceId: String = "cihaz-1",
        email: String = "ali@ornek.com",
        description: String = "Uygulama acilmiyor"
    ): HttpResponse = client.post("/support") {
        contentType(ContentType.Application.Json)
        setBody("""{"deviceId":"$deviceId","email":"$email","description":"$description"}""")
    }

    @Test
    fun `yeni talepte onCreate cagrilir ve kaydin tamami gelir`() = testApplication {
        gorulenler.clear()
        setup(config("oc1") { gorulenler += it })

        val cevap = talepAc(description = "Bildirimler gelmiyor")

        assertEquals(HttpStatusCode.Created, cevap.status)
        val olay = gorulenler.single()
        assertEquals("cihaz-1", olay.deviceId)
        assertEquals("ali@ornek.com", olay.email)
        assertEquals("Bildirimler gelmiyor", olay.description)
        assertEquals(SupportStatus.OPEN, olay.status)
        assertTrue(olay.id > 0, "kayit id'si dolu gelir; e-posta metninde kullanilabilir")
    }

    @Test
    fun `onCreate hata verirse talep yine olusur`() = testApplication {
        setup(config("oc2") { error("e-posta sunucusu kapali") })

        val cevap = talepAc()

        assertEquals(HttpStatusCode.Created, cevap.status, "yan etkinin hatasi istegi bozmaz")
        val kayit = json.decodeFromString<SupportResponse>(cevap.bodyAsText())
        assertEquals("cihaz-1", kayit.deviceId)
    }

    @Test
    fun `gecersiz istekte onCreate cagrilmaz`() = testApplication {
        gorulenler.clear()
        setup(config("oc3") { gorulenler += it })

        assertEquals(HttpStatusCode.BadRequest, talepAc(deviceId = "").status)
        assertEquals(HttpStatusCode.BadRequest, talepAc(email = "").status)
        assertEquals(HttpStatusCode.BadRequest, talepAc(description = "").status)
        assertTrue(gorulenler.isEmpty(), "kayit atilmadiysa yan etki de tetiklenmez")
    }

    @Test
    fun `onCreate verilmezse hicbir sey degismez`() = testApplication {
        setup(
            SupportConfig(
                database = Database.connect("jdbc:h2:mem:oc4;DB_CLOSE_DELAY=-1", driver = "org.h2.Driver"),
                tableName = "oc4_support"
            ).also { it.migrate() }
        )

        assertEquals(HttpStatusCode.Created, talepAc().status)
    }
}
