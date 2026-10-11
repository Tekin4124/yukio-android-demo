package com.example.test.notification

import com.example.test.data.local.LoadOfferEntity
import com.example.test.data.model.RouteStop
import com.example.test.data.model.RouteStopRole
import com.example.test.data.model.normalizedRole
import com.example.test.data.remote.Ilan
import java.util.Locale

object LoadOfferMapper {
    fun fromIlanOrNull(
        ilan: Ilan,
        rawMessage: String,
        sourceGroup: String,
        fallbackSender: String,
        createdAt: Long = System.currentTimeMillis(),
        parserSource: String? = null,
        sampleId: String? = null,
        offerIndex: Int? = null
    ): LoadOfferEntity? {
        val routeStops = normalizeRouteStops(ilan)
        val firstPickup = routeStops.firstOrNull {
            it.normalizedRole() == RouteStopRole.PICKUP
        }
        val firstDelivery = routeStops.firstOrNull {
            it.normalizedRole() == RouteStopRole.DELIVERY
        }
        val fromCity = firstPickup?.city ?: normalizeCity(ilan.gondericiIl) ?: return null
        val toCity = firstDelivery?.city ?: normalizeCity(ilan.aliciIl) ?: return null
        return fromIlan(
            ilan = ilan,
            rawMessage = rawMessage,
            sourceGroup = sourceGroup,
            fallbackSender = fallbackSender,
            createdAt = createdAt,
            parserSource = parserSource,
            normalizedFromCity = fromCity,
            normalizedToCity = toCity,
            normalizedFromDistrict = firstPickup?.district ?: ilan.gondericiIlce?.trim(),
            normalizedToDistrict = firstDelivery?.district ?: ilan.aliciIlce?.trim(),
            normalizedRouteStops = routeStops,
            sampleId = sampleId,
            offerIndex = offerIndex
        )
    }

    fun fromIlan(
        ilan: Ilan,
        rawMessage: String,
        sourceGroup: String,
        fallbackSender: String,
        createdAt: Long = System.currentTimeMillis(),
        parserSource: String? = null,
        sampleId: String? = null,
        offerIndex: Int? = null
    ): LoadOfferEntity {
        val routeStops = normalizeRouteStops(ilan)
        val firstPickup = routeStops.firstOrNull {
            it.normalizedRole() == RouteStopRole.PICKUP
        }
        val firstDelivery = routeStops.firstOrNull {
            it.normalizedRole() == RouteStopRole.DELIVERY
        }
        val fromCity = firstPickup?.city ?: normalizeCity(ilan.gondericiIl).orEmpty()
        val toCity = firstDelivery?.city ?: normalizeCity(ilan.aliciIl).orEmpty()
        return fromIlan(
            ilan = ilan,
            rawMessage = rawMessage,
            sourceGroup = sourceGroup,
            fallbackSender = fallbackSender,
            createdAt = createdAt,
            parserSource = parserSource,
            normalizedFromCity = fromCity,
            normalizedToCity = toCity,
            normalizedFromDistrict = firstPickup?.district ?: ilan.gondericiIlce?.trim(),
            normalizedToDistrict = firstDelivery?.district ?: ilan.aliciIlce?.trim(),
            normalizedRouteStops = routeStops,
            sampleId = sampleId,
            offerIndex = offerIndex
        )
    }

