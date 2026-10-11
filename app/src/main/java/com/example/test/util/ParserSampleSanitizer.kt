package com.example.test.util

import java.util.Locale

/**
 * Kalıcı parser örneği için gizlilik sınırı — PiiSanitizer'dan ayrı ve daha
 * sıkı. PiiSanitizer değerleri maskeler ki canlı parse cihazda geri
 * yerleştirebilsin; bu sanitizer ise SAKLANACAK örnekte kimlik verisinin hiç
 * bulunmamasını sağlar. Ağ isteğinden önce cihazda çalışır; backend aynı
 * kuralları ikinci kez uygular ve şüpheli örneği maskelemeye çalışmadan
 * doğrudan reddeder.
 *
 * Korunan: rota, araç/dorse, yük, tonaj, ölçü, fiyat satırları ve bu
 * satırların birebir düzeni/noktalaması.
 * Atılan/reddedilen: telefon ve [TELEFON]/[ISIM] placeholder'ları, firma
 * başlığı satırları, e-posta, URL/sosyal medya, IBAN, plaka, iletişim/footer/
 * selamlama/saat satırları, sokak seviyesindeki adres ayrıntıları.
 *
 * Kurallar backend/parserSamples/sampleSanitizer.js ile sürüm-paralel
 * tutulmalıdır (SANITIZER_VERSION).
 */
object ParserSampleSanitizer {

    const val SANITIZER_VERSION = 3
    const val MAX_TEXT_CHARS = 4000

    data class Result(
        val ok: Boolean,
        val text: String?,
        val reason: String?
    )

    private val placeholderRegex = Regex(
        """\[(?:TELEFON|ISIM|İSİM)(?:_\d+)?\]|\bTELEFON_\d+\b|\bISIM_\d+\b""",
        RegexOption.IGNORE_CASE
    )
    private val phoneRegex = Regex(
        """(?<![\d])(?:\+?9?0[\s\-.()]*)?[2-5]\d{2}[\s\-.()]*\d{3}[\s\-.()]*\d{2}[\s\-.()]*\d{2}(?![\d])"""
    )
    private val emailRegex = Regex("""[^\s@]+@[^\s@]+\.[^\s@]{2,}""")
    private val urlOrSocialRegex = Regex(
        """(https?://|www\.|wa\.me/|t\.me/|chat\.whatsapp|instagram\.com|facebook\.com|tiktok\.com|(?:^|\s)@[a-z0-9_.]{3,})""",
        RegexOption.IGNORE_CASE
    )
    private val ibanRegex = Regex(
        """\bTR\s?\d{2}(?:\s?\d{4}){5}\s?\d{2}\b|\bTR\d{24}\b""",
        RegexOption.IGNORE_CASE
    )

    // Plakaya benzeyen ama yük/birim kelimesi olan harf grupları.
    private val plateUnitWords = setOf(
        "TON", "TN", "T", "KG", "KILO", "ADET", "ADT", "PLT", "PALET",
        "MT", "M", "CM", "MM", "LT", "TL", "USD", "EUR", "EU", "KM", "X"
    )
    private val plateCandidateRegex = Regex(
        """(?<![A-Za-zÇĞİÖŞÜçğıöşü0-9])(0[1-9]|[1-7]\d|8[01])[\s.-]*([A-ZÇĞİÖŞÜ]{1,3})[\s.-]*(\d{2,4})(?![A-Za-z0-9])""",
        RegexOption.IGNORE_CASE
    )

