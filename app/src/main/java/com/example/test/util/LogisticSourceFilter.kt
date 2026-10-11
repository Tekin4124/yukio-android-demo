package com.example.test.util

import java.text.Normalizer

object LogisticSourceFilter {
    private val keywords = listOf(
        "lojistik", "nakliye", "nakliyat", "kamyon", "tir", "yuk",
        "yukler", "yukleme", "tasima", "tasimacilik", "kargo", "ambar",
        "parsiyel", "kooperatif", "koop", "transport", "trans", "vip",
        "seramik", "sevkiyat", "palet", "dorse", "frigo", "tenteli",
        "agro", "soguk", "cekici", "depo", "fabrika", "ihracat",
        "gumruk", "sevk", "tasiyici", "forwarder", "spedisyon",
        "silobas", "damper", "sahra"
    )

    fun isAllowed(source: String): Boolean {
        val text = normalize(source)
        val compactText = text.filter { it.isLetterOrDigit() }
        return keywords.any { keyword ->
            text.contains(keyword) || compactText.contains(keyword)
        }
    }

    fun normalize(value: String): String {
        val fixedTurkishI = value
            .replace('\u0130', 'I')
            .replace('\u0131', 'i')
        return Normalizer.normalize(fixedTurkishI, Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
            .lowercase()
            .replace(Regex("[^a-z0-9]+"), " ")
            .trim()
    }
}
