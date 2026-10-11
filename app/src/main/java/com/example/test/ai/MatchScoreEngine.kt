package com.example.test.ai

import com.example.test.data.local.LoadOfferEntity
import com.example.test.data.local.RouteItem
import com.example.test.data.local.UserProfileEntity
import com.example.test.data.local.deliveryStops
import com.example.test.data.local.effectiveMaxTonnage
import com.example.test.data.local.effectiveWeightKg
import com.example.test.data.local.pickupStops
import kotlin.math.max
import kotlin.math.min

object ScoreWeights {
    const val VEHICLE_TYPE = 15
    const val TRAILER_TYPE = 10
    const val TONNAGE = 12
    const val DISTANCE = 18
    const val ROUTE_MATCH = 15
    const val PRICE = 12
    const val URGENCY = 5
    const val RISK = 8
    const val USER_HISTORY = 5
}

enum class InteractionType {
    VIEWED,
    CALLED,
    ACCEPTED,
    REJECTED,
    SAVED,
    PARSE_CORRECT,
    PARSE_WRONG,
    PARSE_MISSING
}

data class MatchResult(
    val score: Int,
    val grade: String,
    val factors: Map<String, FactorScore>,
    val recommendation: String,
    val riskLevel: RiskLevel = RiskLevel.LOW,
    val estimatedProfit: Int? = null
)

data class FactorScore(
    val factorName: String,
    val rawScore: Int,
    val weight: Int,
    val weightedScore: Int,
    val reason: String,
    val riskLevel: RiskLevel = RiskLevel.LOW
)

data class UserInteraction(
    val loadId: Long,
    val fromCity: String,
    val toCity: String,
    val vehicleType: String?,
    val price: Int?,
    val action: InteractionType,
    val timestamp: Long
)

