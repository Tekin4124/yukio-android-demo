package com.example.test.ai

import com.example.test.data.local.LoadOfferEntity
import kotlin.math.max
import kotlin.math.min

enum class RiskLevel { LOW, MEDIUM, HIGH, CRITICAL }

data class RiskAnalysisResult(
    val riskPercentage: Int,
    val level: RiskLevel,
    val factors: List<String>,
    val summary: String
)

object RiskKeywords {
    // Anahtar kelimeler ASCII buyuk harf tutulur; metin foldTurkish() ile ayni
    // forma katlanir. Boylece "PEŞİN ÖDEME", "peşin ödeme" ve "pesin odeme"
    // yazimlarin hepsi tek kelimeyle yakalanir.
    val highRisk = listOf("ACIL PARA", "PESIN ODEME", "KAPORA", "ONCE PARA", "WESTERN UNION", "HAVALE YAP")
    val mediumRisk = listOf("SON FIRSAT", "KACIRMA")
    val trusted = listOf("FIRMA", "SIRKET", "LTD", "A.S", "LOJISTIK", "NAKLIYAT", "TASIMACILIK")
}

class RiskAnalyzer {
    fun analyzeRisk(load: LoadOfferEntity): RiskAnalysisResult {
        val factors = mutableListOf<String>()
        var riskScore = 0
        // Duz lowercase() Turkce 'İ'de birlesik nokta birakip aramayi bozuyordu;
        // ASCII'ye katlanmis buyuk harf uzerinden ara.
        val allText = foldTurkish("${load.rawMessage} ${load.notes ?: ""}")
        val phone = load.phone
        val missingPhone = phone.isBlank()
        val suspiciousPrice = load.priceTry == null || load.priceTry <= 0

        RiskKeywords.highRisk.forEach {
            if (allText.contains(it)) {
                riskScore += 25
                factors.add("Supheli ifade: '$it'")
            }
        }

        RiskKeywords.mediumRisk.forEach {
            if (allText.contains(it)) {
                riskScore += 5
            }
        }

        if (missingPhone) {
            riskScore += 30
            factors.add("Telefon numarasi yok")
        } else {
            val clean = phone.replace(Regex("[^0-9]"), "")
            if (clean.length < 10) {
                riskScore += 15
                factors.add("Eksik telefon numarasi")
            }
            if (clean.matches(Regex(".*(.)(\\1){4,}.*"))) {
                riskScore += 20
                factors.add("Supheli numara formati")
            }
        }

        if (suspiciousPrice) {
            riskScore += 15
            factors.add("Fiyat belirtilmemis")
        }

        RiskKeywords.trusted.forEach {
            if (allText.contains(it)) riskScore -= 10
        }

        if (load.rawMessage.length < 15) {
            riskScore += 10
            factors.add("Cok kisa mesaj")
        }

        val canBeHighRisk = missingPhone && suspiciousPrice
        val cappedRisk = if (canBeHighRisk) riskScore else min(riskScore, 39)
        val finalRisk = max(0, min(100, cappedRisk))
        val level = when {
            finalRisk >= 70 -> RiskLevel.CRITICAL
            finalRisk >= 40 -> RiskLevel.HIGH
            finalRisk >= 20 -> RiskLevel.MEDIUM
            else -> RiskLevel.LOW
        }
        val summary = when (level) {
            RiskLevel.CRITICAL -> "Cok yuksek guven uyarisi"
            RiskLevel.HIGH -> "Guven uyarisi: ilani dogrulayin"
            RiskLevel.MEDIUM -> "Orta guven uyarisi"
            RiskLevel.LOW -> "Guven uyarisi yok"
        }
        return RiskAnalysisResult(finalRisk, level, factors, summary)
    }

    private fun foldTurkish(value: String): String {
        return value.uppercase()
            .replace("İ", "I")
            .replace("Ş", "S")
            .replace("Ğ", "G")
            .replace("Ü", "U")
            .replace("Ö", "O")
            .replace("Ç", "C")
    }
}
