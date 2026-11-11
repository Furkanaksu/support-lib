package com.furkan.support

import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StaleDeleteTest {

    private val json = Json { ignoreUnknownKeys = true }

    private class SilenDepo : SupportPhotoStorage {
        val silinenler = mutableListOf<String>()
        var patla = false
        override suspend fun upload(bytes: ByteArray, fileName: String, contentType: String) = "https://depo/$fileName"
        override suspend fun delete(url: String) {
            if (patla) throw SupportPhotoException("yok")
            silinenler += url
        }
    }

    private fun config(dbName: String, depo: SupportPhotoStorage? = null) = SupportConfig(
        database = Database.connect("jdbc:h2:mem:$dbName;DB_CLOSE_DELAY=-1", driver = "org.h2.Driver"),
        tableName = "stale_$dbName",
        photoStorage = depo
    ).also { it.migrate() }

    /** Tarihleri elle verilen talep; istege bagli bir cevapla. */
    private fun talep(
        cfg: SupportConfig,
        created: LocalDateTime,
        updated: LocalDateTime? = null,
        photo: String? = null,
        replyPhoto: String? = null
    ): Int = transaction(cfg.database) {
        val t = cfg.table
        val id = t.insert {
            it[deviceId] = "d"
            it[email] = "a@b.com"
            it[description] = "x"
            it[photoUrl] = photo
            it[photoUrls] = photo
            it[status] = SupportStatus.OPEN.name
            it[createdDate] = created
            it[updatedDate] = updated
        }[t.id].value
        if (replyPhoto != null || updated != null) {
            cfg.replyTable.insert {
                it[supportId] = EntityID(id, t)
                it[message] = "m"
                it[author] = SupportAuthor.STAFF.name
                it[createdDate] = updated ?: created
                it[photoUrl] = replyPhoto
            }
        }
        id
    }

    private fun ApplicationTestBuilder.setup(cfg: SupportConfig) = application {
        install(ContentNegotiation) { json() }
        routing { supportAdminRoutes(cfg) }
    }

    private fun ids(cfg: SupportConfig) = transaction(cfg.database) {
        cfg.table.selectAll().map { it[cfg.table.id].value }.toSet()
    }

    private fun replyCount(cfg: SupportConfig) = transaction(cfg.database) { cfg.replyTable.selectAll().count() }

    @Test
    fun `son hareketi eski olanlar silinir, yeni cevap alan eski talep kalir`() = testApplication {
        val now = LocalDateTime.now()
        val depo = SilenDepo()
        val cfg = config("s1", depo)
        setup(cfg)

        val eski = talep(cfg, now.minusDays(30), photo = "https://depo/a.jpg")
        val eskiCevapli = talep(cfg, now.minusDays(30), updated = now.minusDays(10), replyPhoto = "https://depo/r.jpg")
        val canli = talep(cfg, now.minusDays(30), updated = now.minusDays(2))
        val yeni = talep(cfg, now.minusDays(1))

        val dry = client.delete("/admin/support?olderThanDays=7&dryRun=true")
        assertEquals(HttpStatusCode.OK, dry.status)
        assertEquals(2, json.decodeFromString<SupportBulkDeleteResponse>(dry.bodyAsText()).deleted)
        assertEquals(setOf(eski, eskiCevapli, canli, yeni), ids(cfg))

        val res = json.decodeFromString<SupportBulkDeleteResponse>(
            client.delete("/admin/support?olderThanDays=7").bodyAsText()
        )
        assertEquals(2, res.deleted)
        assertEquals(2, res.photosDeleted)
        assertEquals(setOf(canli, yeni), ids(cfg))
        assertEquals(1L, replyCount(cfg))
        assertEquals(setOf("https://depo/a.jpg", "https://depo/r.jpg"), depo.silinenler.toSet())
    }

    @Test
    fun `gecersiz gun sayisi 400`() = testApplication {
        val cfg = config("s2")
        setup(cfg)
        assertEquals(HttpStatusCode.BadRequest, client.delete("/admin/support").status)
        assertEquals(HttpStatusCode.BadRequest, client.delete("/admin/support?olderThanDays=0").status)
        assertEquals(HttpStatusCode.BadRequest, client.delete("/admin/support?olderThanDays=abc").status)
    }

    @Test
    fun `fotograf silinemezse kayitlar yine silinir`() = testApplication {
        val depo = SilenDepo().apply { patla = true }
        val cfg = config("s3", depo)
        setup(cfg)
        talep(cfg, LocalDateTime.now().minusDays(9), photo = "https://depo/a.jpg")

        val res = json.decodeFromString<SupportBulkDeleteResponse>(
            client.delete("/admin/support?olderThanDays=7").bodyAsText()
        )
        assertEquals(1, res.deleted)
        assertEquals(1, res.photosFailed)
        assertTrue(ids(cfg).isEmpty())
    }

    @Test
    fun `tek talep silme ucu etkilenmez`() = testApplication {
        val cfg = config("s4")
        setup(cfg)
        val id = talep(cfg, LocalDateTime.now())
        assertEquals(HttpStatusCode.NoContent, client.delete("/admin/support/$id").status)
        assertEquals(HttpStatusCode.NotFound, client.get("/admin/support/$id").status)
    }
}