class MatchScoreEngine(
    private val now: () -> Long = { System.currentTimeMillis() }
) {
    private val riskAnalyzer = RiskAnalyzer()
    private val distanceEstimator = DistanceEstimator()
    private val priceEstimator = PriceEstimator()
    private val hiddenRecommendationFactors = setOf("Güvenlik", "Güvenilirlik", "Güzergah", "Aciliyet", "Tercihler")

    fun calculateMatchScore(
        load: LoadOfferEntity,
        user: UserProfileEntity,
        userInteractions: List<UserInteraction> = emptyList()
    ): MatchResult {
        val factors = mutableMapOf<String, FactorScore>()
        factors["vehicle"] = calcVehicleScore(load, user)
        factors["trailer"] = calcTrailerScore(load, user)
        factors["tonnage"] = calcTonnageScore(load, user)
        factors["distance"] = calcDistanceScore(load, user)
        factors["route"] = calcRouteScore(load, user)
        factors["price"] = calcPriceScore(load, user)
        factors["urgency"] = calcUrgencyScore(load)
        factors["risk"] = calcRiskScore(load)
        factors["history"] = calcHistoryScore(load, userInteractions)

        val total = factors.values.sumOf { it.weightedScore }
        val finalScore = max(0, min(100, total))
        val grade = when {
            finalScore >= 90 -> "A+"
            finalScore >= 80 -> "A"
            finalScore >= 70 -> "B+"
            finalScore >= 60 -> "B"
            finalScore >= 50 -> "C"
            finalScore >= 40 -> "D"
            else -> "F"
        }
        val recommendation = generateRecommendation(finalScore, factors)
        val estimatedProfit = calculateEstimatedProfit(load, user)
        return MatchResult(
            score = finalScore,
            grade = grade,
            factors = factors,
            recommendation = recommendation,
            riskLevel = factors["risk"]?.riskLevel ?: RiskLevel.LOW,
            estimatedProfit = estimatedProfit
        )
    }

    private fun calcVehicleScore(load: LoadOfferEntity, user: UserProfileEntity): FactorScore {
        // vehicleCanonical öncelikli: detector'ın kanonik çıktısı ("kamyon",
        // "tır,kamyon" gibi). Yoksa vehicleType'a (ham parser çıktısı) düş.
        val canonicalCompat = load.vehicleCanonical
            ?.takeIf { it.isNotBlank() }
            ?.let { canonical ->
                val vehicleTags = canonical.split(",")
                    .map { it.trim().uppercase().replace("İ","I").replace("Ş","S")
                        .replace("Ğ","G").replace("Ü","U").replace("Ö","O").replace("Ç","C") }
                    .filter { it in VehicleCompatibility.knownVehicles }
                if (vehicleTags.isEmpty()) null
                else vehicleTags.maxOf { tag ->
                    VehicleCompatibility.getCompatibility(user.vehicleType, tag)
                }
            }

        val compatibility = canonicalCompat
            ?: lightLoadVehicleCompatibility(load, user.vehicleType)
            ?: VehicleCompatibility.getCompatibility(user.vehicleType, load.vehicleType)

        val raw = (compatibility * 100).toInt()
        val weighted = (compatibility * ScoreWeights.VEHICLE_TYPE).toInt()
        val reason = when {
            compatibility >= 0.9f -> "✅ Araç tipi mükemmel uyumlu"
            compatibility >= 0.7f -> "👍 Araç tipi uygun"
            compatibility >= 0.4f -> "⚠️ Araç tipi kısmen uygun"
            else -> "❌ Araç tipi uyumsuz"
        }
        return FactorScore("Araç Tipi", raw, ScoreWeights.VEHICLE_TYPE, weighted, reason)
    }

    private fun calcTrailerScore(load: LoadOfferEntity, user: UserProfileEntity): FactorScore {
        val compatibility = TrailerCompatibility.getCompatibility(user.trailerType, load.vehicleType)
        val raw = (compatibility * 100).toInt()
        val weighted = (compatibility * ScoreWeights.TRAILER_TYPE).toInt()
        val reason = when {
            compatibility >= 0.9f -> "✅ Dorse tipi mükemmel"
            compatibility >= 0.7f -> "👍 Dorse tipi uygun"
            else -> "⚠️ Dorse kısmen uygun"
        }
        return FactorScore("Dorse Tipi", raw, ScoreWeights.TRAILER_TYPE, weighted, reason)
    }

    private fun lightLoadVehicleCompatibility(load: LoadOfferEntity, userVehicle: String): Float? {
        val loadVehicle = load.vehicleType?.let(::normalize).orEmpty()
        val hasExplicitVehicle = listOf("KAMYONET", "KAMYON", "CEKICI", "LOWBED", "TIR")
            .any { hasToken(loadVehicle, it) }
        if (hasExplicitVehicle) return null

        val weightKg = load.effectiveWeightKg() ?: return null
        val user = normalize(userVehicle)
        return when {
            weightKg <= 3500 && user == "KAMYONET" -> 1.0f
            weightKg <= 3500 && user == "KAMYON" -> 0.95f
            weightKg <= 3500 && user == "TIR" -> 0.45f
            weightKg <= 12000 && user == "KAMYON" -> 1.0f
            weightKg <= 12000 && user == "TIR" -> 0.75f
            weightKg <= 12000 && user == "KAMYONET" -> 0.25f
            else -> null
        }
    }

    private fun calcTonnageScore(load: LoadOfferEntity, user: UserProfileEntity): FactorScore {
        val loadKg = load.effectiveWeightKg()
        if (loadKg != null) {
            val userMaxKg = user.maxTonnage * 1000
            val ratio = loadKg.toFloat() / userMaxKg
            val score = when {
                loadKg <= userMaxKg -> when {
                    ratio >= 0.85f -> 100
                    ratio >= 0.50f -> 90
                    ratio >= 0.15f -> 80
                    else -> 70
                }
                loadKg <= userMaxKg * 1.05f -> 70
                loadKg <= userMaxKg * 1.10f -> 40
                else -> 0
            }
            val weighted = (score.toFloat() / 100 * ScoreWeights.TONNAGE).toInt()
            val label = if (loadKg < 1000) "$loadKg kg" else "${loadKg / 1000f} ton"
            val reason = when {
                loadKg > userMaxKg * 1.10f -> "Kapasite asimi ($label)"
                loadKg > userMaxKg -> "Kapasite sinirda ($label)"
                loadKg < 1000 -> "Hafif parsiyel yuk ($label)"
                ratio >= 0.50f -> "Kapasite uygun ($label)"
                else -> "Dusuk agirlikli yuk ($label)"
            }
            return FactorScore("Tonaj", score, ScoreWeights.TONNAGE, weighted, reason)
        }
        val loadT = load.effectiveMaxTonnage()
            ?: return FactorScore("Tonaj", 70, ScoreWeights.TONNAGE, 8, "ℹ️ Tonaj belirtilmemiş")
        val userMax = user.maxTonnage
        val ratio = loadT.toFloat() / userMax
        val score = when {
            loadT <= userMax -> when {
                ratio >= 0.85f -> 100
                ratio >= 0.70f -> 90
                ratio >= 0.50f -> 75
                else -> 50
            }
            loadT <= userMax * 1.05f -> 70
            loadT <= userMax * 1.10f -> 40
            else -> 0
        }
        val weighted = (score.toFloat() / 100 * ScoreWeights.TONNAGE).toInt()
        val reason = when {
            loadT > userMax * 1.10f -> "❌ Kapasite aşımı! (${loadT}t > ${userMax}t)"
            loadT > userMax -> "⚠️ Kapasite sınırda (${loadT}t / ${userMax}t)"
            ratio >= 0.85f -> "✅ Optimal kapasite (%${(ratio * 100).toInt()})"
            ratio >= 0.50f -> "👍 İyi kapasite (%${(ratio * 100).toInt()})"
            else -> "ℹ️ Düşük kapasite (%${(ratio * 100).toInt()})"
        }
        return FactorScore("Tonaj", score, ScoreWeights.TONNAGE, weighted, reason)
    }

    private fun calcDistanceScore(load: LoadOfferEntity, user: UserProfileEntity): FactorScore {
        val loadRouteDistance = CityDistances.getDistance(load.fromCity, load.toCity)
        val usesLoadRouteDistance = user.routes.isEmpty() || loadRouteDistance <= 30
        val distanceToLoad = if (usesLoadRouteDistance) {
            loadRouteDistance
        } else {
            distanceEstimator.estimateDistanceToLoad(user.routes, load.fromCity)
        }
        val score = when {
            distanceToLoad <= 30 -> 100
            distanceToLoad <= 50 -> 95
            distanceToLoad <= 100 -> 85
            distanceToLoad <= 200 -> 70
            distanceToLoad <= 300 -> 55
            distanceToLoad <= 500 -> 35
            else -> max(0, 20 - (distanceToLoad - 500) / 100)
        }
        val weighted = (score.toFloat() / 100 * ScoreWeights.DISTANCE).toInt()
        val reason = when {
            usesLoadRouteDistance && distanceToLoad <= 10 -> "✅ Aynı şehir / çok kısa rota (~${distanceToLoad}km)"
            usesLoadRouteDistance && distanceToLoad <= 50 -> "✅ Kısa rota (~${distanceToLoad}km)"
            usesLoadRouteDistance && distanceToLoad <= 150 -> "👍 Yakın rota (~${distanceToLoad}km)"
            usesLoadRouteDistance && distanceToLoad <= 300 -> "ℹ️ Orta rota (~${distanceToLoad}km)"
            usesLoadRouteDistance -> "⚠️ Uzun rota (~${distanceToLoad}km)"
            distanceToLoad <= 50 -> "✅ Yük çok yakın (~${distanceToLoad}km)"
            distanceToLoad <= 150 -> "👍 Yük yakın (~${distanceToLoad}km)"
            distanceToLoad <= 300 -> "ℹ️ Orta mesafe (~${distanceToLoad}km)"
            else -> "⚠️ Yük uzak (~${distanceToLoad}km)"
        }
        return FactorScore("Mesafe", score, ScoreWeights.DISTANCE, weighted, reason)
    }

    private fun calcRouteScore(load: LoadOfferEntity, user: UserProfileEntity): FactorScore {
        if (user.routes.isEmpty()) {
            return FactorScore("Güzergah", 50, ScoreWeights.ROUTE_MATCH, 7, "ℹ️ Güzergah tercihi tanımlı değil")
        }
        // Türkçe İ/Ş/Ğ/Ü/Ö/Ç'yi ASCII'ye katla: parser şehirleri "ISTANBUL"
        // gibi ASCII üretir, profildeki güzergah "İstanbul" gibi Türkçe olabilir.
        // Düz .uppercase() bunları eşleyemez (İ U+0130 ≠ I).
        val loadPairs = load.pickupStops().flatMap { pickup ->
            val loadFrom = pickup.city?.let(::normalize) ?: return@flatMap emptyList()
            load.deliveryStops().mapNotNull { delivery ->
                delivery.city?.let(::normalize)?.let { loadTo -> loadFrom to loadTo }
            }
        }
        var best = 0f
        var matchType = ""

        for (route in user.routes) {
            val rf = normalize(route.fromCity)
            val rt = normalize(route.toCity)
            for ((loadFrom, loadTo) in loadPairs) {
                if (rf == loadFrom && rt == loadTo) {
                    best = 1.0f
                    matchType = "Tam eşleşme"
                    break
                }
                if (rf == loadTo && rt == loadFrom && 0.90f > best) {
                    best = 0.90f
                    matchType = "Dönüş yükü"
                }
                if (rf == loadFrom && 0.65f > best) {
                    best = 0.65f
                    matchType = "Kalkış noktası eşleşti"
                }
                if (rt == loadTo && 0.55f > best) {
                    best = 0.55f
                    matchType = "Varış noktası eşleşti"
                }
                val fromDist = CityDistances.getDistance(rf, loadFrom)
                val toDist = CityDistances.getDistance(rt, loadTo)
                if (fromDist <= 100 && toDist <= 100 && 0.50f > best) {
                    best = 0.50f
                    matchType = "Yakın güzergah"
                }
            }
            if (best >= 1.0f) break
        }

        val raw = (best * 100).toInt()
        val weighted = (best * ScoreWeights.ROUTE_MATCH).toInt()
        val reason = when {
            best >= 0.95f -> "✅ $matchType: ${load.fromCity} → ${load.toCity}"
            best >= 0.85f -> "👍 $matchType"
            best >= 0.50f -> "ℹ️ $matchType"
            else -> "⚠️ Tercih edilen güzergah dışında"
        }
        return FactorScore("Güzergah", raw, ScoreWeights.ROUTE_MATCH, weighted, reason)
    }

    private fun calcPriceScore(load: LoadOfferEntity, user: UserProfileEntity): FactorScore {
        val (score, reason) = priceEstimator.estimate(load, user.priceMin, user.priceMax)
        val weighted = (score.toFloat() / 100 * ScoreWeights.PRICE).toInt()
        return FactorScore("Fiyat", score, ScoreWeights.PRICE, weighted, reason)
    }

    private fun calcUrgencyScore(load: LoadOfferEntity): FactorScore {
        // Duz lowercase() Turkce 'İ'yi "i + birlesik nokta" yapar ve "ACİL"
        // icinde "acil" aramasi tutmaz. normalize() ASCII'ye katladigi icin
        // buyuk/kucuk ve Turkce/ASCII yazimlarin hepsi yakalanir.
        val text = normalize("${load.rawMessage} ${load.notes ?: ""}")
        var urgency = 0
        if (text.contains("ACIL")) urgency += 35
        if (text.contains("HEMEN")) urgency += 25
        if (text.contains("BUGUN")) urgency += 20
        if (text.contains("YARIN")) urgency += 15
        if (text.contains("BEKLIYOR")) urgency += 10
        val ageMinutes = (now() - load.createdAt) / 60000
        val freshness = when {
            ageMinutes <= 5 -> 25
            ageMinutes <= 15 -> 20
            ageMinutes <= 30 -> 15
            ageMinutes <= 60 -> 10
            ageMinutes <= 120 -> 5
            else -> 0
        }
        val total = min(100, urgency + freshness)
        val weighted = (total.toFloat() / 100 * ScoreWeights.URGENCY).toInt()
        val reason = when {
            total >= 50 -> "🔥 Acil yük - hızlı karar verin!"
            total >= 30 -> "⏰ Güncel ve acil"
            total >= 15 -> "📅 Güncel ilan"
            else -> "ℹ️ Normal öncelik"
        }
        return FactorScore("Aciliyet", total, ScoreWeights.URGENCY, weighted, reason)
    }

    private fun calcRiskScore(load: LoadOfferEntity): FactorScore {
        val risk = riskAnalyzer.analyzeRisk(load)
        val safety = 100 - risk.riskPercentage
        val weighted = (safety.toFloat() / 100 * ScoreWeights.RISK).toInt()
        return FactorScore("Güvenlik", safety, ScoreWeights.RISK, weighted, risk.summary, risk.level)
    }

    private fun calcHistoryScore(load: LoadOfferEntity, interactions: List<UserInteraction>): FactorScore {
        if (interactions.isEmpty()) return FactorScore("Tercihler", 50, ScoreWeights.USER_HISTORY, 2, "ℹ️ Henüz yeterli veri yok")
        var similarity = 50f
        val accepted = interactions.filter { it.action == InteractionType.ACCEPTED }
        val called = interactions.filter { it.action == InteractionType.CALLED }
        val rejected = interactions.filter { it.action == InteractionType.REJECTED }

        (accepted + called).forEach {
            if (it.fromCity.uppercase() == load.fromCity.uppercase()) similarity += 8
            if (it.toCity.uppercase() == load.toCity.uppercase()) similarity += 8
            if (it.vehicleType?.uppercase() == load.vehicleType?.uppercase()) similarity += 5
        }
        rejected.forEach {
            if (it.fromCity.uppercase() == load.fromCity.uppercase() &&
                it.toCity.uppercase() == load.toCity.uppercase()
            ) similarity -= 15
        }
        val finalScore = max(0, min(100, similarity.toInt()))
        val weighted = (finalScore.toFloat() / 100 * ScoreWeights.USER_HISTORY).toInt()
        val reason = when {
            finalScore >= 80 -> "✅ Geçmiş tercihlerinize çok uygun"
            finalScore >= 60 -> "👍 Tercihlerinize uygun"
            finalScore >= 40 -> "ℹ️ Tercihlerinize kısmen uygun"
            else -> "⚠️ Tercihlerinize uymuyor"
        }
        return FactorScore("Tercihler", finalScore, ScoreWeights.USER_HISTORY, weighted, reason)
    }

    private fun calculateEstimatedProfit(load: LoadOfferEntity, user: UserProfileEntity): Int? {
        val price = load.priceTry ?: return null
        val distance = CityDistances.getDistance(load.fromCity, load.toCity)
        if (distance == 0) return null
        val fuelConsumption = user.fuelConsumption ?: 35f
        val fuelPrice = 42f
        val costPerKm = user.costPerKm ?: ((fuelConsumption / 100) * fuelPrice + 5)
        val totalCost = (distance * costPerKm).toInt()
        return price - totalCost
    }

    private fun generateRecommendation(score: Int, factors: Map<String, FactorScore>): String {
        val visibleFactors = factors.values.filterNot { it.factorName in hiddenRecommendationFactors }
        val weak = visibleFactors.filter { it.rawScore < 50 }.map { it.factorName }
        val strong = visibleFactors.filter { it.rawScore >= 80 }.map { it.factorName }
        return when {
            score >= 90 -> "🔥 MÜKEMMEL EŞLEŞME! Hemen iletişime geçin."
            score >= 80 -> "✅ Çok uygun ilan. ${if (strong.isNotEmpty()) "Güçlü: ${strong.joinToString()}" else ""}"
            score >= 70 -> "👍 İyi bir seçenek. Değerlendirin."
            score >= 60 -> "ℹ️ Kabul edilebilir. ${if (weak.isNotEmpty()) "Dikkat: ${weak.joinToString()}" else ""}"
            score >= 40 -> if (weak.isNotEmpty()) {
                "⚠️ Bazı uyumsuzluklar var: ${weak.joinToString()}"
            } else {
                "⚠️ Bu ilan düşük puanlı görünüyor."
            }
            else -> "❌ Bu ilan uygun görünmüyor."
        }
    }
}

