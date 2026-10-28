package com.furkan.support

import org.jetbrains.exposed.sql.Database

/**
 * Kutuphanenin tum dis bagimliliklari burada toplanir.
 * Kutuphane icinde hicbir DB baglantisi, path ya da tablo adi hardcode DEGILDIR;
 * her tuketen proje kendi degerlerini verir.
 *
 * @param database       Projenin kendi Exposed [Database] objesi. Kutuphane asla connect() cagirmaz.
 * @param basePath       Kullanici uclarinin taban yolu. Orn. "/support", "/api/v2/destek".
 * @param adminBasePath  Destek ekibi uclarinin taban yolu ([supportAdminRoutes]). Varsayilan: "/admin<basePath>".
 * @param tableName      Talep tablosunun adi. Ayni DB'de birden fazla urun varsa cakismayi onler.
 * @param replyTableName Cevap tablosunun adi. Varsayilan: "<tableName>_replies".
 * @param requireAuth    true ise kullanici uclari [authName] ile korunur; projenin Authentication kurulumu
 *                       gerekir. Admin uclarini proje kendi yetkisinin icine monte eder.
 * @param authName       Ktor Authentication provider adi. null ise varsayilan provider kullanilir.
 * @param defaultPageSize Sayfa boyutu verilmezse kullanilacak deger.
 * @param maxPageSize    Istemcinin isteyebilecegi en buyuk sayfa boyutu.
 * @param photoStorage   Talebe eklenen fotografi saklayan taraf. null ise fotograf KAPALI.
 * @param maxPhotoBytes  Kabul edilen en buyuk dosya boyutu. Asilirsa 413 doner.
 * @param maxPhotos      Bir talebe eklenebilecek en fazla fotograf. Fazlasi yok sayilir. Cevaplara tek fotograf.
 * @param allowedPhotoTypes Kabul edilen icerik turleri. Disindaki tur 415 alir.
 * @param onCreate       Yeni talep olusunca cagrilir. Projeye ozel yan etkiler icindir
 *                       (admin e-postasi, Slack bildirimi, log). Kutuphane sonucu beklemez
 *                       ve firlattigi hata istegi bozmaz: talep yine 201 doner.
 */
data class SupportConfig(
    val database: Database,
    val basePath: String = "/support",
    val adminBasePath: String = "/admin$basePath",
    val tableName: String = "support_tickets",
    val replyTableName: String = "${tableName}_replies",
    val requireAuth: Boolean = false,
    val authName: String? = null,
    val defaultPageSize: Int = 20,
    val maxPageSize: Int = 100,
    val onCreate: (SupportResponse) -> Unit = { },
    val photoStorage: SupportPhotoStorage? = null,
    val maxPhotoBytes: Long = 5L * 1024 * 1024,
    val maxPhotos: Int = 3,
    val allowedPhotoTypes: Set<String> = setOf("image/jpeg", "image/png", "image/webp")
) {
    /** Bu config'e ait tablo tanimlari. Tablo adlari config'ten geldigi icin object degil, instance. */
    val table: SupportTable by lazy { SupportTable(tableName) }

    val replyTable: SupportReplyTable by lazy { SupportReplyTable(replyTableName, table) }

    init {
        require(basePath.startsWith("/")) { "basePath '/' ile baslamali: $basePath" }
        require(adminBasePath.startsWith("/")) { "adminBasePath '/' ile baslamali: $adminBasePath" }
        require(adminBasePath != basePath) { "adminBasePath, basePath ile ayni olamaz" }
        require(tableName.isNotBlank()) { "tableName bos olamaz" }
        require(replyTableName.isNotBlank()) { "replyTableName bos olamaz" }
        require(replyTableName != tableName) { "replyTableName, tableName ile ayni olamaz" }
        require(defaultPageSize in 1..maxPageSize) { "defaultPageSize 1..maxPageSize araliginda olmali" }
        require(maxPhotoBytes > 0) { "maxPhotoBytes pozitif olmali" }
        require(maxPhotos >= 1) { "maxPhotos en az 1 olmali" }
        require(allowedPhotoTypes.isNotEmpty()) { "allowedPhotoTypes bos olamaz" }
    }
}
