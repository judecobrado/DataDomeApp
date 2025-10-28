package com.example.datadomeapp.models

import com.google.firebase.database.PropertyName

data class Quiz(
    val quizId: String = "",
    val assignmentId: String = "",
    val teacherUid: String = "",
    val title: String = "",
    val questions: List<Question> = emptyList(),
    @get:JvmName("isPublished")
    @set:JvmName("setPublished")
    @get:PropertyName("published")
    @set:PropertyName("published")
    var isPublished: Boolean = false,
    val scheduledDateTime: Long = 0L,
    val scheduledEndDateTime: Long = 0L,
    val totalPoints: Int = 0
)


data class Question(
    val questionText: String = "",
    val type: String = "", // "TF", "MC", "MATCHING"

    // Add all fields from all subclasses as nullable properties
    val answer: Boolean? = null,
    val options: List<String>? = null,
    val correctAnswerIndex: Int? = null,
    val matches: List<String>? = null
)

data class ClassDisplayDetails(
    val sectionId: String,
    val subjectTitle: String
)

data class QuizAttempt(
    val attemptId: String = "",
    val studentId: String = "",
    val quizId: String = "",
    val score: Int? = null,           // Null bago ma-submit
    val totalPoints: Int? = 0,
    val answers: Map<Int, Any> = emptyMap(), // Mga sagot ng estudyante
    val startTime: Long = 0L,
    val endTime: Long? = null,
    val violationCount: Int = 0,    // Bilang ng beses na umalis sa screen
    val status: String = "Pending"  // (Pending, Submitted, Disqualified)
)