object VehicleCompatibility {
    private val vehicleKeys = listOf("KAMYONET", "KAMYON", "CEKICI", "LOWBED", "TIR", "PANELVAN", "PICKUP", "ROMORK")
    val knownVehicles = vehicleKeys.toSet()

    private val matrix = mapOf(
        "TIR" to mapOf("TIR" to 1.0f, "KAMYON" to 0.3f, "KAMYONET" to 0.0f, "CEKICI" to 0.8f, "LOWBED" to 0.4f, "PANELVAN" to 0.0f, "PICKUP" to 0.0f, "ROMORK" to 0.5f),
        "KAMYON" to mapOf("TIR" to 0.2f, "KAMYON" to 1.0f, "KAMYONET" to 0.5f, "CEKICI" to 0.1f, "LOWBED" to 0.3f, "PANELVAN" to 0.3f, "PICKUP" to 0.4f, "ROMORK" to 0.2f),
        "KAMYONET" to mapOf("TIR" to 0.0f, "KAMYON" to 0.3f, "KAMYONET" to 1.0f, "CEKICI" to 0.0f, "LOWBED" to 0.0f, "PANELVAN" to 0.7f, "PICKUP" to 0.6f, "ROMORK" to 0.0f),
        "CEKICI" to mapOf("TIR" to 0.9f, "KAMYON" to 0.2f, "KAMYONET" to 0.0f, "CEKICI" to 1.0f, "LOWBED" to 0.7f, "PANELVAN" to 0.0f, "PICKUP" to 0.0f, "ROMORK" to 0.6f),
        "LOWBED" to mapOf("TIR" to 0.3f, "KAMYON" to 0.2f, "KAMYONET" to 0.0f, "CEKICI" to 0.6f, "LOWBED" to 1.0f, "PANELVAN" to 0.0f, "PICKUP" to 0.0f, "ROMORK" to 0.3f),
        "PANELVAN" to mapOf("TIR" to 0.0f, "KAMYON" to 0.1f, "KAMYONET" to 0.6f, "CEKICI" to 0.0f, "LOWBED" to 0.0f, "PANELVAN" to 1.0f, "PICKUP" to 0.5f, "ROMORK" to 0.0f),
        "PICKUP" to mapOf("TIR" to 0.0f, "KAMYON" to 0.2f, "KAMYONET" to 0.5f, "CEKICI" to 0.0f, "LOWBED" to 0.0f, "PANELVAN" to 0.4f, "PICKUP" to 1.0f, "ROMORK" to 0.0f),
        "ROMORK" to mapOf("TIR" to 0.4f, "KAMYON" to 0.2f, "KAMYONET" to 0.0f, "CEKICI" to 0.7f, "LOWBED" to 0.3f, "PANELVAN" to 0.0f, "PICKUP" to 0.0f, "ROMORK" to 1.0f)
    )
    fun getCompatibility(userVehicle: String, loadVehicle: String?): Float {
        if (loadVehicle == null) return 0.7f
        val user = normalize(userVehicle)
        val load = normalize(loadVehicle)
        if (load.isBlank()) return 0.7f
        if (hasToken(load, "SERBEST")) return 0.8f

        val compatibilityMap = matrix[user] ?: return 0.5f
        val detectedLoadVehicles = vehicleKeys.filter { hasToken(load, it) }
        if (detectedLoadVehicles.contains(user)) return 1.0f
        return detectedLoadVehicles
            .mapNotNull { compatibilityMap[it] }
            .maxOrNull()
            ?: 0.5f
    }
}

