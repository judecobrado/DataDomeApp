package com.example.datadomeapp.models

data class Quiz(
    val quizId: String = "",
    val assignmentId: String = "",
    val teacherUid: String = "",
    val title: String = "",
    val questions: List<Question> = emptyList(),
    var isPublished: Boolean = false,
    val scheduledDateTime: Long = 0L,
    val scheduledEndDateTime: Long = 0L
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

data class ClassDisplayDetails(
    val sectionId: String,
    val subjectTitle: String
)

