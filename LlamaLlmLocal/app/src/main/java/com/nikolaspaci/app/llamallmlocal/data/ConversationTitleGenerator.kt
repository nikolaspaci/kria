package com.nikolaspaci.app.llamallmlocal.data

object ConversationTitleGenerator {

    private const val MAX_TITLE_LENGTH = 50

    fun generateTitle(firstUserMessage: String): String {
        val cleaned = firstUserMessage
            .replace(Regex("[\\n\\r]+"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

        return if (cleaned.length <= MAX_TITLE_LENGTH) {
            cleaned
        } else {
            val truncated = cleaned.take(MAX_TITLE_LENGTH)
            val lastSpace = truncated.lastIndexOf(' ')
            if (lastSpace > MAX_TITLE_LENGTH / 2) {
                truncated.take(lastSpace) + "..."
            } else {
                truncated + "..."
            }
        }
    }

    fun generatePreview(message: String, maxLength: Int = 100): String {
        val cleaned = message
            .replace(Regex("[\\n\\r]+"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

        return if (cleaned.length <= maxLength) {
            cleaned
        } else {
            cleaned.take(maxLength - 3) + "..."
        }
    }
}
