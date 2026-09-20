package com.furkan.support

class SupportService(private val repository: SupportRepository) {

    fun getSupports(
        page: Int,
        size: Int,
        email: String?,
        deviceId: String?
    ): Pair<List<SupportResponse>, Long> = repository.findPaged(page, size, email, deviceId)

    fun getSupport(id: Int): SupportResponse? = repository.findById(id)

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

    fun deleteSupport(id: Int): Boolean = repository.delete(id)
}
