# Yerel örnek API

`POST /parse-ilan`, `Content-Type: application/json`:

```json
{"text":"İSTANBUL -> ANKARA | TIR | 20 ton | 18000 TL"}
```

Cevap: `{ "ilanlar": [...], "hata": null }`. Her ilanda kaynak/hedef şehir,
isteğe bağlı açık araç/tonaj/fiyat ve sıralı `rota_noktalari` bulunur.
`X-Parse-Source: LOCAL_DEMO`, `X-Parse-Requires-Review: 0` başlıkları verilir.
Tanınmayan metinde boş ilan listesi döner; sahte rota uydurulmaz.

İstek gövdesi en fazla 32 KiB, `text` en fazla 8.000 karakterdir.
Bozuk JSON/alan: 400; büyük istek: 413; JSON olmayan içerik: 415.
Admin, abonelik, örnek toplama ve AI endpoint'leri bulunmaz.

`GET /`: tarayıcıdan deneme; `GET /health` ve `/healthz`: yerel sağlık;
`GET /privacy`: örnek servisin veri davranışı.

Sunucu girdi veya sonucu diske yazmaz, ağ servisine göndermez. Kimlik doğrulama
ve üretim hizmeti sağlamaz; localhost dışına bağlama seçeneği bilerek yoktur.
