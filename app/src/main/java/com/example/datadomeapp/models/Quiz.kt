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
    data class TrueFalse(
        val questionText: String = "",
        val answer: Boolean = true
    ) : Question()

    data class Matching(
        val questionText: String = "",
        val options: List<String> = emptyList(),  // left side
        val matches: List<String> = emptyList()   // right side
    ) : Question()
}
