package com.example.test.ui.format

import com.example.test.data.local.LoadOfferEntity
import com.example.test.data.local.deliveryStops
import com.example.test.data.local.pickupStops
import com.example.test.data.model.LoadMarker
import java.util.Locale

private val TurkishLocale = Locale("tr", "TR")

private val PlaceNames = mapOf(
    "ADANA" to "Adana",
    "ADIYAMAN" to "Adıyaman",
    "AFYONKARAHISAR" to "Afyonkarahisar",
    "AGRI" to "Ağrı",
    "AKSARAY" to "Aksaray",
    "AMASYA" to "Amasya",
    "ANKARA" to "Ankara",
    "ANTALYA" to "Antalya",
    "ARDAHAN" to "Ardahan",
    "ARTVIN" to "Artvin",
    "AYDIN" to "Aydın",
    "BALIKESIR" to "Balıkesir",
    "BARTIN" to "Bartın",
    "BATMAN" to "Batman",
    "BAYBURT" to "Bayburt",
    "BILECIK" to "Bilecik",
    "BINGOL" to "Bingöl",
    "BITLIS" to "Bitlis",
    "BOLU" to "Bolu",
    "BURDUR" to "Burdur",
    "BURSA" to "Bursa",
    "CANAKKALE" to "Çanakkale",
    "CANKIRI" to "Çankırı",
    "CORUM" to "Çorum",
    "DENIZLI" to "Denizli",
    "DIYARBAKIR" to "Diyarbakır",
    "DUZCE" to "Düzce",
    "EDIRNE" to "Edirne",
    "ELAZIG" to "Elazığ",
    "ERZINCAN" to "Erzincan",
    "ERZURUM" to "Erzurum",
    "ESKISEHIR" to "Eskişehir",
    "GAZIANTEP" to "Gaziantep",
    "GIRESUN" to "Giresun",
    "GUMUSHANE" to "Gümüşhane",
    "HAKKARI" to "Hakkari",
    "HATAY" to "Hatay",
    "IGDIR" to "Iğdır",
    "ISPARTA" to "Isparta",
    "ISTANBUL" to "İstanbul",
    "IZMIR" to "İzmir",
    "KAHRAMANMARAS" to "Kahramanmaraş",
    "KARABUK" to "Karabük",
    "KARAMAN" to "Karaman",
    "KARS" to "Kars",
    "KASTAMONU" to "Kastamonu",
    "KAYSERI" to "Kayseri",
    "KILIS" to "Kilis",
    "KIRIKKALE" to "Kırıkkale",
    "KIRKLARELI" to "Kırklareli",
    "KIRSEHIR" to "Kırşehir",
    "KOCAELI" to "Kocaeli",
    "KONYA" to "Konya",
    "KUTAHYA" to "Kütahya",
    "MALATYA" to "Malatya",
    "MANISA" to "Manisa",
    "MARDIN" to "Mardin",
    "MERSIN" to "Mersin",
    "MUGLA" to "Muğla",
    "MUS" to "Muş",
    "NEVSEHIR" to "Nevşehir",
    "NIGDE" to "Niğde",
    "ORDU" to "Ordu",
    "OSMANIYE" to "Osmaniye",
    "RIZE" to "Rize",
    "SAKARYA" to "Sakarya",
    "SAMSUN" to "Samsun",
    "SANLIURFA" to "Şanlıurfa",
    "SIIRT" to "Siirt",
    "SINOP" to "Sinop",
    "SIRNAK" to "Şırnak",
    "SIVAS" to "Sivas",
    "TEKIRDAG" to "Tekirdağ",
    "TOKAT" to "Tokat",
    "TRABZON" to "Trabzon",
    "TUNCELI" to "Tunceli",
    "USAK" to "Uşak",
    "VAN" to "Van",
    "YALOVA" to "Yalova",
    "YOZGAT" to "Yozgat",
    "ZONGULDAK" to "Zonguldak",
    "IZMIT" to "İzmit",
    "GEBZE" to "Gebze",
    "DILOVASI" to "Dilovası",
    "SEKERPINAR" to "Şekerpınar",
    "DARICA" to "Darıca",
    "CAYIROVA" to "Çayırova",
    "DERINCE" to "Derince",
    "KORFEZ" to "Körfez",
    "KARTEPE" to "Kartepe",
    "BASISKELE" to "Başiskele",
    "GOLCUK" to "Gölcük",
    "KANDIRA" to "Kandıra",
    "KARAMURSEL" to "Karamürsel",
    "TUZLA" to "Tuzla",
    "PENDIK" to "Pendik",
    "KARTAL" to "Kartal",
    "MALTEPE" to "Maltepe",
    "ATASEHIR" to "Ataşehir",
    "UMRANIYE" to "Ümraniye",
    "USKUDAR" to "Üsküdar",
    "SULTANBEYLI" to "Sultanbeyli",
    "SANCAKTEPE" to "Sancaktepe",
    "CEKMEKOY" to "Çekmeköy",
    "BAYRAMPASA" to "Bayrampaşa",
    "FERHATPASA" to "Ferhatpaşa",
    "ESENYURT" to "Esenyurt",
    "ESENLER" to "Esenler",
    "BAGCILAR" to "Bağcılar",
    "BASAKSEHIR" to "Başakşehir",
    "BASAKSEH R" to "Başakşehir",
    "AVCILAR" to "Avcılar",
    "BEYLIKDUZU" to "Beylikdüzü",
    "SILIVRI" to "Silivri",
    "CATALCA" to "Çatalca",
    "ADAPAZARI" to "Adapazarı",
    "AKCAKOCA" to "Akçakoca",
    "AKSEHIR" to "Akşehir",
    "ALASEHIR" to "Alaşehir",
    "ALIAGA" to "Aliağa",
    "ALTINDAG" to "Altındağ",
    "ALTINOZU" to "Altınözü",
    "ARNAVUTKOY" to "Arnavutköy",
    "AYANCIK" to "Ayancık",
    "BAGCILAR" to "Bağcılar",
    "BAHCELIEVLER" to "Bahçelievler",
    "BANDIRMA" to "Bandırma",
    "BAYRAMIC" to "Bayramiç",
    "BESIKTAS" to "Beşiktaş",
    "BEYTUSSEBAP" to "Beytüşşebap",
    "BUYUKCEKMECE" to "Büyükçekmece",
    "CAN" to "Çan",
    "CARSAMBA" to "Çarşamba",
    "CATALPINAR" to "Çatalpınar",
    "CAYYOLU" to "Çayyolu",
    "CERKEZKOY" to "Çerkezköy",
    "CESME" to "Çeşme",
    "CEVRE" to "Çevre",
    "CIGLI" to "Çiğli",
    "CORLU" to "Çorlu",
    "CUBUK" to "Çubuk",
    "CUMRA" to "Çumra",
    "DOGANSEHIR" to "Doğanşehir",
    "DORTYOL" to "Dörtyol",
    "DOSEMEALTI" to "Döşemealtı",
    "DUZICI" to "Düziçi",
    "ERCIS" to "Erciş",
    "EREGLI" to "Ereğli",
    "FATIH" to "Fatih",
    "FERIZLI" to "Ferizli",
    "GAZIEMIR" to "Gaziemir",
    "GAZIOSMANPASA" to "Gaziosmanpaşa",
    "GAZIPASA" to "Gazipaşa",
    "GOLBASI" to "Gölbaşı",
    "GONEN" to "Gönen",
    "GOZTEPE" to "Göztepe",
    "GUCLUKONAK" to "Güçlükonak",
    "GUMUSOVA" to "Gümüşova",
    "GUNGOREN" to "Güngören",
    "HADIMKOY" to "Hadımköy",
    "IKITELLI" to "İkitelli",
    "ILGIN" to "Ilgın",
    "INCESU" to "İncesu",
    "INEBOLU" to "İnebolu",
    "ISIKKENT" to "Işıkkent",
    "ISKENDERUN" to "İskenderun",
    "KADIKOY" to "Kadıköy",
    "KAGITHANE" to "Kağıthane",
    "KARABAGLAR" to "Karabağlar",
    "KARSIYAKA" to "Karşıyaka",
    "KAS" to "Kaş",
    "KAYNARCA" to "Kaynarca",
    "KEMALPASA" to "Kemalpaşa",
    "KESAN" to "Keşan",
    "KIRAC" to "Kıraç",
    "KIRIKHAN" to "Kırıkhan",
    "KOCAALI" to "Kocaali",
    "KOYCEGIZ" to "Köyceğiz",
    "KURSUNLU" to "Kurşunlu",
    "MACKA" to "Maçka",
    "MELIKGAZI" to "Melikgazi",
    "NILUFER" to "Nilüfer",
    "ODEMIS" to "Ödemiş",
    "OMERLI" to "Ömerli",
    "OSMANCIK" to "Osmancık",
    "PASINLER" to "Pasinler",
    "PURSAKLAR" to "Pursaklar",
    "SAMANDAG" to "Samandağ",
    "SARAYONU" to "Sarayönü",
    "SARIGERME" to "Sarıgerme",
    "SARIKAYA" to "Sarıkaya",
    "SEHITKAMIL" to "Şehitkamil",
    "SELCUKLU" to "Selçuklu",
    "SEMDINLI" to "Şemdinli",
    "SOGUT" to "Söğüt",
    "SOKE" to "Söke",
    "SULEYMANPASA" to "Süleymanpaşa",
    "SURUC" to "Suruç",
    "TASDELEN" to "Taşdelen",
    "TAVSANLI" to "Tavşanlı",
    "TEKKEKOY" to "Tekkeköy",
    "TORBALI" to "Torbalı",
    "UNYE" to "Ünye",
    "VELIMESE" to "Velimeşe",
    "VIRANSEHIR" to "Viranşehir",
    "YENIBOSNA" to "Yenibosna",
    "YENIMAHALLE" to "Yenimahalle",
    "YERKOY" to "Yerköy",
    "YILDIRIM" to "Yıldırım",
    "YUKSEKOVA" to "Yüksekova",
    "ZEYTINBURNU" to "Zeytinburnu",
    "AKDENIZ" to "Akdeniz",
    "AKH SAR" to "Akhisar",
    "AKHISAR" to "Akhisar",
    "AKYAZI" to "Akyazı",
    "AKYURT" to "Akyurt",
    "AMASRA" to "Amasra",
    "ARAPGIR" to "Arapgir",
    "ARIFIYE" to "Arifiye",
    "ATAKUM" to "Atakum",
    "AVRUPA" to "Avrupa",
    "AZIZIYE" to "Aziziye",
    "BERGAMA" to "Bergama",
    "BEYKOZ" to "Beykoz",
    "BODRUM" to "Bodrum",
    "BOR" to "Bor",
    "BORNOVA" to "Bornova",
    "BOYABAT" to "Boyabat",
    "BUCA" to "Buca",
    "CANLI" to "Canlı",
    "CEYLANPINAR" to "Ceylanpınar",
    "DERECIK" to "Derecik",
    "DIDIM" to "Didim",
    "DUDULLU" to "Dudullu",
    "DURSUNBEY" to "Dursunbey",
    "EDREMIT" to "Edremit",
    "EFLANI" to "Eflani",
    "ERDEMLI" to "Erdemli",
    "EZINE" to "Ezine",
    "FETHIYE" to "Fethiye",
    "FINIKE" to "Finike",
    "GELIBOLU" to "Gelibolu",
    "GEMLIK" to "Gemlik",
    "HAVAALANI" to "Havaalanı",
    "HAYRABOLU" to "Hayrabolu",
    "HALKALI" to "Halkalı",
    "HOPA" to "Hopa",
    "KAPI" to "Kapı",
    "KARASU" to "Karasu",
    "KEPSUT" to "Kepsut",
    "KES" to "Kes",
    "KOVA" to "Kova",
    "MANAVGAT" to "Manavgat",
    "MANYAS" to "Manyas",
    "MARMARIS" to "Marmaris",
    "MENDERES" to "Menderes",
    "MIDYAT" to "Midyat",
    "MILAS" to "Milas",
    "MUSTAFAKEMALPASA" to "Mustafakemalpaşa",
    "MUT" to "Mut",
    "NUSAYBIN" to "Nusaybin",
    "NUSAYBUN" to "Nusaybin",
    "ORHANGAZI" to "Orhangazi",
    "ORTACA" to "Ortaca",
    "OSB" to "OSB",
    "OTOGAR" to "Otogar",
    "POZANTI" to "Pozantı",
    "SAH" to "Şah",
    "SALIHLI" to "Salihli",
    "SAPANCA" to "Sapanca",
    "SART" to "Sart",
    "SARUHANLI" to "Saruhanlı",
    "SELENDI" to "Selendi",
    "SERIK" to "Serik",
    "SEYHAN" to "Seyhan",
    "SILIFKE" to "Silifke",
    "SILVAN" to "Silvan",
    "SINCAN" to "Sincan",
    "SPARTA" to "Isparta",
    "SULTANGAZI" to "Sultangazi",
    "SUNGURLU" to "Sungurlu",
    "TALAS" to "Talas",
    "TARSUS" to "Tarsus",
    "TAVAS" to "Tavas",
    "TEPEBASI" to "Tepebaşı",
    "TOROSLAR" to "Toroslar",
    "TURGUTLU" to "Turgutlu",
    "YARIM" to "Yarım",
    "YURT" to "Yurt"
)

