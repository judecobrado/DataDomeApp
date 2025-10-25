package com.example.datadomeapp.utils

object StringUtils {

    private val exceptions = setOf(
        "the", "on", "in", "of", "and", "a", "an", "for", "to", "at", "by", "from"
    )

    fun toTitleCaseWithExceptions(input: String): String {
        return input
            .split(" ")
            .mapIndexed { index, word ->
                if (index == 0 || word.lowercase() !in exceptions) {
                    word.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
                } else {
                    word.lowercase()
                }
            }
            .joinToString(" ")
    }
}