    private fun fromIlan(
        ilan: Ilan,
        rawMessage: String,
        sourceGroup: String,
        fallbackSender: String,
        createdAt: Long,
        parserSource: String?,
        normalizedFromCity: String,
        normalizedToCity: String,
        normalizedFromDistrict: String?,
        normalizedToDistrict: String?,
        normalizedRouteStops: List<RouteStop>,
        sampleId: String?,
        offerIndex: Int?
    ): LoadOfferEntity {
        val rawTonaj = firstNonBlank(ilan.tonaj, ilan.agirlik)
        val rawVehicle = ilan.aracTipi?.trim()
        val rawDorse = ilan.dorseTipi?.trim()
        val quantityText = buildQuantityText(ilan.miktar, ilan.miktarBirimi)
        val tonnageInfo = parseTonnage(rawTonaj, rawMessage)

        val is13_60Tonaj = rawTonaj?.replace(" ", "") in listOf("13.60", "1360")
        val axleMatch = rawTonaj?.contains("teker", ignoreCase = true) == true
        val numberIsAxle = rawTonaj?.let { Regex("(\\d+)").find(it)?.value?.toIntOrNull() }
            ?.let { it in 4..30 && axleMatch } == true

        val extraDorseNotes = buildList {
            if (is13_60Tonaj) add("13.60")
            if (numberIsAxle) add(rawTonaj.orEmpty())
        }.joinToString(" ").takeIf { it.isNotBlank() }

        val vehicleTypeStr = listOfNotNull(
            rawVehicle?.takeIf { it.isNotBlank() },
            rawDorse?.takeIf { it.isNotBlank() },
            extraDorseNotes
        ).joinToString(" ").trim().takeIf { it.isNotBlank() }

        // Backend'in kanonik etiketleri (varsa). Filtre eşleştirmesi için
        // ayrı bir alanda saklarız; UI gösterimi vehicleType'tan gelir.
        val canonicalStr = listOfNotNull(
            ilan.aracTipiKanonik?.trim()?.takeIf { it.isNotBlank() },
            ilan.dorseTipiKanonik?.trim()?.takeIf { it.isNotBlank() }
        ).joinToString(",").takeIf { it.isNotBlank() }

        // Araç tahmini adayları: yalnız backend vehicle_inferred=true dediyse
        // VE cihazda da açık araç bilgisi görünmüyorsa saklanır. Kanonik alana
        // asla yazılmaz — "Bana göre" eşleştirmesi tahmini gerçek araç sanmasın.
        val hasExplicitVehicle = !rawVehicle.isNullOrBlank() ||
            !ilan.aracTipiKanonik?.trim().isNullOrEmpty()
        val inferredCandidatesStr = ilan.vehicleCandidates
            ?.takeIf { ilan.vehicleInferred == true && !hasExplicitVehicle }
            ?.mapNotNull { candidate -> candidate.trim().takeIf { it.isNotBlank() } }
            ?.takeIf { it.isNotEmpty() }
            ?.joinToString(",")

        // Price affects sorting, matching and the user's commercial decision.
        // Keep a parser price only when the same amount has explicit price
        // evidence in the device-side sanitized source message.
        val fiyatInt = verifiedPriceTry(ilan.fiyat, rawMessage)

        val notesStr = buildList {
            ilan.yukCinsi?.trim()?.takeIf { it.isNotBlank() }?.let { add("Yuk: $it") }
            quantityText?.let { add("Miktar: $it") }
            rawTonaj?.takeIf { tonnageInfo.displayText != null }?.let { add("Agirlik: ${tonnageInfo.displayText}") }
            ilan.yukDetay?.trim()?.takeIf { it.isNotBlank() }?.let { add(it) }
            ilan.yuklemeTarihi?.trim()?.takeIf { it.isNotBlank() }?.let { add("Tarih: $it") }
            ilan.fiyatNotu?.trim()?.takeIf { it.isNotBlank() }?.let { add(it) }
            if (numberIsAxle) add("Not: ${rawTonaj.orEmpty()}")
        }.distinct().joinToString(" | ").takeIf { it.isNotBlank() }

        return LoadOfferEntity(
            fromCity = normalizedFromCity,
            fromDistrict = normalizedFromDistrict,
            toCity = normalizedToCity,
            toDistrict = normalizedToDistrict,
            tonnageTon = tonnageInfo.maxTon,
            tonnageText = tonnageInfo.displayText,
            vehicleType = vehicleTypeStr,
            vehicleCanonical = canonicalStr,
            priceTry = fiyatInt,
            phone = normalizePhoneForDevice(ilan.telefon),
            notes = notesStr,
            rawMessage = rawMessage,
            sourceGroup = sourceGroup,
            sourceSender = WhatsappNotificationPrivacy.HIDDEN_SENDER,
            createdAt = createdAt,
            // Çok ilanlı mesajda bütün ilanlar aynı sampleId'yi, her ilan
            // parser cevabındaki kendi sırasını (offerIndex) taşır.
            sampleId = sampleId?.takeIf { it.isNotBlank() },
            offerIndex = offerIndex.takeIf { sampleId?.isNotBlank() == true },
            vehicleInferredCandidates = inferredCandidatesStr,
            routeStops = normalizedRouteStops.takeIf { it.isNotEmpty() }
        )
    }

