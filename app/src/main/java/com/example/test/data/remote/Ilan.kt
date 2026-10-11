package com.example.test.data.remote

import com.example.test.data.model.RouteStop
import com.google.gson.annotations.SerializedName

data class ParseResponse(
    val ilanlar: List<Ilan>?,
    val hata: String?
)

data class Ilan(
    @SerializedName("gonderici_il")
    val gondericiIl: String?,

    @SerializedName("gonderici_ilce")
    val gondericiIlce: String?,

    @SerializedName("alici_il")
    val aliciIl: String?,

    @SerializedName("alici_ilce")
    val aliciIlce: String?,

    @SerializedName("arac_tipi")
    val aracTipi: String?,

    @SerializedName("dorse_tipi")
    val dorseTipi: String?,

    // Deterministik vehicleDetector backend tarafında ekliyor. Filtre
    // eşleştirmesi için kanonik (küçük harf, normalleştirilmiş) etiketler.
    // Eski backend sürümleri bu alanları göndermeyebilir — opsiyonel.
    @SerializedName("arac_tipi_kanonik")
    val aracTipiKanonik: String? = null,

    @SerializedName("dorse_tipi_kanonik")
    val dorseTipiKanonik: String? = null,

    val tonaj: String?,

    val miktar: String?,

    @SerializedName(value = "miktar_birimi", alternate = ["miktarBirimi"])
    val miktarBirimi: String?,

    @SerializedName(value = "agirlik", alternate = ["ağırlık", "agırlık"])
    val agirlik: String?,

    @SerializedName("yuk_cinsi")
    val yukCinsi: String?,

    @SerializedName("yuk_detay")
    val yukDetay: String?,

    val fiyat: String?,

    @SerializedName("fiyat_notu")
    val fiyatNotu: String?,

    @SerializedName("yuklemeTarihi")
    val yuklemeTarihi:  String?,

    val firma: String?,
    val yetkili: String?,
    val telefon: String?,

    // Araç TAHMİNİ (backend vehicleInference): açık araç yazmadığında yük
    // kanıtından türetilen aday listesi. Kesin bilgi değildir; UI "Tahmini
    // araç:" kalıbıyla gösterir, "Bana göre" filtresi bunları kullanmaz.
    // Eski backend sürümleri bu alanları göndermez — opsiyonel.
    @SerializedName("vehicle_candidates")
    val vehicleCandidates: List<String>? = null,

    @SerializedName("vehicle_inferred")
    val vehicleInferred: Boolean? = null,

    @SerializedName("vehicle_confidence")
    val vehicleConfidence: String? = null,

    // Ordered multi-stop route. Older backends omit this field.
    @SerializedName(
        value = "rota_noktalari",
        alternate = ["route_stops", "routeStops"]
    )
    val routeStops: List<RouteStop>? = null
)
