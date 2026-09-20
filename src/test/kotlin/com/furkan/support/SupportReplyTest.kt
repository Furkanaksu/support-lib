package com.furkan.support

import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.patch
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
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Cevaplama akisi, durumlar ve cihaza gore listeleme. */
class SupportReplyTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun config(dbName: String) = SupportConfig(
        database = Database.connect("jdbc:h2:mem:$dbName;DB_CLOSE_DELAY=-1", driver = "org.h2.Driver"),
        tableName = "support_$dbName"
    ).also { it.migrate() }

    private fun ApplicationTestBuilder.setup(cfg: SupportConfig) {
        application {
            install(ContentNegotiation) { json() }
            routing { supportRoutes(cfg) }
        }
    }

    private suspend fun ApplicationTestBuilder.createTicket(deviceId: String, description: String): Int {
        val response = client.post("/support") {
            contentType(ContentType.Application.Json)
            setBody("""{"deviceId":"$deviceId","email":"a@b.com","description":"$description"}""")
        }
        assertEquals(HttpStatusCode.Created, response.status)
        return json.decodeFromString<SupportResponse>(response.bodyAsText()).id
    }

    @Test
    fun `yeni talep OPEN acilir ve cevabi yoktur`() = testApplication {
        setup(config("r1"))
        val id = createTicket("d1", "yardim")

        val detail = json.decodeFromString<SupportDetailResponse>(
            client.get("/support/$id").bodyAsText()
        )
        assertEquals(SupportStatus.OPEN, detail.status)
        assertTrue(detail.replies.isEmpty())
    }

    @Test
    fun `destek ekibi cevap yazinca durum ANSWERED olur ve cevap detayda gorunur`() = testApplication {
        setup(config("r2"))
        val id = createTicket("d1", "yardim")

        val reply = client.post("/support/$id/replies") {
            contentType(ContentType.Application.Json)
            setBody("""{"message":"Merhaba, konuyu inceledik."}""")
        }
        assertEquals(HttpStatusCode.Created, reply.status)
        val replyBody = json.decodeFromString<SupportReplyResponse>(reply.bodyAsText())
        assertEquals(SupportAuthor.STAFF, replyBody.author, "author verilmezse STAFF kabul edilir")

        val detail = json.decodeFromString<SupportDetailResponse>(
            client.get("/support/$id").bodyAsText()
        )
        assertEquals(SupportStatus.ANSWERED, detail.status)
        assertEquals(1, detail.replies.size)
        assertEquals("Merhaba, konuyu inceledik.", detail.replies.first().message)
    }

    @Test
    fun `kullanici cevap yazinca durum tekrar OPEN olur ve cevaplar sirali doner`() = testApplication {
        setup(config("r3"))
        val id = createTicket("d1", "yardim")

        client.post("/support/$id/replies") {
            contentType(ContentType.Application.Json)
            setBody("""{"message":"Ekip cevabi","author":"STAFF"}""")
        }
        client.post("/support/$id/replies") {
            contentType(ContentType.Application.Json)
            setBody("""{"message":"Kullanici cevabi","author":"USER"}""")
        }

        val detail = json.decodeFromString<SupportDetailResponse>(
            client.get("/support/$id").bodyAsText()
        )
        assertEquals(SupportStatus.OPEN, detail.status, "son sozu kullanici soyledi, talep tekrar acik")
        assertEquals(2, detail.replies.size)
        assertEquals(SupportAuthor.STAFF, detail.replies[0].author)
        assertEquals(SupportAuthor.USER, detail.replies[1].author)
    }

    @Test
    fun `durum kapatilabilir`() = testApplication {
        setup(config("r4"))
        val id = createTicket("d1", "yardim")

        val patched = client.patch("/support/$id/status") {
            contentType(ContentType.Application.Json)
            setBody("""{"status":"CLOSED"}""")
        }
        assertEquals(HttpStatusCode.OK, patched.status)
        assertEquals(
            SupportStatus.CLOSED,
            json.decodeFromString<SupportDetailResponse>(patched.bodyAsText()).status
        )

        val gecersiz = client.patch("/support/$id/status") {
            contentType(ContentType.Application.Json)
            setBody("""{"status":"BILINMEYEN"}""")
        }
        assertEquals(HttpStatusCode.BadRequest, gecersiz.status)
    }

    @Test
    fun `cihaz kendi taleplerini durum ve cevap sayisiyla listeler`() = testApplication {
        setup(config("r5"))
        val benim = createTicket("benim-cihazim", "benim talebim")
        createTicket("baska-cihaz", "baskasinin talebi")

        client.post("/support/$benim/replies") {
            contentType(ContentType.Application.Json)
            setBody("""{"message":"cevap 1"}""")
        }

        val liste = json.decodeFromString<PaginatedSupportResponse>(
            client.get("/support/device/benim-cihazim").bodyAsText()
        )
        assertEquals(1, liste.totalItems.toInt(), "sadece kendi talepleri donmeli")
        val item = liste.data.first()
        assertEquals(benim, item.id)
        assertEquals(SupportStatus.ANSWERED, item.status)
        assertEquals(1, item.replyCount)
    }

    @Test
    fun `cihaz listesi durum filtreleyebilir`() = testApplication {
        setup(config("r6"))
        val acik = createTicket("cihaz", "acik talep")
        val kapali = createTicket("cihaz", "kapali talep")
        client.patch("/support/$kapali/status") {
            contentType(ContentType.Application.Json)
            setBody("""{"status":"CLOSED"}""")
        }

        val acikListe = json.decodeFromString<PaginatedSupportResponse>(
            client.get("/support/device/cihaz?status=OPEN").bodyAsText()
        )
        assertEquals(1, acikListe.totalItems.toInt())
        assertEquals(acik, acikListe.data.first().id)

        assertEquals(
            HttpStatusCode.BadRequest,
            client.get("/support/device/cihaz?status=YOK").status
        )
    }

    @Test
    fun `olmayan talebe cevap yazilamaz`() = testApplication {
        setup(config("r7"))
        val response = client.post("/support/9999/replies") {
            contentType(ContentType.Application.Json)
            setBody("""{"message":"cevap"}""")
        }
        assertEquals(HttpStatusCode.NotFound, response.status)

        val bosMesaj = client.post("/support/9999/replies") {
            contentType(ContentType.Application.Json)
            setBody("""{"message":"  "}""")
        }
        assertEquals(HttpStatusCode.BadRequest, bosMesaj.status)
    }

    @Test
    fun `talep silinince cevaplari da silinir`() = testApplication {
        val cfg = config("r8")
        setup(cfg)
        val id = createTicket("d1", "yardim")
        client.post("/support/$id/replies") {
            contentType(ContentType.Application.Json)
            setBody("""{"message":"cevap"}""")
        }

        assertEquals(HttpStatusCode.NoContent, client.delete("/support/$id").status)
        assertEquals(HttpStatusCode.NotFound, client.get("/support/$id").status)

        val kalanCevap = transaction(cfg.database) { cfg.replyTable.selectAll().count() }
        assertEquals(0L, kalanCevap, "CASCADE ile cevaplar da silinmeli")
    }
}