    private data class TonnageInfo(
        val maxTon: Int?,
        val displayText: String?
    )

    private fun firstNonBlank(vararg values: String?): String? {
        return values.firstNotNullOfOrNull { value ->
            value?.trim()?.takeIf { it.isNotBlank() }
        }
    }

    private fun buildQuantityText(miktar: String?, miktarBirimi: String?): String? {
        val amount = miktar?.trim()?.takeIf { it.isNotBlank() }
        val unit = miktarBirimi?.trim()?.takeIf { it.isNotBlank() }
        return when {
            amount == null && unit == null -> null
            amount != null && unit != null && amount.contains(unit, ignoreCase = true) -> amount
            amount != null && unit != null -> "$amount $unit"
            else -> amount ?: unit
        }
    }

    private fun verifiedPriceTry(parsedPrice: String?, rawMessage: String): Int? {
        val parsedAmount = parseBackendPrice(parsedPrice) ?: return null
        if (parsedAmount !in MIN_PRICE_TRY..MAX_PRICE_TRY) return null
        return parsedAmount.takeIf { it in explicitPricesFromMessage(rawMessage) }
    }

    private fun parseBackendPrice(value: String?): Int? {
        val text = value
            ?.trim()
            ?.lowercase(Locale("tr", "TR"))
            ?.takeIf { it.isNotBlank() }
            ?: return null
        val token = MONEY_TOKEN.find(text)?.groupValues?.getOrNull(1) ?: return null
        return parseMoneyToken(
            token = token,
            thousandHint = text.contains(Regex("""\bbin\b"""))
        )
    }

    private fun explicitPricesFromMessage(message: String): Set<Int> {
        val out = linkedSetOf<Int>()
        val text = message.lowercase(Locale("tr", "TR"))

        PRICE_WITH_CURRENCY.findAll(text).forEach { match ->
            parseMoneyToken(
                token = match.groupValues[1],
                thousandHint = match.groupValues[2].isNotBlank()
            )?.let(out::add)
        }
        PRICE_WITH_PREFIX.findAll(text).forEach { match ->
            parseMoneyToken(
                token = match.groupValues[1],
                thousandHint = match.groupValues[2].isNotBlank()
            )?.let(out::add)
        }
        PRICE_WITH_KDV.findAll(text).forEach { match ->
            val token = match.groupValues[1]
            val isShortThousands = token.contains(',') || token.contains('.')
            parseMoneyToken(
                token = token,
                thousandHint = match.groupValues[2].isNotBlank() || isShortThousands
            )?.let(out::add)
        }

        return out.filterTo(linkedSetOf()) { it in MIN_PRICE_TRY..MAX_PRICE_TRY }
    }

    private fun parseMoneyToken(token: String, thousandHint: Boolean): Int? {
        val compact = token.replace(" ", "")
        if (compact.isBlank()) return null

        if (thousandHint) {
            val decimal = compact.replace(".", "").replace(',', '.').toDoubleOrNull()
                ?: return null
            return (decimal * 1_000.0).toInt().takeIf { it > 0 }
        }

        val integer = when {
            compact.matches(Regex("""\d{1,3}(?:[.,]\d{3})+""")) ->
                compact.replace(".", "").replace(",", "").toIntOrNull()
            compact.matches(Regex("""\d+""")) -> compact.toIntOrNull()
            else -> null
        }
        return integer?.takeIf { it > 0 }
    }

    private fun parseTonnage(rawTonaj: String?, rawMessage: String): TonnageInfo {
        // Accept a populated parser field only when that exact weight also
        // exists in the sanitized source. If a message contains several
        // weights, never copy the first one onto every offer.
        val sourceEvidence = explicitTonnagesFromMessage(rawMessage)
        parseTonnageText(rawTonaj, requireUnit = true)?.let { parsed ->
            return parsed.takeIf { candidate ->
                sourceEvidence.any { evidence ->
                    evidence.maxTon == candidate.maxTon &&
                        evidence.displayText == candidate.displayText
                }
            } ?: TonnageInfo(maxTon = null, displayText = null)
        }
        sourceEvidence.singleOrNull()?.let { return it }
        return TonnageInfo(maxTon = null, displayText = null)
    }

