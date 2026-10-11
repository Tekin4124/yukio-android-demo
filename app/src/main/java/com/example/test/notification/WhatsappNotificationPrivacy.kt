package com.example.test.notification

object WhatsappNotificationPrivacy {
    const val HIDDEN_SENDER = "Gizli"

    private val invisibleChars = Regex("[\\u200E\\u200F\\u202A-\\u202E\\u2066-\\u2069]")
    private val unicodeSpaces = Regex("[\\u00A0\\u202F]")
    private val messageCount = Regex("""\s*\(\d+\s+mesaj\)\s*""", RegexOption.IGNORE_CASE)
    private val senderSuffix = Regex(""":\s*~\s*(.+)$""")
    private val repeatedWhitespace = Regex("""\s+""")

    fun sourceCandidates(
        conversationTitle: String,
        notificationTitle: String,
        subText: String
    ): List<String> {
        val cleaned = listOf(conversationTitle, subText, notificationTitle)
            .map { cleanSourceName(it) }
            .filter { it.isNotBlank() && it != "WhatsApp" }
            .distinctBy { normalizeForCompare(it) }

        return cleaned.ifEmpty { listOf("WhatsApp") }
    }

    fun senderNameCandidates(vararg values: String): List<String> {
        return values
            .map { normalizeForDisplay(it) }
            .mapNotNull { value ->
                senderSuffix.find(value)
                    ?.groupValues
                    ?.getOrNull(1)
                    ?.trim(' ', ':', '~')
                    ?.takeIf { it.isNotBlank() }
            }
            .distinctBy { normalizeForCompare(it) }
    }

    fun cleanSourceName(raw: String): String {
        var cleaned = normalizeForDisplay(raw)
        senderSuffix.find(cleaned)?.let { match ->
            cleaned = cleaned.substring(0, match.range.first)
        }
        cleaned = messageCount.replace(cleaned, " ")
        cleaned = repeatedWhitespace.replace(cleaned, " ").trim(' ', ':', '-', '~')
        return cleaned.ifBlank { "WhatsApp" }
    }

    fun stripKnownSenderPrefix(message: String, senderCandidates: List<String>): String {
        var cleaned = normalizeForDisplay(message)
        senderCandidates.forEach { sender ->
            val normalizedSender = normalizeForDisplay(sender).trim()
            if (normalizedSender.isBlank()) return@forEach

            val prefixes = listOf("$normalizedSender:", "~ $normalizedSender:", "~$normalizedSender:")
            prefixes.firstOrNull { prefix ->
                cleaned.startsWith(prefix, ignoreCase = true)
            }?.let { prefix ->
                cleaned = cleaned.drop(prefix.length).trim()
            }
        }
        return cleaned
    }

    private fun normalizeForDisplay(value: String): String {
        return value
            .replace(invisibleChars, "")
            .replace(unicodeSpaces, " ")
            .trim()
    }

    private fun normalizeForCompare(value: String): String {
        return repeatedWhitespace.replace(normalizeForDisplay(value), " ").lowercase()
    }
}
