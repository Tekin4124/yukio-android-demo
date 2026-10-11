# Doğrulama — 11 Ekim 2026

- Android debug APK derlendi: `assembleDebug` başarılı.
- Android JVM testleri: 121 test, 0 başarısız, 0 hata, 0 atlanan.
- Yerel demo backend: 8 test başarılı.
- Demo JSON cevabının Android ilan modeline dönüşümü ortak fixture ile doğrulandı.
- Paylaşım sınırı ve bilinen yerel credential değerleri için kontrol: eşleşme yok.
- Üretim backend'i, özel parser, prompt, gerçek gold veri, imzalama dosyaları
  ve geçmiş Git nesneleri paylaşım paketine alınmadı.

Bu kontroller tam güvenlik denetimi değildir. Gerçek cihaz/emülatör üzerinde
arayüz tıklama ve WhatsApp bildirim teslimatı bu doğrulamada çalıştırılmadı.
APK debug imzalıdır; Play yayını için hazırlanmış üretim paketi değildir.
