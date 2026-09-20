package com.furkan.support

import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.Op
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.count
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.lowerCase
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.time.LocalDateTime

/**
 * Tum sorgular `transaction(database)` ile, disaridan verilen DB uzerinde calisir.
 * Kutuphane global transaction'a ya da kendi baglantisina asla guvenmez.
 */
class SupportRepository(
    private val database: Database,
    private val table: SupportTable,
    private val replyTable: SupportReplyTable
) {

    fun findPaged(
        page: Int,
        size: Int,
        email: String?,
        deviceId: String?,
        status: SupportStatus? = null
    ): Pair<List<SupportResponse>, Long> = transaction(database) {
        val condition: (SqlExpressionBuilder.() -> Op<Boolean>)? = buildCondition(email, deviceId, status)

        val baseQuery = {
            if (condition != null) table.selectAll().where(condition) else table.selectAll()
        }

        val total = baseQuery().count()

        val rows = baseQuery()
            .orderBy(table.createdDate, SortOrder.DESC)
            .limit(size).offset(((page - 1).coerceAtLeast(0).toLong()) * size)
            .toList()

        val counts = replyCounts(rows.map { it[table.id].value })
        val items = rows.map { it.toSupportResponse(table, counts[it[table.id].value] ?: 0) }

        items to total
    }

    fun findDetail(id: Int): SupportDetailResponse? = transaction(database) {
        val row = table.selectAll()
            .where { table.id eq id }
            .limit(1)
            .firstOrNull() ?: return@transaction null

        row.toSupportDetailResponse(table, repliesOf(id))
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
            it[this.status] = SupportStatus.OPEN.name
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
            status = SupportStatus.OPEN,
            replyCount = 0,
            createdDate = now.toString(),
            updatedDate = null
        )
    }

    /**
     * Talebe cevap ekler ve durumu gunceller:
     * destek ekibi yazdiysa ANSWERED, kullanici yazdiysa tekrar OPEN.
     */
    fun addReply(supportId: Int, message: String, author: SupportAuthor): SupportReplyResponse? =
        transaction(database) {
            val exists = table.selectAll().where { table.id eq supportId }.limit(1).any()
            if (!exists) return@transaction null

            val now = LocalDateTime.now()
            val inserted = replyTable.insert {
                it[this.supportId] = EntityID(supportId, table)
                it[this.message] = message
                it[this.author] = author.name
                it[this.createdDate] = now
            }

            val newStatus = if (author == SupportAuthor.STAFF) SupportStatus.ANSWERED else SupportStatus.OPEN
            table.update({ table.id eq supportId }) {
                it[this.status] = newStatus.name
                it[this.updatedDate] = now
            }

            SupportReplyResponse(
                id = inserted[replyTable.id].value,
                supportId = supportId,
                message = message,
                author = author,
                createdDate = now.toString()
            )
        }

    fun updateStatus(supportId: Int, status: SupportStatus): SupportDetailResponse? = transaction(database) {
        val updated = table.update({ table.id eq supportId }) {
            it[this.status] = status.name
            it[this.updatedDate] = LocalDateTime.now()
        }
        if (updated == 0) return@transaction null

        table.selectAll()
            .where { table.id eq supportId }
            .limit(1)
            .firstOrNull()
            ?.toSupportDetailResponse(table, repliesOf(supportId))
    }

    fun delete(id: Int): Boolean = transaction(database) {
        table.deleteWhere { builder -> builder.run { table.id eq id } } > 0
    }

    private fun buildCondition(
        email: String?,
        deviceId: String?,
        status: SupportStatus?
    ): (SqlExpressionBuilder.() -> Op<Boolean>)? {
        val parts = mutableListOf<SqlExpressionBuilder.() -> Op<Boolean>>()
        if (!email.isNullOrBlank()) {
            parts += { table.email.lowerCase() like "%${email.lowercase()}%" }
        }
        if (!deviceId.isNullOrBlank()) {
            parts += { table.deviceId.lowerCase() like "%${deviceId.lowercase()}%" }
        }
        if (status != null) {
            parts += { table.status eq status.name }
        }
        if (parts.isEmpty()) return null
        return { parts.map { it() }.reduce { acc, op -> acc and op } }
    }

    /** Cihazin kendi talepleri: tam esleme, LIKE degil. */
    fun findByDevicePaged(
        deviceId: String,
        page: Int,
        size: Int,
        status: SupportStatus?
    ): Pair<List<SupportResponse>, Long> = transaction(database) {
        val condition: SqlExpressionBuilder.() -> Op<Boolean> = {
            if (status == null) table.deviceId eq deviceId
            else (table.deviceId eq deviceId) and (table.status eq status.name)
        }

        val total = table.selectAll().where(condition).count()

        val rows = table.selectAll().where(condition)
            .orderBy(table.createdDate, SortOrder.DESC)
            .limit(size).offset(((page - 1).coerceAtLeast(0).toLong()) * size)
            .toList()

        val counts = replyCounts(rows.map { it[table.id].value })
        rows.map { it.toSupportResponse(table, counts[it[table.id].value] ?: 0) } to total
    }

    private fun repliesOf(supportId: Int): List<SupportReplyResponse> =
        replyTable.selectAll()
            .where { replyTable.supportId eq EntityID(supportId, table) }
            .orderBy(replyTable.createdDate, SortOrder.ASC)
            .map { it.toSupportReplyResponse(replyTable) }

    /** Sayfadaki tum talepler icin cevap sayilarini tek sorguda toplar (N+1 yok). */
    private fun replyCounts(ids: List<Int>): Map<Int, Int> {
        if (ids.isEmpty()) return emptyMap()
        val entityIds = ids.map { EntityID(it, table) }
        val counter = replyTable.id.count()
        return replyTable
            .select(replyTable.supportId, counter)
            .where { replyTable.supportId inList entityIds }
            .groupBy(replyTable.supportId)
            .associate { it[replyTable.supportId].value to it[counter].toInt() }
    }
}
