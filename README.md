# support-lib

[![JitPack](https://jitpack.io/v/Furkanaksu/support-lib.svg)](https://jitpack.io/#Furkanaksu/support-lib)

Ktor + Exposed tabanlı, **yeniden kullanılabilir support (destek talebi) kütüphanesi**.
Bir kez yazılır, GitHub'a konur, her yeni projede bağımlılık olarak eklenip iki satırla çağrılır.

Altyapı `furkan.api` ile aynıdır: Gradle Kotlin DSL + version catalog, JDK 21, Kotlin 2.2.21,
Ktor 3.3.2, Exposed 0.61.0, kotlinx.serialization.

## Kritik tasarım kuralları

1. **DB kütüphane içinde `connect` edilmez.** Dışarıdan `Database` objesi alınır, her sorgu
   `transaction(database)` ile çalışır.
2. **Path sabit yazılmaz.** `basePath` parametriktir — `/support`, `/api/v2/destek` fark etmez.
3. **Her şey config'le enjekte edilir.** DB, path, tablo adı, auth hep `SupportConfig`'ten gelir;
   kütüphane hiçbir ortama özel şey bilmez.

## Kurulum (tüketen proje)

`settings.gradle.kts`:

```kotlin
dependencyResolutionManagement {
    repositories {
        mavenCentral()
        maven("https://jitpack.io")
    }
}
```

`build.gradle.kts`:

```kotlin
dependencies {
    implementation("com.github.Furkanaksu:support-lib:1.0.3")
}
```

## Kullanım

```kotlin
fun Application.module() {
    // Bu projenin KENDİ veritabanı
    val db = Database.connect(
        url = "jdbc:postgresql://localhost:5432/proje_a_db",
        driver = "org.postgresql.Driver",
        user = System.getenv("DB_USER"),
        password = System.getenv("DB_PASSWORD")
    )

    val supportConfig = SupportConfig(
        database = db,
        basePath = "/api/destek",        // base link bu projede farklı olabilir
        tableName = "prayapp_support"    // tablo adı da projeye özel
    )
    supportConfig.migrate()              // tabloyu bu DB'de oluştur

    install(ContentNegotiation) { json() }   // tüketen projenin sorumluluğu

    routing {
        supportRoutes(supportConfig)     // hazır
    }
}
```

Auth istenirse, projenin kendi Authentication kurulumu kullanılır:

```kotlin
SupportConfig(database = db, requireAuth = true, authName = "auth-jwt")
```

## Uç noktalar

`basePath` varsayılanı `/support`:

| Metot | Yol | Açıklama |
| --- | --- | --- |
| GET | `{basePath}` | Sayfalı liste — `page`, `size`, `email`, `deviceId` |
| GET | `{basePath}/{id}` | Tek kayıt |
| POST | `{basePath}` | Yeni kayıt — `deviceId`, `email`, `description` zorunlu |
| DELETE | `{basePath}/{id}` | Kayıt sil |

`POST` gövdesi:

```json
{
  "deviceId": "device-1",
  "email": "a@b.com",
  "description": "Uygulamada hata alıyorum",
  "location": "Istanbul",
  "latitude": 41.0,
  "longitude": 29.0
}
```

## Yayınlama (JitPack)

```bash
git tag 1.0.3
git push origin 1.0.3
```

Tag atıldıktan sonra JitPack ilk istekte derler. JDK 21 için repo kökündeki `jitpack.yml` kullanılır.

Yerelde denemek için:

```bash
./gradlew publishToMavenLocal
```

Tüketen projenin `repositories` bloğuna `mavenLocal()` eklemen yeterli.

## Geliştirme

```bash
./gradlew build
```
