package com.furkan.support

import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.javatime.datetime
import org.jetbrains.exposed.sql.transactions.transaction

/**
 * Support tablosu. Tablo adi disaridan geldigi icin `object` degil `class`.
 */
class SupportTable(tableName: String) : IntIdTable(tableName) {
    val deviceId = varchar("device_id", 255).index()
    val email = varchar("email", 255).index()
    val description = text("description")
    val location = varchar("location", 255).nullable()
    val latitude = double("latitude").nullable()
    val longitude = double("longitude").nullable()
    val createdDate = datetime("created_date").index()
}

/**
 * Tabloyu, config'te verilen DB'de olusturur/gunceller.
 * Kutuphane kendiliginden calistirmaz; proje acilista bir kez cagirir.
 */
fun SupportConfig.migrate() = transaction(database) {
    @Suppress("DEPRECATION")
    SchemaUtils.createMissingTablesAndColumns(table)
}
