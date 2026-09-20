package com.furkan.support

import org.jetbrains.exposed.sql.Database

/**
 * Kutuphanenin tum dis bagimliliklari burada toplanir.
 * Kutuphane icinde hicbir DB baglantisi, path ya da tablo adi hardcode DEGILDIR;
 * her tuketen proje kendi degerlerini verir.
 *
 * @param database    Projenin kendi Exposed [Database] objesi. Kutuphane asla connect() cagirmaz.
 * @param basePath    Route'larin monte edilecegi taban yol. Orn. "/support", "/api/v2/destek".
 * @param tableName   Tablo adi. Ayni DB'de birden fazla urun varsa cakismayi onler.
 * @param requireAuth true ise route'lar [authName] ile korunur; projenin Authentication kurulumu gerekir.
 * @param authName    Ktor Authentication provider adi. null ise varsayilan provider kullanilir.
 * @param defaultPageSize Sayfa boyutu verilmezse kullanilacak deger.
 * @param maxPageSize Istemcinin isteyebilecegi en buyuk sayfa boyutu.
 */
data class SupportConfig(
    val database: Database,
    val basePath: String = "/support",
    val tableName: String = "support_tickets",
    val requireAuth: Boolean = false,
    val authName: String? = null,
    val defaultPageSize: Int = 20,
    val maxPageSize: Int = 100
) {
    /** Bu config'e ait tablo tanimi. Tablo adi config'ten geldigi icin object degil, instance'tir. */
    val table: SupportTable by lazy { SupportTable(tableName) }

    init {
        require(basePath.startsWith("/")) { "basePath '/' ile baslamali: $basePath" }
        require(tableName.isNotBlank()) { "tableName bos olamaz" }
        require(defaultPageSize in 1..maxPageSize) { "defaultPageSize 1..maxPageSize araliginda olmali" }
    }
}