object TrailerCompatibility {
    private val trailerKeys = listOf("13.60", "1360", "TENTE", "ACIK", "KAPALI", "FRIGO", "KONTEYNER", "PLATFORM")

    private val matrix = mapOf(
        "13.60" to mapOf("13.60" to 1.0f, "1360" to 1.0f, "TENTE" to 0.8f, "ACIK" to 0.6f, "KAPALI" to 0.6f, "FRIGO" to 0.0f, "KONTEYNER" to 0.3f, "PLATFORM" to 0.5f),
        "TENTE" to mapOf("13.60" to 0.7f, "1360" to 0.7f, "TENTE" to 1.0f, "ACIK" to 0.8f, "KAPALI" to 0.6f, "FRIGO" to 0.0f, "KONTEYNER" to 0.2f, "PLATFORM" to 0.5f),
        "ACIK" to mapOf("13.60" to 0.5f, "1360" to 0.5f, "TENTE" to 0.7f, "ACIK" to 1.0f, "KAPALI" to 0.4f, "FRIGO" to 0.0f, "KONTEYNER" to 0.3f, "PLATFORM" to 0.8f),
        "KAPALI" to mapOf("13.60" to 0.6f, "1360" to 0.6f, "TENTE" to 0.6f, "ACIK" to 0.3f, "KAPALI" to 1.0f, "FRIGO" to 0.2f, "KONTEYNER" to 0.3f, "PLATFORM" to 0.2f),
        "FRIGO" to mapOf("13.60" to 0.0f, "1360" to 0.0f, "TENTE" to 0.0f, "ACIK" to 0.0f, "KAPALI" to 0.2f, "FRIGO" to 1.0f, "KONTEYNER" to 0.2f, "PLATFORM" to 0.0f),
        "KONTEYNER" to mapOf("13.60" to 0.2f, "1360" to 0.2f, "TENTE" to 0.1f, "ACIK" to 0.3f, "KAPALI" to 0.3f, "FRIGO" to 0.1f, "KONTEYNER" to 1.0f, "PLATFORM" to 0.2f),
        "PLATFORM" to mapOf("13.60" to 0.5f, "1360" to 0.5f, "TENTE" to 0.5f, "ACIK" to 0.8f, "KAPALI" to 0.2f, "FRIGO" to 0.0f, "KONTEYNER" to 0.2f, "PLATFORM" to 1.0f)
    )
    fun getCompatibility(userTrailer: String?, loadTrailer: String?): Float {
        if (userTrailer == null || loadTrailer == null) return 0.6f
        val user = normalize(userTrailer).let { if (it == "1360") "13.60" else it }
        val load = normalize(loadTrailer)
        if (load.isBlank()) return 0.6f
        val compatibilityMap = matrix[user] ?: return 0.5f
        if (hasToken(load, user)) return 1.0f
        return compatibilityMap
            .filterKeys { trailerKey -> trailerKeys.contains(trailerKey) && hasToken(load, trailerKey) }
            .values
            .maxOrNull()
            ?: 0.5f
    }
}

private fun normalize(value: String): String {
    return value.uppercase()
        .replace("İ", "I")
        .replace("Ş", "S")
        .replace("Ğ", "G")
        .replace("Ü", "U")
        .replace("Ö", "O")
        .replace("Ç", "C")
        .replace(Regex("\\s+"), " ")
        .trim()
}

private fun hasToken(text: String, token: String): Boolean {
    return when (token) {
        "13.60" -> text.contains("13.60")
        "1360" -> text.contains("1360")
        else -> Regex("(^|[^A-Z0-9])${Regex.escape(token)}([^A-Z0-9]|$)").containsMatchIn(text)
    }
}