    private fun explicitTonnagesFromMessage(value: String): List<TonnageInfo> {
        val text = value.trim().uppercase(Locale("tr", "TR"))
        if (text.isBlank()) return emptyList()
        val results = linkedSetOf<TonnageInfo>()
        val occupiedRanges = mutableListOf<IntRange>()

        TONNAGE_RANGE.findAll(text).forEach { match ->
            val first = match.groupValues[1].toIntOrNull() ?: return@forEach
            val second = match.groupValues[2].toIntOrNull() ?: return@forEach
            val minTon = minOf(first, second)
            val maxTon = maxOf(first, second)
            if (maxTon in 1..80) {
                results += TonnageInfo(maxTon = maxTon, displayText = "$minTon-$maxTon ton")
                occupiedRanges += match.range
            }
        }
        TONNAGE_SINGLE.findAll(text).forEach { match ->
            if (occupiedRanges.any { range -> match.range.first in range }) return@forEach
            val ton = match.groupValues[1].toIntOrNull() ?: return@forEach
            if (ton in 1..80) {
                results += TonnageInfo(maxTon = ton, displayText = "$ton ton")
            }
        }
        WEIGHT_KG.findAll(text).forEach { match ->
            val kg = match.groupValues[1].toIntOrNull() ?: return@forEach
            if (kg in 1..80_000) {
                results += TonnageInfo(maxTon = ceilKgToTon(kg), displayText = "$kg kg")
            }
        }
        return results.toList()
    }

    private fun parseTonnageText(value: String?, requireUnit: Boolean): TonnageInfo? {
        val text = value
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?.uppercase(Locale("tr", "TR"))
            ?.replace("Ä°", "I")
            ?.replace("Å", "S")
            ?.replace("Ä", "G")
            ?.replace("Ãœ", "U")
            ?.replace("Ã–", "O")
            ?.replace("Ã‡", "C")
            ?: return null

        val compact = text.replace(Regex("\\s+"), "")
        if (compact == "13.60" || compact == "1360" || Regex("\\b13[.,]?60\\b").containsMatchIn(text)) return null
        if (Regex("\\b(TEKER|DINGIL|DİNGIL|DINGİL)\\b").containsMatchIn(text)) return null

        val rangePattern = if (requireUnit) {
            Regex("""\b(\d{1,2})\s*(?:-|/|ILE|ILA)\s*(\d{1,2})\s*(?:TON|TN|T)\b""")
        } else {
            Regex("""\b(\d{1,2})\s*(?:-|/|ILE|ILA)\s*(\d{1,2})(?:\s*(?:TON|TN|T))?\b""")
        }
        rangePattern.find(text)?.let { match ->
            val first = match.groupValues[1].toIntOrNull() ?: return@let
            val second = match.groupValues[2].toIntOrNull() ?: return@let
            val minTon = minOf(first, second)
            val maxTon = maxOf(first, second)
            if (maxTon in 1..80) {
                return TonnageInfo(maxTon = maxTon, displayText = "$minTon-$maxTon ton")
            }
        }

        val singlePattern = if (requireUnit) {
            Regex("""\b(\d{1,2})\s*(?:TON|TN|T)\b""")
        } else {
            Regex("""\b(\d{1,2})(?:\s*(?:TON|TN|T))?\b""")
        }
        val singleTon = singlePattern.find(text)?.groupValues?.getOrNull(1)?.toIntOrNull()
        singleTon
            ?.takeIf { it in 1..80 }
            ?.let { return TonnageInfo(maxTon = it, displayText = "$it ton") }

        val kgMatch = Regex("""\b(\d{1,5})\s*(?:KG|KILO|KILOGRAM)\b""").find(text)
        val kg = kgMatch?.groupValues?.getOrNull(1)?.toIntOrNull()
        return kg
            ?.takeIf { it in 1..80_000 }
            ?.let { TonnageInfo(maxTon = ceilKgToTon(it), displayText = "$it kg") }
    }

    private fun ceilKgToTon(kg: Int): Int {
        return ((kg + 999) / 1000).coerceAtLeast(1)
    }

