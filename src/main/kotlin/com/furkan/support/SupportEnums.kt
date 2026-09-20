package com.furkan.support

import kotlinx.serialization.Serializable

/** Destek talebinin durumu. Yeni talep OPEN acilir. */
@Serializable
enum class SupportStatus {
    /** Acik: henuz cevaplanmadi ya da kullanici son sozu soyledi. */
    OPEN,

    /** Cevaplandi: son cevabi destek ekibi yazdi. */
    ANSWERED,

    /** Kapatildi: islem tamamlandi. */
    CLOSED;

    companion object {
        fun fromOrNull(value: String?): SupportStatus? =
            entries.firstOrNull { it.name.equals(value?.trim(), ignoreCase = true) }
    }
}

/** Cevabi kimin yazdigi. */
@Serializable
enum class SupportAuthor {
    /** Talebi acan kullanici. */
    USER,

    /** Destek ekibi. */
    STAFF;

    companion object {
        fun fromOrNull(value: String?): SupportAuthor? =
            entries.firstOrNull { it.name.equals(value?.trim(), ignoreCase = true) }
    }
}
