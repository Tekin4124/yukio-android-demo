package com.example.test.util

import java.nio.charset.StandardCharsets
import java.util.Base64
import java.util.Locale

/**
 * KVKK uyumu için ham WhatsApp mesajındaki kişisel veriyi maskeleyen yardımcı.
 *
 * Telefon, kişi adı ve gerçek kişiyi belirlenebilir kılan şirket/bağlam bilgisi
 * kişisel veri olabilir. Bu yardımcı bilinen telefonları ve sınırlı bir ad
 * listesini maskeler; tüm kişisel verileri yakalama veya veriyi hukuken anonim
 * hâle getirme garantisi vermez.
 *
 * Akış:
 *   val s = PiiSanitizer.sanitize(rawMessage)
 *   LoadParserApi.parseIlan(s.cleanText)   // backend'e/AI'a maskeli metin gider
 *   // s.phones cihazda kalır → JSON'a sonradan eklenir.
 */
object PiiSanitizer {

    data class Sanitized(
        val cleanText: String,
        val phones: List<String>,
        val phoneMap: Map<String, String> = emptyMap(),
        val names: Map<String, String> = emptyMap()
    )

    /**
     * Türkiye telefon numarası regex'i.
     *
     * Eşleşir:
     *   +90 532 123 45 67 / 0532 123 45 67 / 5321234567 / +905321234567 /
     *   0(532) 123-45-67 / 0.532.123.45.67 / 0212 555 11 22 (sabit hat)
     *
     * - Alan kodu 2-5 ile başlamalı (TR'de geçerli alan kodları).
     * - 7 haneli yerel kısım (3+2+2 split toleranslı).
     * - Önünde/arkasında başka rakam varsa eşleşme reddedilir
     *   (uzun rakam dizilerinde yanlış pozitifi engeller).
     */
    private val phoneRegex = Regex(
        """(?<![\d])(?:\+?9?0[\s\-.()]*)?[2-5]\d{2}[\s\-.()]*\d{3}[\s\-.()]*\d{2}[\s\-.()]*\d{2}(?![\d])"""
    )

