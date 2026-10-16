# Demo v44 — özellik aktarımı

16 Ekim 2026. Sürüm kodu 44, sürüm adı 1.0.8-demo, uygulama kimliği com.yukio.demo.

Aktarılan istemci davranışları: yeni ilan / ilanlarım düzeni, kart önizlemesi ve
seçimi, eksik iletişim alanı isteme, yayın öncesi ve sonrası düzenleme, tekrar
engelleme, 24 saatlik yerel gösterim ve dakikada 150 yeni ilan sınırı. Detay ayrı
tam ekran sayfadır. Referans ekranı iki sanal hesapla 14'er günlük ödül gösterir.

İlanlar ve ödül durumu yalnız demo paketinin cihaz deposunda kalır. Gerçek ortak
listeye yayın, gerçek premium/ortaklık kodu üretimi veya ödeme doğrulaması yoktur.
Yeni akışlar için sunucu başlatmak gerekmez. Telefon alanı yalnız 0000 ile başlayan
örnek numaraları kabul eder; arama kapalıdır. Kullanıcı yerel verileri temizleyebilir.

Özel parser, üretim backend'i, prompt, gerçek ilan/veri, sunucu yönetim dosyaları,
kurulum doğrulama/abonelik transport'u, üretim URL'si ve imza anahtarları taşınmadı.
Üretim uygulaması ve özel depo değiştirilmedi. Optional yerel örnek API bağlantısı
hem derleme hem her HTTP isteğinde yalnız yerel adreslere izin verir; yönlendirmeler
kapalıdır. Yerel demo çok kullanıcılı sunucu güvenliği örneği değildir.

Doğrulama: 129 Android birim testi (0 hata), yerel Python API'ın 8 testi (0 hata),
lint başarılı, paylaşılabilir kaynak denetimi, APK üzerinde bilinen üretim adresi / credential
örüntü denetimi, sürüm ve uygulama kimliği kontrolü. Bu kontroller tam kapsamlı
bağımsız güvenlik denetimi anlamına gelmez. Fiziksel telefon bağlı değildi;
emülatör bu ortamda güvenilir biçimde açılamadığı için yeni ekranların çalışma
zamanı görsel kontrolü tamamlanamadı.