    private fun normalizePhoneForDevice(value: String?): String {
        val digits = value?.filter { it.isDigit() }.orEmpty()
        return when {
            digits.length == 12 && digits.startsWith("90") -> "0${digits.drop(2)}"
            digits.length == 11 && digits.startsWith("0") -> digits
            digits.length == 10 && digits.firstOrNull() in '2'..'5' -> "0$digits"
            else -> digits
        }
    }

    private fun normalizeCity(value: String?): String? {
        return value
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?.uppercase(Locale("tr", "TR"))
            ?.replace("İ", "I")
            ?.replace("Ş", "S")
            ?.replace("Ğ", "G")
            ?.replace("Ü", "U")
            ?.replace("Ö", "O")
            ?.replace("Ç", "C")
    }

    private fun normalizeRouteStops(ilan: Ilan): List<RouteStop> {
        val rawStops = ilan.routeStops.orEmpty()
        if (rawStops.isEmpty()) return emptyList()

        val normalized = rawStops
            .withIndex()
            .sortedWith(
                compareBy<IndexedValue<RouteStop>>(
                    { it.value.sequence ?: Int.MAX_VALUE },
                    { it.index }
                )
            )
            .mapNotNull { indexed ->
                val stop = indexed.value
                val role = stop.normalizedRole() ?: return@mapNotNull null
                val city = normalizeCity(stop.city) ?: return@mapNotNull null
                RouteStop(
                    role = when (role) {
                        RouteStopRole.PICKUP -> "ALIS"
                        RouteStopRole.DELIVERY -> "TESLIM"
                    },
                    sequence = stop.sequence ?: indexed.index,
                    city = city,
                    district = stop.district?.trim()?.takeIf(String::isNotBlank)
                )
            }
            .distinctBy { stop ->
                listOf(
                    stop.normalizedRole()?.name,
                    stop.city,
                    stop.district?.uppercase(Locale("tr", "TR"))
                )
            }
            .toMutableList()

        if (normalized.none { it.normalizedRole() == RouteStopRole.PICKUP }) {
            normalizeCity(ilan.gondericiIl)?.let { city ->
                normalized.add(
                    0,
                    RouteStop(
                        role = "ALIS",
                        sequence = -1,
                        city = city,
                        district = ilan.gondericiIlce?.trim()?.takeIf(String::isNotBlank)
                    )
                )
            }
        }
        if (normalized.none { it.normalizedRole() == RouteStopRole.DELIVERY }) {
            normalizeCity(ilan.aliciIl)?.let { city ->
                normalized += RouteStop(
                    role = "TESLIM",
                    sequence = normalized.size,
                    city = city,
                    district = ilan.aliciIlce?.trim()?.takeIf(String::isNotBlank)
                )
            }
        }
        return normalized.mapIndexed { index, stop -> stop.copy(sequence = index) }
    }

    private val MONEY_TOKEN =
        Regex("""(?<!\d)(\d{1,7}(?:[.,]\d{1,3})?)(?!\d)""")
    private val PRICE_WITH_CURRENCY =
        Regex(
            """(?<!\d)(\d{1,7}(?:[.,]\d{1,3})?)\s*(bin\b)?\s*(?:₺|tl\b|try\b|lira\b)""",
            RegexOption.IGNORE_CASE
        )
    private val PRICE_WITH_PREFIX =
        Regex(
            """(?:fiyat|fiat|navlun|ücret|ucret|ücreti|ucreti)\s*[:=]?\s*(\d{1,7}(?:[.,]\d{1,3})?)\s*(bin\b)?""",
            RegexOption.IGNORE_CASE
        )
    private val PRICE_WITH_KDV =
        Regex(
            """(?<!\d)(\d{1,7}(?:[.,]\d{1,2})?)\s*(bin\b)?\s*(?:\+|artı|arti)\s*kdv\b""",
            RegexOption.IGNORE_CASE
        )
    private val TONNAGE_RANGE =
        Regex("""\b(\d{1,2})\s*(?:-|/|ILE|ILA)\s*(\d{1,2})\s*(?:TON|TN|T)\b""")
    private val TONNAGE_SINGLE =
        Regex("""\b(\d{1,2})\s*(?:TON|TN|T)\b""")
    private val WEIGHT_KG =
        Regex("""\b(\d{1,5})\s*(?:KG|KILO|KILOGRAM)\b""")
    private const val MIN_PRICE_TRY = 500
    private const val MAX_PRICE_TRY = 10_000_000
}