fun displayPlace(city: String, district: String?): String {
    val districtText = district?.trim()?.takeIf { it.isNotBlank() }
    return formatPlaceName(districtText ?: city)
}

fun displayRoute(fromCity: String, fromDistrict: String?, toCity: String, toDistrict: String?): String {
    return "${displayPlace(fromCity, fromDistrict)} → ${displayPlace(toCity, toDistrict)}"
}

fun LoadOfferEntity.displayRoute(): String {
    return "${displayPickupStops()} → ${displayDeliveryStops()}"
}

fun LoadOfferEntity.displayPickupStops(): String {
    return pickupStops()
        .mapNotNull { stop ->
            stop.city?.trim()?.takeIf(String::isNotBlank)?.let { city ->
                displayPlace(city, stop.district)
            }
        }
        .distinct()
        .joinToString(" + ")
        .ifBlank { displayPlace(fromCity, fromDistrict) }
}

fun LoadOfferEntity.displayDeliveryStops(): String {
    return deliveryStops()
        .mapNotNull { stop ->
            stop.city?.trim()?.takeIf(String::isNotBlank)?.let { city ->
                displayPlace(city, stop.district)
            }
        }
        .distinct()
        .joinToString(" + ")
        .ifBlank { displayPlace(toCity, toDistrict) }
}

