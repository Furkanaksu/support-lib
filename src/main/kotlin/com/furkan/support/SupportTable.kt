package com.furkan.support

import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.javatime.datetime
import org.jetbrains.exposed.sql.transactions.transaction

/**
 * Destek talepleri. Tablo adi disaridan geldigi icin `object` degil `class`.
 */
class SupportTable(tableName: String) : IntIdTable(tableName) {
    val deviceId = varchar("device_id", 255).index()
    val email = varchar("email", 255).index()
    val description = text("description")
    val location = varchar("location", 255).nullable()
    val latitude = double("latitude").nullable()
    val longitude = double("longitude").nullable()
    val status = varchar("status", 20).default(SupportStatus.OPEN.name).index()
    val createdDate = datetime("created_date").index()
    val updatedDate = datetime("updated_date").nullable().default(null)
}

/**
 * Bir talebe yazilan cevaplar. Talep silinince cevaplari da silinir (CASCADE).
 */
class SupportReplyTable(tableName: String, support: SupportTable) : IntIdTable(tableName) {
    val supportId = reference("support_id", support, onDelete = ReferenceOption.CASCADE).index()
    val message = text("message")
    val author = varchar("author", 20)
    val createdDate = datetime("created_date").index()
}

/**
 * Tablolari, config'te verilen DB'de olusturur/gunceller.
 * Kutuphane kendiliginden calistirmaz; proje acilista bir kez cagirir.
 */
fun SupportConfig.migrate() = transaction(database) {
    @Suppress("DEPRECATION")
    SchemaUtils.createMissingTablesAndColumns(table, replyTable)
}
