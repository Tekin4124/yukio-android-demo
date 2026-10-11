package com.example.test.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.test.data.model.RouteStop
import com.example.test.data.model.RouteStopRole
import com.example.test.data.model.normalizedRole
import java.util.Locale

@Entity(
    tableName = "load_offers",
    indices = [
        Index(value = ["fromCity"]),
        Index(value = ["toCity"]),
        Index(value = ["createdAt"]),
        Index(value = ["rawMessage"])
    ]
)
data class LoadOfferEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val fromCity: String,
    val fromDistrict: String?,
    val toCity: String,
    val toDistrict: String?,
    val tonnageTon: Int?,
    val tonnageText: String? = null,
    val vehicleType: String?,
    // Backend vehicleDetector'dan gelen kanonik etiketler (virgülle ayrılmış,
    // küçük harf). Filtre eşleştirmesi için kullanılır; UI bunu göstermez.
    // Eski backend göndermezse null kalır, filtre eski yolla (vehicleType
    // contains) çalışmaya devam eder.
    val vehicleCanonical: String? = null,
    val priceTry: Int?,
    val phone: String,
    val notes: String?,
    val sourceGroup: String,
    val sourceSender: String,
    val rawMessage: String,
    val createdAt: Long,
    // Kimliksiz parser örneği eşlemesi: backend sample'ı kabul ettiyse
    // opak sampleId ve bu ilanın parser cevabındaki sırası (offerIndex).
    // Opt-in kapalıyken veya eski kayıtlarda null kalır.
    val sampleId: String? = null,
    val offerIndex: Int? = null,
    // Araç TAHMİNİ adayları (virgülle ayrılmış, kanonik küçük harf; örn.
    // "kamyonet,kamyon"). Yalnız açık araç bilgisi yokken dolar. UI "Tahmini
    // araç:" olarak gösterir; filtre/eşleştirme bu alanı KULLANMAZ.
    val vehicleInferredCandidates: String? = null,
    // Null means this is a legacy/scalar-only row.
    val routeStops: List<RouteStop>? = null
)

fun LoadOfferEntity.pickupStops(): List<RouteStop> {
    val stops = normalizedStops(RouteStopRole.PICKUP)
    return stops.ifEmpty {
        listOf(RouteStop("ALIS", 0, fromCity, fromDistrict))
    }
}

fun LoadOfferEntity.deliveryStops(): List<RouteStop> {
    val stops = normalizedStops(RouteStopRole.DELIVERY)
    return stops.ifEmpty {
        listOf(RouteStop("TESLIM", 1, toCity, toDistrict))
    }
}

fun LoadOfferEntity.routeCities(): List<String> =
    (pickupStops() + deliveryStops())
        .mapNotNull { it.city?.trim()?.takeIf(String::isNotBlank) }
        .distinct()

private fun LoadOfferEntity.normalizedStops(role: RouteStopRole): List<RouteStop> =
    routeStops
        .orEmpty()
        .withIndex()
        .sortedWith(
            compareBy<IndexedValue<RouteStop>>(
                { it.value.sequence ?: Int.MAX_VALUE },
                { it.index }
            )
        )
        .map { it.value }
        .filter { it.normalizedRole() == role && !it.city.isNullOrBlank() }
        .distinctBy { stop ->
            listOf(
                stop.city?.trim()?.uppercase(Locale.ROOT),
                stop.district?.trim()?.uppercase(Locale.ROOT)
            )
        }

fun LoadOfferEntity.displayTonnage(): String {
    tonnageText?.trim()?.takeIf { it.isNotBlank() }?.let { text ->
        return when {
            text.contains("kg", ignoreCase = true) -> text
            text.contains("ton", ignoreCase = true) -> text
            else -> "$text ton"
        }
    }
    inferSingleKilogram(rawMessage)?.let { return "$it kg" }
    inferTonnageRange(rawMessage)?.let { (min, max) -> return "$min-$max ton" }
    return effectiveMaxTonnage()?.let { "$it ton" } ?: "Tonaj yok"
}

fun LoadOfferEntity.effectiveMaxTonnage(): Int? {
    return inferSingleKilogram(tonnageText.orEmpty())?.let(::ceilKgToTon)
        ?: inferSingleKilogram(rawMessage)?.let(::ceilKgToTon)
        ?: tonnageTon?.takeIf { it > 0 }
        ?: inferTonnageRange(tonnageText.orEmpty())?.second
        ?: inferSingleTonnage(tonnageText.orEmpty())
        ?: inferTonnageRange(rawMessage)?.second
}

fun LoadOfferEntity.effectiveWeightKg(): Int? {
    inferSingleKilogram(tonnageText.orEmpty())?.let { return it }
    inferSingleKilogram(rawMessage)?.let { return it }
    effectiveMaxTonnage()?.let { return it * 1000 }
    return null
}

private fun inferTonnageRange(text: String): Pair<Int, Int>? {
    val match = Regex(
        pattern = """\b(\d{1,2})\s*(?:-|/|ile|ila)\s*(\d{1,2})\s*(?:ton|t)\b""",
        option = RegexOption.IGNORE_CASE
    ).find(text) ?: return null

    val first = match.groupValues[1].toIntOrNull() ?: return null
    val second = match.groupValues[2].toIntOrNull() ?: return null
    val min = minOf(first, second)
    val max = maxOf(first, second)
    if (max !in 1..80) return null
    return min to max
}

private fun inferSingleTonnage(text: String): Int? {
    return Regex(
        pattern = """\b(\d{1,2})\s*(?:ton|t)?\b""",
        option = RegexOption.IGNORE_CASE
    ).find(text)
        ?.groupValues
        ?.getOrNull(1)
        ?.toIntOrNull()
        ?.takeIf { it in 1..80 }
}

private fun inferSingleKilogram(text: String): Int? {
    val match = Regex(
        pattern = """\b(\d{1,5})(?:[,.](\d{1,3}))?\s*(?:kg|kilo|kilogram)\b""",
        option = RegexOption.IGNORE_CASE
    ).find(text) ?: return null

    val whole = match.groupValues[1].toIntOrNull() ?: return null
    val fraction = match.groupValues.getOrNull(2).orEmpty()
    val kg = if (fraction.isNotBlank() && whole <= 80) {
        // "0,5 ton" is handled by ton patterns; for kg, comma is usually decimal kg.
        whole
    } else {
        whole
    }
    return kg.takeIf { it in 1..80_000 }
}

private fun ceilKgToTon(kg: Int): Int {
    return ((kg + 999) / 1000).coerceAtLeast(1)
}