    private const val phonePrefix = "TELEFON"
    private const val genericPhonePlaceholder = "[TELEFON]"
    private const val phonePlaceholderPattern = """\[TELEFON(?:_\d+)?]"""
    private const val namePrefix = "ISIM"
    private const val nameToken = """[\p{L}]{2,}(?:\s+[\p{L}]{2,}){0,2}"""
    private const val capitalizedNameToken = """[\p{Lu}][\p{Ll}]{1,}(?:\s+[\p{Lu}][\p{Ll}]{1,}){0,2}"""
    private val labeledNameRegex = Regex(
        """((?:irtibat|yetkili|ilgili|sorumlu|kontak|muhatap)\s*[:\-]?\s*)($nameToken)(?=\s*(?:-|–|—|$phonePlaceholderPattern|\+?\d|,|\.|$))""",
        RegexOption.IGNORE_CASE
    )
    private val nameBeforePhoneRegex = Regex(
        """(?<![\p{L}])($capitalizedNameToken)(?=\s*(?:-|–|—)?\s*$phonePlaceholderPattern)"""
    )
    private val looseNameBeforePhoneRegex = Regex(
        """(^|[\n\r,;:☎📞👤🧖♂️\-]\s*)($nameToken)(?=\s*(?:-|–|—)?\s*$phonePlaceholderPattern)""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE)
    )

    private val nonNameTokens = setOf(
        "KG", "KILO", "GRAM", "GR", "TON", "TONAJ", "HAFIF", "AGIR",
        "CM", "MM", "MT", "METRE", "M",
        "YUK", "YUKLEME", "YUKLER", "YUKLENIR", "BOSALTMA", "TESLIM",
        "TIR", "KAMYON", "KAMYONET", "DORSE", "TENTE", "FRIGO",
        "ACIK", "KAPALI", "PALET", "PARCA", "MAKINA", "MAKINE",
        "NAKLIYAT", "LOJISTIK"
    )

    fun sanitize(text: String, fallbackPhoneCandidate: String? = null): Sanitized {
        val phoneMap = linkedMapOf<String, String>()
        val names = linkedMapOf<String, String>()
        val phoneMaskedText = phoneRegex.replace(text) { match ->
            placeholderForPhone(match.value, phoneMap)
        }
        val phoneMaskedTextWithFallback = appendFallbackPhoneIfNeeded(
            text = phoneMaskedText,
            fallbackPhoneCandidate = fallbackPhoneCandidate,
            phoneMap = phoneMap
        )
        val cleanText = maskNames(phoneMaskedTextWithFallback, names)
        return Sanitized(cleanText, phoneMap.values.distinct(), phoneMap, names)
    }

    fun encodeNames(names: Map<String, String>): String? {
        return encodeMap(names)
    }

    fun decodeNames(encoded: String?): Map<String, String> {
        return decodeMap(encoded)
    }

    fun encodePhones(phones: Map<String, String>): String? {
        return encodeMap(phones)
    }

    fun decodePhones(encoded: String?): Map<String, String> {
        return decodeMap(encoded)
    }

    fun restorePhone(value: String?, phones: Map<String, String>, fallbackPhone: String?): String? {
        if (phones.isEmpty()) {
            return fallbackPhone.takeIf { !it.isNullOrBlank() }
        }
        if (value.isNullOrBlank()) {
            return phones.values.singleOrNull() ?: fallbackPhone.takeIf { !it.isNullOrBlank() }
        }

        val restored = restorePlaceholders(value, phones)
        if (restored.contains("TELEFON", ignoreCase = true)) {
            return phones.values.singleOrNull() ?: fallbackPhone.takeIf { !it.isNullOrBlank() }
        }
        return restored.takeIf { it.any(Char::isDigit) }
            ?: phones.values.singleOrNull()
            ?: fallbackPhone.takeIf { !it.isNullOrBlank() }
    }

    fun restoreNames(value: String?, names: Map<String, String>): String? {
        if (value.isNullOrBlank() || names.isEmpty()) return value
        return restorePlaceholders(value, names)
    }

    fun restoreText(
        value: String,
        phones: Map<String, String>,
        names: Map<String, String> = emptyMap(),
        fallbackPhone: String? = null
    ): String {
        var restored = if (phones.isEmpty()) value else restorePlaceholders(value, phones)
        if (!fallbackPhone.isNullOrBlank()) {
            restored = Regex("""\[?TELEFON(?:_\d+)?]?""", RegexOption.IGNORE_CASE)
                .replace(restored, fallbackPhone)
        }
        return if (names.isEmpty()) restored else restorePlaceholders(restored, names)
    }

    private fun encodeMap(values: Map<String, String>): String? {
        if (values.isEmpty()) return null
        val encoder = Base64.getUrlEncoder().withoutPadding()
        return values.entries.joinToString("\n") { (placeholder, value) ->
            "${encoder.encodeToString(placeholder.toByteArray(StandardCharsets.UTF_8))}=" +
                encoder.encodeToString(value.toByteArray(StandardCharsets.UTF_8))
        }
    }

    private fun decodeMap(encoded: String?): Map<String, String> {
        if (encoded.isNullOrBlank()) return emptyMap()
        val decoder = Base64.getUrlDecoder()
        return encoded
            .lineSequence()
            .mapNotNull { line ->
                val parts = line.split("=", limit = 2)
                if (parts.size != 2) return@mapNotNull null
                runCatching {
                    val placeholder = String(decoder.decode(parts[0]), StandardCharsets.UTF_8)
                    val name = String(decoder.decode(parts[1]), StandardCharsets.UTF_8)
                    placeholder to name
                }.getOrNull()
            }
            .toMap()
    }

    private fun restorePlaceholders(value: String, values: Map<String, String>): String {
        var restored = value.orEmpty()
        values.forEach { (placeholder, replacement) ->
            val id = placeholder.removePrefix("[").removeSuffix("]")
            // (?![0-9]) sonda: TELEFON_1 deseninin TELEFON_10'un basini yemesini engeller.
            restored = Regex("""\[?\Q$id\E]?(?![0-9])""", RegexOption.IGNORE_CASE)
                .replace(restored, replacement)
        }
        return restored
    }

    /**
     * Ham eşleşmeyi +905XXXXXXXXX (E.164) formatına normalize et.
     * Hane sayısı tutmazsa null döner — bu durumda telefon listesine alınmaz.
     */
    private fun normalize(raw: String): String? {
        val digits = raw.filter { it.isDigit() }
        return when {
            digits.length == 10 -> "+90$digits"                                    // 5321234567
            digits.length == 11 && digits.startsWith("0") -> "+90${digits.drop(1)}" // 05321234567
            digits.length == 12 && digits.startsWith("90") -> "+$digits"            // 905321234567
            digits.length == 13 && digits.startsWith("90") -> "+$digits"            // edge case
            else -> null
        }
    }

    private fun placeholderForPhone(rawPhone: String, phones: MutableMap<String, String>): String {
        val phone = normalize(rawPhone) ?: return genericPhonePlaceholder
        phones.entries.firstOrNull { it.value == phone }?.let { return it.key }

        val placeholder = "[${phonePrefix}_${phones.size + 1}]"
        phones[placeholder] = phone
        return placeholder
    }

    private fun appendFallbackPhoneIfNeeded(
        text: String,
        fallbackPhoneCandidate: String?,
        phoneMap: MutableMap<String, String>
    ): String {
        if (phoneMap.isNotEmpty() || fallbackPhoneCandidate.isNullOrBlank()) return text

        val placeholder = placeholderForPhone(fallbackPhoneCandidate, phoneMap)
        if (placeholder == genericPhonePlaceholder) return text

        return buildString {
            append(text.trimEnd())
            if (isNotEmpty()) append('\n')
            append("Irtibat: ")
            append(placeholder)
        }
    }

    private fun maskNames(text: String, names: MutableMap<String, String>): String {
        val labeledMasked = labeledNameRegex.replace(text) { match ->
            val prefix = match.groupValues[1]
            val name = match.groupValues[2]
            if (shouldMaskName(name)) {
                "$prefix${placeholderFor(name, names)}"
            } else {
                match.value
            }
        }

        val directMasked = nameBeforePhoneRegex.replace(labeledMasked) { match ->
            val name = match.groupValues[1]
            if (shouldMaskName(name)) placeholderFor(name, names) else match.value
        }

        return looseNameBeforePhoneRegex.replace(directMasked) { match ->
            val name = match.groupValues[2]
            if (shouldMaskName(name)) {
                "${match.groupValues[1]}${placeholderFor(name, names)}"
            } else {
                match.value
            }
        }
    }

    private fun shouldMaskName(rawName: String): Boolean {
        val normalized = normalizeName(rawName)
        if (normalized.isBlank()) return false
        val tokens = normalized.split(Regex("\\s+")).filter { it.isNotBlank() }
        return tokens.none { it in nonNameTokens }
    }

    private fun placeholderFor(rawName: String, names: MutableMap<String, String>): String {
        val name = rawName.replace(Regex("\\s+"), " ").trim()
        val normalized = normalizeName(name)
        names.entries.firstOrNull { normalizeName(it.value) == normalized }?.let { return it.key }

        val placeholder = "[${namePrefix}_${names.size + 1}]"
        names[placeholder] = name
        return placeholder
    }

    private fun normalizeName(value: String): String {
        return value
            .replace(Regex("\\s+"), " ")
            .trim()
            .uppercase(Locale("tr", "TR"))
    }
}
