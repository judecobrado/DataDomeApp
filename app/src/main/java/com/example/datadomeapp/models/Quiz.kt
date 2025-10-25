package com.example.datadomeapp.models

data class Quiz(
    val quizId: String = "",
    val assignmentId: String = "",        // Para i-link sa class
    val teacherUid: String = "",
    val title: String = "",
    val questions: List<Question> = emptyList(),
    var isPublished: Boolean = false,     // Publish / Unpublish
    var scheduledDateTime: Long = 0L      // Epoch millis ng quiz taking time
)


sealed class Question {
    abstract val questionText: String
    abstract val type: String

    data class TrueFalse(
        override val questionText: String,
        val answer: Boolean
    ) : Question() {
        override val type = "TF"
    }

    data class MultipleChoice(
        override val questionText: String,
        val options: List<String>,
        val correctAnswerIndex: Int
    ) : Question() {
        override val type = "MC"
    }

    data class Matching(
        override val questionText: String,
        val options: List<String>, // [ "Paris", "Tokyo" ]
        val matches: List<String>
    ) : Question() {
        override val type = "MATCHING"
    }
}

