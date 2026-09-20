package com.furkan.support

class SupportService(private val repository: SupportRepository) {

    fun getSupports(
        page: Int,
        size: Int,
        email: String?,
        deviceId: String?,
        status: SupportStatus?
    ): Pair<List<SupportResponse>, Long> = repository.findPaged(page, size, email, deviceId, status)

    /** Kullanicinin kendi talepleri: deviceId tam eslesir. */
    fun getSupportsByDevice(
        deviceId: String,
        page: Int,
        size: Int,
        status: SupportStatus?
    ): Pair<List<SupportResponse>, Long> = repository.findByDevicePaged(deviceId, page, size, status)

    fun getSupportDetail(id: Int): SupportDetailResponse? = repository.findDetail(id)

    fun createSupport(
        deviceId: String,
        email: String,
        description: String,
        location: String?,
        latitude: Double?,
        longitude: Double?
    ): SupportResponse? = try {
        repository.create(deviceId, email, description, location, latitude, longitude)
    } catch (e: Exception) {
        null
    }

    fun addReply(supportId: Int, message: String, author: SupportAuthor): SupportReplyResponse? =
        repository.addReply(supportId, message, author)

    fun updateStatus(supportId: Int, status: SupportStatus): SupportDetailResponse? =
        repository.updateStatus(supportId, status)

    fun deleteSupport(id: Int): Boolean = repository.delete(id)
}