    // Aşağıdakiler Türkçe katlanmış (küçük harf + aksansız) satıra uygulanır.
    private val contactLineRegex = Regex(
        """^\s*(?:irtibat|iletisim|ilt\.|tel[\s.:]|gsm|whatsapp|watsap|wp[\s.:]|numara|bilgi\s*icin|ara(?:yiniz)?\s*:)"""
    )
    private val companyLineRegex = Regex(
        """(nakliyat|lojistik|tasimacilik|tasima(?:cilik)?|transport|shipping|kargo|ambar|kurye|uluslararasi\s+tas|\bltd\b|\ba\.\s?s\.|\bsti\b|\bticaret\b|\btic\.\s|\bsanayi\b|\bsan\.\s|grup\s+nakl)"""
    )
    private val footerLineRegex = Regex(
        """(whatsapp\s+aramalar|aramalar\w*\s+kapal|sadece\s+mesaj|mesaj\s+yaz|cagri\w*\s+kapal|arama\s+yapmayin)"""
    )
    private val greetingLineRegex = Regex(
        """^\s*(?:gunaydin|merhaba|selam|hayirli|iyi\s+(?:calismalar|gunler|aksamlar))\b"""
    )
    private val clockPrefixLineRegex = Regex("""^\s*saat\b""")

    // Dakika 00-59 olmalı; 13.60 dorse ölçüsü asla saat sayılmaz.
    private val clockOnlyLineRegex = Regex("""^\s*\[?(?:[01]?\d|2[0-3])[:.][0-5]\d]?\s*$""")

    // Kalıcı örneğe yalnız pozitif lojistik sinyali taşıyan satırlar alınır.
    // Böylece "Erdem", "Ahmet Yılmaz", "MARMARA TUR" ve "ACME" gibi tek
    // başına kimlik/şirket olabilecek başlıklar deny-list eşleşmese de düşer.
    private val logisticsSignalRegex = Regex(
        """\b(?:ton|tn|kg|kilo|gr|gram|adet|adt|palet|plt|koli|kutu|paket|cuval|varil|rulo|balya|sandik|parca|m3|desi|lt|litre|tir|kamyon|kamyonet|panelvan|pickup|cekici|dorse|tenteli|kapali|acik|frigo|lowbed|damper|kirkayak|scooter|motosiklet|motor|makine|mobilya|tekstil|gida|sebze|meyve|metal|demir|celik|plastik|pecete|yuk|yukleme|bosaltma|alinacak|teslim|fiyat|ucret|navlun)\b"""
    )
    private val currencyOrMeasureRegex = Regex(
        """(?:\d+(?:[.,]\d+)?\s*(?:₺|tl|try|usd|eur|€|\$)(?=\s|$))|(?:\b\d{1,4}\s*[x×]\s*\d{1,4}(?:\s*[x×]\s*\d{1,4})?\b)""",
        RegexOption.IGNORE_CASE
    )
    private val standaloneDecimalMeasureRegex = Regex(
        """^\s*\d{1,2}[.,]\d{1,2}\s*(?:m|mt)?\s*$""",
        RegexOption.IGNORE_CASE
    )
    private val routeSeparatorRegex = Regex("""(?:->|=>|→|⟶|–|—|\s-\s)""")
    private val provinceWordRegex = Regex(
        """\b(?:adana|adiyaman|afyon(?:karahisar)?|agri|amasya|ankara|antalya|artvin|aydin|balikesir|bilecik|bingol|bitlis|bolu|burdur|bursa|canakkale|cankiri|corum|denizli|diyarbakir|edirne|elazig|erzincan|erzurum|eskisehir|gaziantep|giresun|gumushane|hakkari|hatay|isparta|mersin|istanbul|izmir|kars|kastamonu|kayseri|kirklareli|kirsehir|kocaeli|konya|kutahya|malatya|manisa|kahramanmaras|mardin|mugla|mus|nevsehir|nigde|ordu|rize|sakarya|samsun|siirt|sinop|sivas|tekirdag|tokat|trabzon|tunceli|sanliurfa|usak|van|yozgat|zonguldak|aksaray|bayburt|karaman|kirikkale|batman|sirnak|bartin|ardahan|igdir|yalova|karabuk|kilis|osmaniye|duzce)\b"""
    )
    private val safeRouteLocationWordRegex = Regex(
        """\b(?:bakirkoy|maslak|hadimkoy|bostanci|pasakoy|cerkezkoy|maltepe|soke|pendik|tepeoren|arsin|agva|inegol|osmangazi|tuzla|gebze|ikitelli|esenyurt|cekmekoy|samandira|kartal|gerede|bozuyuk|iznik|taskesti|mudurnu|goynuk|baki|baku|astana|almati|almaty|biskek|bishkek|moskova|poti|tiflis|tbilisi|rustavi|erbil|podolsk)\b"""
    )
    private val safeRoutePairSeparatorRegex = Regex("""(?:->|=>|→|⟶|–|—|>|/|\s*-\s*)""")

