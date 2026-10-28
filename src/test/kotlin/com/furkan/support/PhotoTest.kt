package com.furkan.support

import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.get
import io.ktor.client.request.forms.formData
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
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
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Fotografi saklamis gibi yapar; ne aldigini kaydeder. */
private class SahteDepo(var hata: String? = null) : SupportPhotoStorage {
    val adlar = mutableListOf<String>()
    val turler = mutableListOf<String>()
    val boyutlar = mutableListOf<Int>()
    var cagriSayisi = 0

    override suspend fun upload(bytes: ByteArray, fileName: String, contentType: String): String {
        cagriSayisi++
        adlar += fileName
        turler += contentType
        boyutlar += bytes.size
        hata?.let { throw SupportPhotoException(it) }
        return "https://depo.ornek.com/$fileName"
    }
}

class PhotoTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun config(
        dbName: String,
        depo: SupportPhotoStorage? = null,
        maxPhotoBytes: Long = 5L * 1024 * 1024
    ) = SupportConfig(
        database = Database.connect("jdbc:h2:mem:$dbName;DB_CLOSE_DELAY=-1", driver = "org.h2.Driver"),
        tableName = "${dbName}_support",
        photoStorage = depo,
        maxPhotoBytes = maxPhotoBytes
    ).also { it.migrate() }

    private fun ApplicationTestBuilder.setup(config: SupportConfig) {
        application {
            install(ContentNegotiation) { json() }
            routing {
                supportRoutes(config)
                supportAdminRoutes(config)
            }
        }
    }

    private suspend fun ApplicationTestBuilder.fotografli(
        bytes: ByteArray = ByteArray(128) { 1 },
        tur: String = "image/jpeg",
        dosyaAdi: String = "ekran.jpg",
        aciklama: String = "Uygulama acilmiyor"
    ): HttpResponse = client.post("/support") {
        setBody(
            MultiPartFormDataContent(
                formData {
                    append("deviceId", "cihaz-1")
                    append("email", "ali@ornek.com")
                    append("description", aciklama)
                    append("location", "Istanbul")
                    append("latitude", "41.0")
                    append(
                        "photo", bytes,
                        Headers.build {
                            append(HttpHeaders.ContentType, tur)
                            append(HttpHeaders.ContentDisposition, "filename=\"$dosyaAdi\"")
                        }
                    )
                }
            )
        )
    }

    private suspend inline fun <reified T> HttpResponse.decode(): T = json.decodeFromString(bodyAsText())

    @Test
    fun `fotografli talep depoya yuklenir ve adresi kayda yazilir`() = testApplication {
        val depo = SahteDepo()
        setup(config("f1", depo))

        val cevap = fotografli(bytes = ByteArray(2048) { 7 })

        assertEquals(HttpStatusCode.Created, cevap.status)
        val kayit = cevap.decode<SupportResponse>()
        assertEquals("https://depo.ornek.com/${depo.adlar.single()}", kayit.photoUrl)
        assertEquals("Istanbul", kayit.location)
        assertEquals(41.0, kayit.latitude)
        assertEquals(1, depo.cagriSayisi)
        assertEquals(2048, depo.boyutlar.single(), "dosyanin tamami depoya gitti")
        assertEquals("image/jpeg", depo.turler.single())
        assertTrue(depo.adlar.single().endsWith(".jpg"), "ad uzantiyi korur: ${depo.adlar.single()}")
        assertTrue(depo.adlar.single().startsWith("support-"), "ad kutuphane tarafindan uretilir")
    }

    @Test
    fun `fotograf opsiyonel - multipart ta dosya yoksa talep yine olusur`() = testApplication {
        val depo = SahteDepo()
        setup(config("f2", depo))

        val cevap = client.post("/support") {
            setBody(
                MultiPartFormDataContent(
                    formData {
                        append("deviceId", "cihaz-1")
                        append("email", "ali@ornek.com")
                        append("description", "Fotografsiz talep")
                    }
                )
            )
        }

        assertEquals(HttpStatusCode.Created, cevap.status)
        assertNull(cevap.decode<SupportResponse>().photoUrl)
        assertEquals(0, depo.cagriSayisi, "dosya yoksa depoya hic gidilmez")
    }

    @Test
    fun `JSON govdesi eskisi gibi calisir`() = testApplication {
        val depo = SahteDepo()
        setup(config("f3", depo))

        val cevap = client.post("/support") {
            contentType(ContentType.Application.Json)
            setBody("""{"deviceId":"cihaz-1","email":"ali@ornek.com","description":"Eski istemci"}""")
        }

        assertEquals(HttpStatusCode.Created, cevap.status)
        assertNull(cevap.decode<SupportResponse>().photoUrl)
        assertEquals(0, depo.cagriSayisi)
    }

    @Test
    fun `fotografi kendi yukleyen istemci adresi JSON ile gonderebilir`() = testApplication {
        val depo = SahteDepo()
        setup(config("f4", depo))

        val cevap = client.post("/support") {
            contentType(ContentType.Application.Json)
            setBody(
                """{"deviceId":"cihaz-1","email":"ali@ornek.com","description":"Kendi yukledim",
                   "photoUrl":"https://baska.com/foto.jpg"}"""
            )
        }

        assertEquals(HttpStatusCode.Created, cevap.status)
        assertEquals("https://baska.com/foto.jpg", cevap.decode<SupportResponse>().photoUrl)
        assertEquals(0, depo.cagriSayisi, "adres verildiginde depoya gidilmez")
    }

    @Test
    fun `cok buyuk dosya 413 alir ve depoya gitmez`() = testApplication {
        val depo = SahteDepo()
        setup(config("f5", depo, maxPhotoBytes = 1024))

        val cevap = fotografli(bytes = ByteArray(4096) { 3 })

        assertEquals(HttpStatusCode.PayloadTooLarge, cevap.status)
        assertEquals(0, depo.cagriSayisi)
        assertTrue(cevap.bodyAsText().contains("MB"), cevap.bodyAsText())
    }

    @Test
    fun `desteklenmeyen tur 415 alir`() = testApplication {
        val depo = SahteDepo()
        setup(config("f6", depo))

        val cevap = fotografli(tur = "application/pdf", dosyaAdi = "belge.pdf")

        assertEquals(HttpStatusCode.UnsupportedMediaType, cevap.status)
        assertEquals(0, depo.cagriSayisi)
    }

    @Test
    fun `depo yapilandirilmamissa fotograf reddedilir`() = testApplication {
        setup(config("f7", depo = null))

        val cevap = fotografli()

        assertEquals(HttpStatusCode.BadRequest, cevap.status)
        assertTrue(cevap.bodyAsText().contains("kapali"), cevap.bodyAsText())
    }

    @Test
    fun `depo patlarsa talep olusmaz ve kullaniciya ne yapacagi soylenir`() = testApplication {
        val depo = SahteDepo(hata = "S3 erisilemedi")
        val cfg = config("f8", depo)
        setup(cfg)

        val cevap = fotografli()

        assertEquals(HttpStatusCode.ServiceUnavailable, cevap.status)
        assertTrue(cevap.bodyAsText().contains("fotografsiz"), cevap.bodyAsText())
    }

    @Test
    fun `eksik alan varsa depoya hic gidilmez`() = testApplication {
        val depo = SahteDepo()
        setup(config("f9", depo))

        val cevap = fotografli(aciklama = "")

        assertEquals(HttpStatusCode.BadRequest, cevap.status)
        assertEquals(0, depo.cagriSayisi, "dogrulama once, yukleme sonra")
    }

    @Test
    fun `png ve webp kabul edilir, ad uzantiyi korur`() = testApplication {
        val depo = SahteDepo()
        setup(config("f10", depo))

        assertEquals(HttpStatusCode.Created, fotografli(tur = "image/png", dosyaAdi = "a.png").status)
        assertEquals(HttpStatusCode.Created, fotografli(tur = "image/webp", dosyaAdi = "b.webp").status)
        assertTrue(depo.adlar[0].endsWith(".png"), depo.adlar[0])
        assertTrue(depo.adlar[1].endsWith(".webp"), depo.adlar[1])
    }

    @Test
    fun `fotograf detay ve liste uclarinda da doner`() = testApplication {
        val depo = SahteDepo()
        val cfg = config("f11", depo)
        setup(cfg)
        val id = fotografli().decode<SupportResponse>().id

        val detay = client.post("/admin/support/$id/replies") {
            contentType(ContentType.Application.Json)
            setBody("""{"message":"Bakiyoruz"}""")
        }
        assertEquals(HttpStatusCode.Created, detay.status)

        val kayit = client.get("/admin/support/$id").decode<SupportDetailResponse>()
        assertTrue(kayit.photoUrl!!.startsWith("https://depo.ornek.com/"), kayit.photoUrl!!)
    }

    private suspend fun ApplicationTestBuilder.fotografliCevap(
        yol: String,
        mesaj: String? = "Ekran goruntusu bu",
        bytes: ByteArray? = ByteArray(64) { 2 }
    ): HttpResponse = client.post(yol) {
        setBody(
            MultiPartFormDataContent(
                formData {
                    mesaj?.let { append("message", it) }
                    bytes?.let {
                        append(
                            "photo", it,
                            Headers.build {
                                append(HttpHeaders.ContentType, "image/jpeg")
                                append(HttpHeaders.ContentDisposition, "filename=\"c.jpg\"")
                            }
                        )
                    }
                }
            )
        )
    }

    @Test
    fun `kullanici cevabina fotograf ekleyebilir, detayda ve son cevapta gorunur`() = testApplication {
        val depo = SahteDepo()
        setup(config("f12", depo))
        val id = fotografli().decode<SupportResponse>().id

        val cevap = fotografliCevap("/support/device/cihaz-1/$id/replies")
        assertEquals(HttpStatusCode.Created, cevap.status)
        val body = cevap.decode<SupportReplyResponse>()
        assertEquals(SupportAuthor.USER, body.author)
        assertEquals("Ekran goruntusu bu", body.message)
        assertTrue(body.photoUrl!!.startsWith("https://depo.ornek.com/support-"), body.photoUrl!!)

        val kayit = client.get("/support/device/cihaz-1/$id").decode<SupportDetailResponse>()
        assertEquals(body.photoUrl, kayit.replies.single().photoUrl)
        assertEquals(2, depo.cagriSayisi, "talep + cevap fotografi")
    }

    @Test
    fun `fotograf varsa mesaj bos olabilir, ikisi de yoksa 400`() = testApplication {
        val depo = SahteDepo()
        setup(config("f13", depo))
        val id = fotografli().decode<SupportResponse>().id

        val sadeceFoto = fotografliCevap("/support/device/cihaz-1/$id/replies", mesaj = null)
        assertEquals(HttpStatusCode.Created, sadeceFoto.status)
        assertEquals("", sadeceFoto.decode<SupportReplyResponse>().message)

        val bos = fotografliCevap("/support/device/cihaz-1/$id/replies", mesaj = "  ", bytes = null)
        assertEquals(HttpStatusCode.BadRequest, bos.status)
    }

    @Test
    fun `baskasinin talebine fotografli cevap 404 alir ve depoya gitmez`() = testApplication {
        val depo = SahteDepo()
        setup(config("f14", depo))
        val id = fotografli().decode<SupportResponse>().id
        val once = depo.cagriSayisi

        val cevap = fotografliCevap("/support/device/baska-cihaz/$id/replies")
        assertEquals(HttpStatusCode.NotFound, cevap.status)
        assertEquals(once, depo.cagriSayisi)
    }

    @Test
    fun `ekip de cevabina fotograf ekleyebilir, JSON cevaplarda photoUrl bos kalir`() = testApplication {
        val depo = SahteDepo()
        setup(config("f15", depo))
        val id = fotografli().decode<SupportResponse>().id

        assertEquals(HttpStatusCode.Created, fotografliCevap("/admin/support/$id/replies").status)
        val json = client.post("/admin/support/$id/replies") {
            contentType(ContentType.Application.Json)
            setBody("""{"message":"Duz metin"}""")
        }.decode<SupportReplyResponse>()
        assertNull(json.photoUrl)

        val kayit = client.get("/admin/support/$id").decode<SupportDetailResponse>()
        assertEquals(listOf(true, false), kayit.replies.map { it.photoUrl != null })
    }

    @Test
    fun `depo yoksa fotografli cevap reddedilir`() = testApplication {
        setup(config("f16", depo = null))
        val id = client.post("/support") {
            contentType(ContentType.Application.Json)
            setBody("""{"deviceId":"cihaz-1","email":"a@b.com","description":"x"}""")
        }.decode<SupportResponse>().id

        assertEquals(HttpStatusCode.BadRequest, fotografliCevap("/support/device/cihaz-1/$id/replies").status)
    }
}