fun LoadMarker.displayRoute(): String {
    return displayRoute(fromCity, fromDistrict, toCity, toDistrict)
}

fun formatPlaceName(raw: String): String {
    val cleaned = raw
        .replace(Regex("[\\u200E\\u200F\\u202A-\\u202E\\u2066-\\u2069]"), "")
        .replace(Regex("[\\u00A0\\u202F]"), " ")
        .replace(Regex("\\s+"), " ")
        .trim(' ', ',', ';', ':', '-', '~')
    if (cleaned.isBlank()) return cleaned

    val normalized = normalizePlaceKey(cleaned)
    PlaceNames[normalized]?.let { return it }

    return cleaned
        .lowercase(TurkishLocale)
        .split(" ")
        .joinToString(" ") { word ->
            word.replaceFirstChar { char ->
                if (char.isLowerCase()) char.titlecase(TurkishLocale) else char.toString()
            }
        }
}

private val VehicleTokenNames = mapOf(
    "TIR" to "Tır",
    "KAMYON" to "Kamyon",
    "KAMYONET" to "Kamyonet",
    "CEKICI" to "Çekici",
    "LOWBED" to "Lowbed",
    "TENTELI" to "Tenteli",
    "TENTE" to "Tente",
    "FRIGO" to "Frigo",
    "ACIK" to "Açık",
    "KAPALI" to "Kapalı",
    "KASA" to "Kasa",
    "PLATFORM" to "Platform",
    "KONTEYNER" to "Konteyner",
    "DAMPERLI" to "Damperli",
    "DAMPER" to "Damper",
    "DORSE" to "Dorse",
    "SILOBAS" to "Silobas",
    "TANKER" to "Tanker",
    "SERBEST" to "Serbest",
    "MEGA" to "Mega",
    "JUMBO" to "Jumbo",
    "PILOT" to "Pilot"
)

