package com.furkan.support

import org.jetbrains.exposed.sql.ResultRow

internal fun ResultRow.toSupportResponse(table: SupportTable) = SupportResponse(
    id = this[table.id].value,
    deviceId = this[table.deviceId],
    email = this[table.email],
    description = this[table.description],
    location = this[table.location],
    latitude = this[table.latitude],
    longitude = this[table.longitude],
    createdDate = this[table.createdDate].toString()
)
