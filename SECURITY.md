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