fun formatVehicleType(raw: String?): String {
    val cleaned = raw?.trim().orEmpty()
    if (cleaned.isBlank()) return ""
    return cleaned
        .split(Regex("\\s+"))
        .joinToString(" ") { token -> formatVehicleToken(token) }
}

/**
 * Ekranda gösterilecek araç yazısı. Önce parser'ın ham alanı (vehicleType);
 * o boşsa backend vehicleDetector'ın kanonik etiketi ("tır,kapalı kasa" →
 * "Tır, Kapalı Kasa"). İkisi de boşsa null — çağıran "Araç belirtilmemiş"
 * göstermeye devam eder. 30k ilanlık cihaz taramasında 910 ilan sırf bu
 * fallback olmadığı için "belirtilmemiş" görünüyordu.
 */
fun vehicleDisplayText(vehicleType: String?, vehicleCanonical: String?): String? {
    vehicleType?.trim()?.takeIf { it.isNotBlank() }?.let { raw ->
        formatVehicleType(raw).takeIf { it.isNotBlank() }?.let { return it }
    }
    return vehicleCanonical
        ?.split(",")
        ?.map { it.trim() }
        ?.filter { it.isNotBlank() }
        ?.takeIf { it.isNotEmpty() }
        ?.joinToString(", ") { formatVehicleType(it) }
}