    // Sokak seviyesindeki adres parçaları satırdan çıkarılır; il/ilçe kalır.
    private val addressDetailRegex = Regex(
        """(\S+\s+)?(?:mah\.|mahalles[iıİI]|mahalle)\s*\S*|(\S+\s+)?(?:sok\.|sokak|sk\.)\s*\S*|(?:cad\.|caddes[iıİI]|cadde|blv\.|bulvar[ıiİI]?)\s*\S*|(?<![a-zçğıöşüA-ZÇĞİÖŞÜ])no\s*[:.]\s*\d+[a-z/\-]*|(?<![a-zçğıöşüA-ZÇĞİÖŞÜ])kat\s*[:.]\s*\d+|(?<![a-zçğıöşüA-ZÇĞİÖŞÜ])daire\s*[:.]\s*\d+|apt\.\s*\S*|(?<![a-zçğıöşüA-ZÇĞİÖŞÜ])blok\s*:\s*\S*""",
        RegexOption.IGNORE_CASE
    )

    private val longSeparatedDigitsRegex = Regex("""\d[\d\s.\-()]{8,}\d""")
    private val longDigitRunRegex = Regex("""(?<!\d)\d{9,}(?!\d)""")
    private val combiningMarksRegex = Regex("[\u0300-\u036F]")

    fun sanitize(input: String?): Result {
        if (input.isNullOrBlank()) return Result(ok = false, text = null, reason = "empty")
        if (input.length > MAX_TEXT_CHARS * 2) return Result(ok = false, text = null, reason = "too_long")

        val keptLines = input
            .replace("\r\n", "\n")
            .replace("\r", "\n")
            .split("\n")
            .filterNot(::shouldDropLine)
            .map(::reduceAddressDetail)
            .filter(::isLikelyLogisticsLine)

        // Atılan satırların bıraktığı boş satır dizilerini teke indir; blok
        // düzeni parser için olduğu gibi kalsın.
        val collapsed = mutableListOf<String>()
        for (line in keptLines) {
            val isBlank = line.isBlank()
            if (isBlank && (collapsed.isEmpty() || collapsed.last().isBlank())) continue
            collapsed.add(if (isBlank) "" else line)
        }
        while (collapsed.isNotEmpty() && collapsed.last().isBlank()) {
            collapsed.removeAt(collapsed.lastIndex)
        }

        val text = collapsed.joinToString("\n")
        val suspicion = findSuspicion(text)
        if (suspicion != null) return Result(ok = false, text = null, reason = suspicion)
        return Result(ok = true, text = text, reason = null)
    }

