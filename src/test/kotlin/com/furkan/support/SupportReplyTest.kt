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
            routing {
                supportRoutes(cfg)
                supportAdminRoutes(cfg)
            }
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
            client.get("/admin/support/$id").bodyAsText()
        )
        assertEquals(SupportStatus.OPEN, detail.status)
        assertTrue(detail.replies.isEmpty())
    }

    @Test
    fun `destek ekibi cevap yazinca durum ANSWERED olur ve cevap detayda gorunur`() = testApplication {
        setup(config("r2"))
        val id = createTicket("d1", "yardim")

        val reply = client.post("/admin/support/$id/replies") {
            contentType(ContentType.Application.Json)
            setBody("""{"message":"Merhaba, konuyu inceledik."}""")
        }
        assertEquals(HttpStatusCode.Created, reply.status)
        val replyBody = json.decodeFromString<SupportReplyResponse>(reply.bodyAsText())
        assertEquals(SupportAuthor.STAFF, replyBody.author, "admin ucundan yazilan cevap STAFF")

        val detail = json.decodeFromString<SupportDetailResponse>(
            client.get("/admin/support/$id").bodyAsText()
        )
        assertEquals(SupportStatus.ANSWERED, detail.status)
        assertEquals(1, detail.replies.size)
        assertEquals("Merhaba, konuyu inceledik.", detail.replies.first().message)
    }

    @Test
    fun `kullanici cevap yazinca durum tekrar OPEN olur ve cevaplar sirali doner`() = testApplication {
        setup(config("r3"))
        val id = createTicket("d1", "yardim")

        client.post("/admin/support/$id/replies") {
            contentType(ContentType.Application.Json)
            setBody("""{"message":"Ekip cevabi"}""")
        }
        client.post("/support/device/d1/$id/replies") {
            contentType(ContentType.Application.Json)
            setBody("""{"message":"Kullanici cevabi"}""")
        }

        val detail = json.decodeFromString<SupportDetailResponse>(
            client.get("/admin/support/$id").bodyAsText()
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

        val patched = client.patch("/admin/support/$id/status") {
            contentType(ContentType.Application.Json)
            setBody("""{"status":"CLOSED"}""")
        }
        assertEquals(HttpStatusCode.OK, patched.status)
        assertEquals(
            SupportStatus.CLOSED,
            json.decodeFromString<SupportDetailResponse>(patched.bodyAsText()).status
        )

        val gecersiz = client.patch("/admin/support/$id/status") {
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

        client.post("/admin/support/$benim/replies") {
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
        client.patch("/admin/support/$kapali/status") {
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
        val response = client.post("/admin/support/9999/replies") {
            contentType(ContentType.Application.Json)
            setBody("""{"message":"cevap"}""")
        }
        assertEquals(HttpStatusCode.NotFound, response.status)

        val bosMesaj = client.post("/admin/support/9999/replies") {
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
        client.post("/admin/support/$id/replies") {
            contentType(ContentType.Application.Json)
            setBody("""{"message":"cevap"}""")
        }

        assertEquals(HttpStatusCode.NoContent, client.delete("/admin/support/$id").status)
        assertEquals(HttpStatusCode.NotFound, client.get("/admin/support/$id").status)

        val kalanCevap = transaction(cfg.database) { cfg.replyTable.selectAll().count() }
        assertEquals(0L, kalanCevap, "CASCADE ile cevaplar da silinmeli")
    }

    @Test
    fun `kullanici kendi talebinin detayini gorur, baskasininkini goremez`() = testApplication {
        setup(config("r9"))
        val id = createTicket("benim", "yardim")
        client.post("/admin/support/$id/replies") {
            contentType(ContentType.Application.Json)
            setBody("""{"message":"Ekip cevabi"}""")
        }

        val benim = client.get("/support/device/benim/$id")
        assertEquals(HttpStatusCode.OK, benim.status)
        assertEquals(1, json.decodeFromString<SupportDetailResponse>(benim.bodyAsText()).replies.size)

        assertEquals(HttpStatusCode.NotFound, client.get("/support/device/baskasi/$id").status)
        assertEquals(HttpStatusCode.NotFound, client.get("/support/device/benim/9999").status)
    }

    @Test
    fun `kullanici baskasinin talebine cevap yazamaz ve yazar her zaman USER olur`() = testApplication {
        setup(config("r10"))
        val id = createTicket("benim", "yardim")

        val yabanci = client.post("/support/device/baskasi/$id/replies") {
            contentType(ContentType.Application.Json)
            setBody("""{"message":"araya giriyorum"}""")
        }
        assertEquals(HttpStatusCode.NotFound, yabanci.status)

        val kendi = client.post("/support/device/benim/$id/replies") {
            contentType(ContentType.Application.Json)
            setBody("""{"message":"ek bilgi"}""")
        }
        assertEquals(HttpStatusCode.Created, kendi.status)
        assertEquals(SupportAuthor.USER, json.decodeFromString<SupportReplyResponse>(kendi.bodyAsText()).author)
    }

    @Test
    fun `liste son cevabin yazarini ve tarihini tasir`() = testApplication {
        setup(config("r11"))
        val cevapsiz = createTicket("cihaz", "bekleyen")
        val cevapli = createTicket("cihaz", "cevaplanan")
        client.post("/support/device/cihaz/$cevapli/replies") {
            contentType(ContentType.Application.Json)
            setBody("""{"message":"ek bilgi"}""")
        }
        val ekip = json.decodeFromString<SupportReplyResponse>(
            client.post("/admin/support/$cevapli/replies") {
                contentType(ContentType.Application.Json)
                setBody("""{"message":"cozuldu"}""")
            }.bodyAsText()
        )

        val liste = json.decodeFromString<PaginatedSupportResponse>(
            client.get("/support/device/cihaz").bodyAsText()
        ).data.associateBy { it.id }

        assertEquals(null, liste.getValue(cevapsiz).lastReplyAuthor)
        assertEquals(null, liste.getValue(cevapsiz).lastReplyDate)
        assertEquals(SupportAuthor.STAFF, liste.getValue(cevapli).lastReplyAuthor)
        assertEquals(ekip.createdDate, liste.getValue(cevapli).lastReplyDate)
        assertEquals(2, liste.getValue(cevapli).replyCount)
    }

    @Test
    fun `admin uclari kullanici uclariyla birlikte gelmez`() = testApplication {
        val cfg = config("r12")
        application {
            install(ContentNegotiation) { json() }
            routing { supportRoutes(cfg) }
        }
        val id = createTicket("d1", "yardim")

        assertTrue(client.get("/support").status != HttpStatusCode.OK, "herkesin listesi acik olmamali")
        assertEquals(HttpStatusCode.NotFound, client.get("/admin/support").status)
        assertEquals(HttpStatusCode.NotFound, client.delete("/admin/support/$id").status)
        assertEquals(HttpStatusCode.NotFound, client.get("/support/$id").status)
    }
}
