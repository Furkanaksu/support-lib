package com.furkan.support

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.Op
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.lowerCase
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.LocalDateTime

/**
 * Tum sorgular `transaction(database)` ile, disaridan verilen DB uzerinde calisir.
 * Kutuphane global transaction'a ya da kendi baglantisina asla guvenmez.
 */
class SupportRepository(
    private val database: Database,
    private val table: SupportTable
) {

    fun findPaged(
        page: Int,
        size: Int,
        email: String?,
        deviceId: String?
    ): Pair<List<SupportResponse>, Long> = transaction(database) {
        val condition: (SqlExpressionBuilder.() -> Op<Boolean>)? = when {
            !email.isNullOrBlank() && !deviceId.isNullOrBlank() -> {
                {
                    (table.email.lowerCase() like "%${email.lowercase()}%") and
                        (table.deviceId.lowerCase() like "%${deviceId.lowercase()}%")
                }
            }
            !email.isNullOrBlank() -> {
                { table.email.lowerCase() like "%${email.lowercase()}%" }
            }
            !deviceId.isNullOrBlank() -> {
                { table.deviceId.lowerCase() like "%${deviceId.lowercase()}%" }
            }
            else -> null
        }

        val baseQuery = {
            if (condition != null) table.selectAll().where(condition) else table.selectAll()
        }

        val total = baseQuery().count()

        val items = baseQuery()
            .orderBy(table.createdDate, SortOrder.DESC)
            .limit(size).offset(((page - 1).coerceAtLeast(0).toLong()) * size)
            .map { it.toSupportResponse(table) }

        items to total
    }

    fun findById(id: Int): SupportResponse? = transaction(database) {
        table.selectAll()
            .where { table.id eq id }
            .limit(1)
            .map { it.toSupportResponse(table) }
            .firstOrNull()
    }

    fun create(
        deviceId: String,
        email: String,
        description: String,
        location: String?,
        latitude: Double?,
        longitude: Double?
    ): SupportResponse = transaction(database) {
        val now = LocalDateTime.now()
        val inserted = table.insert {
            it[this.deviceId] = deviceId
            it[this.email] = email
            it[this.description] = description
            it[this.location] = location
            it[this.latitude] = latitude
            it[this.longitude] = longitude
            it[this.createdDate] = now
        }

        SupportResponse(
            id = inserted[table.id].value,
            deviceId = deviceId,
            email = email,
            description = description,
            location = location,
            latitude = latitude,
            longitude = longitude,
            createdDate = now.toString()
        )
    }

    fun delete(id: Int): Boolean = transaction(database) {
        table.deleteWhere { builder -> builder.run { table.id eq id } } > 0
    }
}