    /**
     * Sanitize edilmiş metin üzerinde son fail-closed kontrol. Null dönerse
     * temizdir; aksi halde örnek gönderilmemelidir.
     */
    fun findSuspicion(text: String?): String? {
        if (text == null) return "not_text"
        if (text.isBlank()) return "empty"
        if (text.length > MAX_TEXT_CHARS) return "too_long"
        if (placeholderRegex.containsMatchIn(text)) return "placeholder"
        if (phoneRegex.containsMatchIn(text)) return "phone"
        if (emailRegex.containsMatchIn(text)) return "email"
        if (urlOrSocialRegex.containsMatchIn(text)) return "url"
        if (ibanRegex.containsMatchIn(text)) return "iban"
        if (longSeparatedDigitsRegex.containsMatchIn(text) &&
            longDigitRunRegex.containsMatchIn(text.replace(Regex("""[\s.\-()]"""), ""))
        ) {
            return "long_digit_run"
        }
        for (line in text.split("\n")) {
            val folded = foldTurkish(line)
            if (containsPlate(line)) return "plate"
            if (companyLineRegex.containsMatchIn(folded)) return "company"
            if (contactLineRegex.containsMatchIn(folded)) return "contact"
            if (footerLineRegex.containsMatchIn(folded)) return "footer"
            if (!isLikelyLogisticsLine(line)) return "non_logistics_line"
        }
        return null
    }

    // Kotlin regex'i de Türkçe İ/ı büyük-küçük dönüşümünü katlamaz; anahtar
    // kelime kuralları tr küçük harfe indirilip aksanları atılmış kopyada
    // çalışır ("NAKLİYAT" → "nakliyat"). Düzen kararı orijinal satırla verilir.
    private fun foldTurkish(value: String): String {
        val lowered = value.lowercase(Locale("tr", "TR"))
        val decomposed = java.text.Normalizer.normalize(lowered, java.text.Normalizer.Form.NFD)
        return combiningMarksRegex.replace(decomposed, "").replace('\u0131', 'i')
    }

    private fun containsPlate(line: String): Boolean {
        return plateCandidateRegex.findAll(line).any { match ->
            val letters = foldTurkish(match.groupValues[2]).uppercase(Locale.ROOT)
            letters !in plateUnitWords
        }
    }

    private fun shouldDropLine(line: String): Boolean {
        val trimmed = line.trim()
        if (trimmed.isEmpty()) return false
        val folded = foldTurkish(trimmed)
        return placeholderRegex.containsMatchIn(trimmed) ||
            phoneRegex.containsMatchIn(trimmed) ||
            emailRegex.containsMatchIn(trimmed) ||
            urlOrSocialRegex.containsMatchIn(trimmed) ||
            ibanRegex.containsMatchIn(trimmed) ||
            containsPlate(trimmed) ||
            contactLineRegex.containsMatchIn(folded) ||
            companyLineRegex.containsMatchIn(folded) ||
            footerLineRegex.containsMatchIn(folded) ||
            greetingLineRegex.containsMatchIn(folded) ||
            clockOnlyLineRegex.matches(trimmed) ||
            clockPrefixLineRegex.containsMatchIn(folded)
    }

    private fun isLikelyLogisticsLine(line: String): Boolean {
        val trimmed = line.trim()
        if (trimmed.isEmpty()) return true
        val folded = foldTurkish(trimmed)
        if (logisticsSignalRegex.containsMatchIn(folded) ||
            currencyOrMeasureRegex.containsMatchIn(trimmed) ||
            standaloneDecimalMeasureRegex.matches(trimmed)
        ) {
            return true
        }
        if (provinceWordRegex.containsMatchIn(folded) &&
            routeSeparatorRegex.containsMatchIn(trimmed)
        ) {
            return true
        }
        val safeLocationCount = safeRouteLocationWordRegex
            .findAll(folded)
            .map { it.value }
            .toSet()
            .size
        if (safeLocationCount >= 2 && safeRoutePairSeparatorRegex.containsMatchIn(trimmed)) {
            return true
        }
        // Bazı kaynaklarda il/ilçe blokları ayrı satırdır.
        return provinceWordRegex.containsMatchIn(folded) &&
            folded.split(Regex("""\s+""")).size <= 3
    }

    private fun reduceAddressDetail(line: String): String {
        val reduced = addressDetailRegex.replace(line, " ")
        if (reduced == line) return line
        return reduced.replace(Regex("""[ \t]{2,}"""), " ").trimEnd()
    }
}
