# Demo güvenlik sınırları

Bu depo yalnız Android demo istemcisini ve sıfırdan yazılmış yerel örnek sunucuyu içerir.
Özel parser, üretim backend'i, prompt, gold veri, sunucu yönetimi, sır ve geçmiş taşınmaz.
Demo API'ı kimlik doğrulamaz ve yalnız 127.0.0.1 üzerinde dinler; internete açmayın.
Ödeme, satın alma doğrulaması, AI ve telemetri demoda çalıştırılmaz.
Debug HTTP izni yalnız localhost, 127.0.0.1 ve Android emülatör adresiyle sınırlıdır.
Üretim uygulamasından ayrı com.yukio.demo kimliği kullanılır.

Ekranlar yerel örneklerle izin vermeden kullanılabilir. Bildirim erişimi isteğe bağlıdır;
açılırsa yalnız sentetik mesajlarla test edin. Örnek sunucu mesajları diske yazmaz,
uzak servise göndermez ve istek gövdelerini loglamaz. Android yerel ilan/veri temizleme
ayarları profilde bulunur. Debug sürümünü gerçek kişisel mesajlar için kullanmayın.

## v44 yerel özellik sınırı

- İlan ekleme ve referans akışları Android içinde çalışır. Özel API, erişim token'ı,
  kurulum imzası, admin/partner kod üretimi veya gerçek ödeme kodu taşınmadı.
- İlan onayı genel bir ağa yayın yapmaz; yalnız bu demo paketinin cihaz deposunu
  günceller. Ham giriş metni yayın deposunda saklanmaz. Önizleme uygulama belleğindedir.
- Telefon alanı yalnız 0000 ile başlayan aranamayan örnekleri kabul eder; demo
  kartlarından telefon uygulaması açılmaz. Gerçek kişisel veri kullanmayın.
- Referans kodları sabit DEMO-MINE ve DEMO-FRIEND; iki sanal hesabın ödülünü
  gösterir. EntitlementRuntime veya gerçek abonelik değiştirilmez.
- İsteğe bağlı eski örnek API bağlantısı derleme sırasında ve her HTTP isteğinde
  yalnız 127.0.0.1, localhost ve 10.0.2.2 ile sınırlıdır. HTTP yönlendirmeleri
  kapalıdır; localhost çözümü 127.0.0.1'e sabitlenmiştir.
- Yerel demo, kimlik doğrulanmış çok kullanıcılı sunucu örneği değildir.
  Cihazdaki dosyayı değiştirmek yerel simülasyonu değiştirebilir; bu gerçek
  ürün güvenliğini veya hesap yetkisini temsil etmez.
