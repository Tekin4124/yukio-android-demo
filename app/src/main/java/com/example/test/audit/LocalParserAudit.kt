package com.example.test.audit

import com.example.test.data.local.LoadOfferEntity
import java.util.Locale

data class LocalParserAuditReport(
    val totalOffers: Int,
    val distinctMessages: Int,
    val requiresReviewOffers: Int,
    val missingCoreRoute: Int,
    val sameExactRoute: Int,
    val invalidTonnage: Int,
    val invalidPrice: Int,
    val garbledDisplayText: Int,
    val duplicateOffers: Int,
    val missingVehicle: Int,
    val missingTonnage: Int,
    val missingPrice: Int
)

object LocalParserAudit {
    fun analyze(loads: List<LoadOfferEntity>): LocalParserAuditReport {
        var missingCoreRoute = 0
        var sameExactRoute = 0
        var invalidTonnage = 0
        var invalidPrice = 0
        var garbledDisplayText = 0
        var missingVehicle = 0
        var missingTonnage = 0
        var missingPrice = 0
        var requiresReviewOffers = 0
        val duplicateKeys = HashMap<String, Int>()

        loads.forEach { load ->
            val fromCity = normalize(load.fromCity)
            val toCity = normalize(load.toCity)
            val fromDistrict = normalize(load.fromDistrict)
            val toDistrict = normalize(load.toDistrict)
            val routeMissing = fromCity.isBlank() || toCity.isBlank() ||
                fromCity in PLACEHOLDERS || toCity in PLACEHOLDERS
            val sameRoute = !routeMissing &&
                fromCity == toCity &&
                fromDistrict == toDistrict
            val tonnageInvalid = load.tonnageTon?.let { it !in 1..80 } == true
            val priceInvalid = load.priceTry?.let { it !in MIN_PRICE_TRY..MAX_PRICE_TRY } == true
            val garbled = listOf(
                load.fromCity,
                load.fromDistrict,
                load.toCity,
                load.toDistrict,
                load.vehicleType,
                load.tonnageText
            ).filterNotNull().any { GARBLED_TEXT.containsMatchIn(it) }

            if (routeMissing) missingCoreRoute += 1
            if (sameRoute) sameExactRoute += 1
            if (tonnageInvalid) invalidTonnage += 1
            if (priceInvalid) invalidPrice += 1
            if (garbled) garbledDisplayText += 1
            if (load.vehicleType.isNullOrBlank()) missingVehicle += 1
            if (load.tonnageTon == null && load.tonnageText.isNullOrBlank()) missingTonnage += 1
            if (load.priceTry == null) missingPrice += 1
            if (routeMissing || sameRoute || tonnageInvalid || priceInvalid || garbled) {
                requiresReviewOffers += 1
            }

            val duplicateKey = listOf(
                normalizeMessage(load.rawMessage),
                fromCity,
                fromDistrict,
                toCity,
                toDistrict,
                normalize(load.vehicleType),
                load.tonnageTon?.toString().orEmpty(),
                load.priceTry?.toString().orEmpty(),
                load.phone.filter(Char::isDigit)
            ).joinToString("|")
            duplicateKeys[duplicateKey] = (duplicateKeys[duplicateKey] ?: 0) + 1
        }

        return LocalParserAuditReport(
            totalOffers = loads.size,
            distinctMessages = loads.mapTo(linkedSetOf()) { normalizeMessage(it.rawMessage) }.size,
            requiresReviewOffers = requiresReviewOffers,
            missingCoreRoute = missingCoreRoute,
            sameExactRoute = sameExactRoute,
            invalidTonnage = invalidTonnage,
            invalidPrice = invalidPrice,
            garbledDisplayText = garbledDisplayText,
            duplicateOffers = duplicateKeys.values.sumOf { count -> (count - 1).coerceAtLeast(0) },
            missingVehicle = missingVehicle,
            missingTonnage = missingTonnage,
            missingPrice = missingPrice
        )
    }

    private fun normalize(value: String?): String {
        return value
            .orEmpty()
            .trim()
            .uppercase(Locale("tr", "TR"))
            .replace("İ", "I")
            .replace("Ş", "S")
            .replace("Ğ", "G")
            .replace("Ü", "U")
            .replace("Ö", "O")
            .replace("Ç", "C")
            .replace(Regex("""[^\p{L}\p{N}]+"""), " ")
            .trim()
    }

    private fun normalizeMessage(value: String): String {
        return value
            .replace(Regex("""[\u200B-\u200F\u202A-\u202E\u2060-\u206F]"""), "")
            .replace(Regex("""\s+"""), " ")
            .trim()
            .lowercase(Locale("tr", "TR"))
    }

    private val PLACEHOLDERS = setOf("NULL", "YOK", "BELIRSIZ", "BILINMIYOR")
    private val GARBLED_TEXT = Regex("""[ÃÄÅâ]""")
    private const val MIN_PRICE_TRY = 500
    private const val MAX_PRICE_TRY = 10_000_000
}
