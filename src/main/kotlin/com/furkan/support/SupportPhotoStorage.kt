package com.furkan.support

/**
 * Destek talebine eklenen fotografi saklayan taraf.
 *
 * Kutuphane dosyayi NEREYE koyacagini bilmez; sadece baytlari verir ve karsiliginda
 * erisilebilir bir adres bekler. Projeler kendi depolarini baglar (Supabase, S3, disk...):
 *
 * ```
 * SupportConfig(
 *     database = db,
 *     photoStorage = SupportPhotoStorage { bytes, fileName, contentType ->
 *         SupabaseStorageService.upload(fileName, bytes, contentType)
 *     }
 * )
 * ```
 *
 * [SupportConfig.photoStorage] null birakilirsa fotograf ozelligi KAPALIDIR: multipart
 * istek gelse bile fotograf yok sayilmaz, istek 400 ile reddedilir.
 */
fun interface SupportPhotoStorage {

    /**
     * @param bytes Dosyanin icerigi.
     * @param fileName Kutuphanenin urettigi benzersiz ad (orn. "support-1730000000-a1b2.jpg").
     *        Proje isterse yok sayip kendi adini uretebilir.
     * @param contentType Istemcinin bildirdigi tur; [SupportConfig.allowedPhotoTypes] ile dogrulanmistir.
     * @return Fotografa erisilecek adres. Cevapta `photoUrl` olarak doner.
     * @throws SupportPhotoException yukleme basarisizsa.
     */
    suspend fun upload(bytes: ByteArray, fileName: String, contentType: String): String
}

/** Fotograf saklanamadi: ag hatasi, kota, yetki... */
class SupportPhotoException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