/**
 * Araç TAHMİNİ gösterimi. "kamyonet,kamyon" -> "Tahmini araç: Kamyonet / Kamyon".
 * Kesinlik iddia etmeyen kalıp bilinçli: tahmin, tespit değildir.
 */
fun inferredVehicleDisplayText(candidates: String?): String? {
    val parts = candidates
        ?.split(",")
        ?.map { it.trim() }
        ?.filter { it.isNotBlank() }
        ?.takeIf { it.isNotEmpty() }
        ?: return null
    return "Tahmini araç: " + parts.joinToString(" / ") { formatVehicleType(it) }
}

private fun formatVehicleToken(token: String): String {
    if (token.isBlank()) return token
    // 13.60, 1360, sayı içeren tokenlar olduğu gibi kalır.
    if (token.any { it.isDigit() }) return token
    val key = token.uppercase(TurkishLocale)
        .replace("İ", "I")
        .replace("Ş", "S")
        .replace("Ğ", "G")
        .replace("Ü", "U")
        .replace("Ö", "O")
        .replace("Ç", "C")
    VehicleTokenNames[key]?.let { return it }
    // Bilinmeyen token: title case (örn. "OZEL" -> "Özel" değil ama "Ozel")
    return token.lowercase(TurkishLocale).replaceFirstChar { ch ->
        if (ch.isLowerCase()) ch.titlecase(TurkishLocale) else ch.toString()
    }
}

private fun normalizePlaceKey(value: String): String {
    return value.uppercase(TurkishLocale)
        .replace("İ", "I")
        .replace("Ş", "S")
        .replace("Ğ", "G")
        .replace("Ü", "U")
        .replace("Ö", "O")
        .replace("Ç", "C")
        .replace(Regex("[^A-Z0-9 ]+"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()
}
