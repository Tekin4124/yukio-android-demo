# Yükio — Android Demo

Yükio, lojistik ilanlarını araç ve güzergâh tercihlerine göre listeleyen Android uygulamasıdır.
Bu paylaşım sürümü uygulamanın arayüzünü, yerel veritabanını, filtrelerini ve bildirim akışını
incelemek içindir. Ticari ürünün özel parser'ı ve üretim backend'i bu depoda bulunmaz.

**API anahtarı, abonelik ve geliştiriciden erişim istemek gerekmez.** İlk açılışta beş
sentetik ilan görünür. WhatsApp veya bildirim izni vermeden listeyi ve profili deneyebilirsin.

## Hızlı başlangıç

Android Studio'da projeyi aç, SDK 36 ve JDK 17 ile `app` debug sürümünü çalıştır.
Komut satırında:

```bash
./gradlew assembleDebug
```

Windows: `gradlew.bat assembleDebug`. Android SDK yolunu Android Studio ayarlar veya
Git'e alınmayan `local.properties` dosyasında `sdk.dir=...` olarak belirtebilirsin.
APK: `app/build/outputs/apk/debug/app-debug.apk`.

Demo uygulama kimliği `com.yukio.demo`; gerçek Yükio kurulumu ve verileriyle çakışmaz.

## Yerel örnek backend — tek komut

Python 3.10 veya üzeri yeterli; ek paket kurulumu yok:

```bash
python3 demo-backend/server.py
```

Windows: `py demo-backend/server.py`.
Bilgisayarda `http://127.0.0.1:3000` adresini açarak örnek mesajları deneyebilirsin.
Sunucu yalnız bilgisayarın yerel arayüzünde dinler; internete açmayın.

Android uygulamasından denemek için cihazı USB ile bağla veya emülatör aç:

```bash
adb reverse tcp:3000 tcp:3000
```

Uygulamada ana ekranın sağ üstündeki **Demo** düğmesine bas, örnek metni gönder ve
listeye dön. Mesaj yerel backend'e gider; üretilen ilanlar uygulamanın Room veritabanına
kaydolur. WhatsApp izni gerekmez.

Emülatörde alternatif: `./gradlew assembleDebug -PDEMO_BASE_URL=http://10.0.2.2:3000/`.
Port değişirse sunucuyu `--port 3001` ile çalıştır; derleme adresini ve adb reverse
portlarını birlikte değiştir. Demo derlemesi dış sunucu adreslerini kabul etmez.

## Örnek mesaj biçimi

```text
İSTANBUL -> ANKARA | TIR | 20 ton | 18000 TL
BURSA -> İZMİR | KAMYON | 8 ton | 9000 TL
```

Her satır ayrı bir ilan. Desteklenen şehirler: İstanbul, Ankara, İzmir, Bursa,
Antalya, Konya, Adana, Mersin, Kocaeli, Samsun, Tekirdağ ve Trabzon.
Araç, tonaj ve fiyat isteğe bağlı; `->` veya `→` ile açık kaynak/hedef yazılmalıdır.

**Bu sunucu gerçek parser'ın kalitesini temsil etmez.** Serbest WhatsApp mesajı,
ilçe, ortak başlık, karmaşık çok durak, belirsiz araç ve AI fallback desteklenmez.
Amaç API → ilan dönüşümü → veritabanı → ekran akışını çalıştırmaktır.
İletişim numarası üretilmez; gerçek kişisel mesajları kullanmayın.

## Neler incelenebilir?

- Jetpack Compose ile ilan listesi, detay, araç profili ve açık/koyu tema.
- Room yerel veritabanı, filtreler, sıralama ve profil uygunluğu.
- Retrofit ile örnek parser sözleşmesi ve çoklu ilan cevabı.
- İsteğe bağlı WhatsApp bildirim dinleyicisi, maskeleme ve retry.
- Yerel veri temizleme ve bildirim sağlık ayarları.

Demo sürümünde satın alma, üretim erişim token'ı, AI çağrısı, Play güncellemesi,
telemetri ve parser örneği toplama çalıştırılmaz. Harita mevcut kullanıcı akışında
aktif değildir; konum izni istenmez. Geliştirme dosyalarında kullanılan dahili
`com.example.test` namespace'i korunmuştur; paket kimliği ayrıdır.

## Dosya yapısı

```text
app/                  Android istemcisi ve testler
  src/main/.../ui/    Liste, profil ve demo mesaj ekranları
  src/main/.../data/  Room, tercihler ve Retrofit modelleri
  src/main/.../notification/ Bildirim, dönüşüm ve retry
  src/main/.../ai/    Kurallı profil eşleştirmesi
  src/main/.../billing/ Demo erişim durumu ve arayüz modelleri
  src/debug/          Yalnız yerel HTTP izni
 demo-backend/        Bağımsız, basit Python örnek API
 tools/               Paylaşım içeriği kontrolü
```

## Testler

```bash
python3 -m unittest discover -s demo-backend -p 'test_*.py'
python3 tools/check-public-package.py
./gradlew testDebugUnitTest assembleDebug
```

Bildirim erişimini ancak akışı ayrıca denemek istersen aç. Android arka plan
kısıtlamaları nedeniyle gerçek bildirim teslimatı cihaz koşullarına bağlıdır.
Demo varsayılan örneklerle bu izin olmadan kullanılabilir.

Güvenlik kapsamı: [SECURITY.md](SECURITY.md). Örnek API sözleşmesi:
[docs/DEMO-API.md](docs/DEMO-API.md).
