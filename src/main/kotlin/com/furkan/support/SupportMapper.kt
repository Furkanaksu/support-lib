package com.furkan.support

import org.jetbrains.exposed.sql.ResultRow

internal fun ResultRow.toSupportResponse(table: SupportTable, replyCount: Int) = SupportResponse(
    id = this[table.id].value,
    deviceId = this[table.deviceId],
    email = this[table.email],
    description = this[table.description],
    location = this[table.location],
    latitude = this[table.latitude],
    longitude = this[table.longitude],
    status = SupportStatus.fromOrNull(this[table.status]) ?: SupportStatus.OPEN,
    replyCount = replyCount,
    createdDate = this[table.createdDate].toString(),
    updatedDate = this[table.updatedDate]?.toString()
)

internal fun ResultRow.toSupportDetailResponse(
    table: SupportTable,
    replies: List<SupportReplyResponse>
) = SupportDetailResponse(
    id = this[table.id].value,
    deviceId = this[table.deviceId],
    email = this[table.email],
    description = this[table.description],
    location = this[table.location],
    latitude = this[table.latitude],
    longitude = this[table.longitude],
    status = SupportStatus.fromOrNull(this[table.status]) ?: SupportStatus.OPEN,
    createdDate = this[table.createdDate].toString(),
    updatedDate = this[table.updatedDate]?.toString(),
    replies = replies
)

internal fun ResultRow.toSupportReplyResponse(table: SupportReplyTable) = SupportReplyResponse(
    id = this[table.id].value,
    supportId = this[table.supportId].value,
    message = this[table.message],
    author = SupportAuthor.fromOrNull(this[table.author]) ?: SupportAuthor.STAFF,
    createdDate = this[table.createdDate].toString()
)
