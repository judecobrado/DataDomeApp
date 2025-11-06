package com.example.datadomeapp.models

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * Represents a complete Quiz object, now parcelable for fast data transfer in Android components.
 */
@Parcelize
data class Quiz(
    val quizId: String = "",
    val assignmentId: String = "",
    val teacherUid: String = "",
    val title: String = "",
    val questions: List<Question> = emptyList(),
    var isPublished: Boolean = false,
    val scheduledDateTime: Long = 0L,
    val scheduledEndDateTime: Long = 0L,
    val description: String = "",
    val quizType: String = "Quiz",
    val academicTerm: String? = null,
    val academicYear: String? = null,
    val semester: String? = null
) : Parcelable


/**
 * Sealed class representing different types of questions, all parcelable.
 */
@Parcelize
sealed class Question : Parcelable {
    abstract val questionText: String
    abstract val type: String

    @Parcelize
    data class TrueFalse(
        override var questionText: String = "", // default value
        var answer: Boolean = false
    ) : Question() {
        override val type = "TF"
    }

    @Parcelize
    data class MultipleChoice(
        override val questionText: String,
        val options: List<String>,
        val correctAnswerIndex: Int
    ) : Question() {
        override val type = "MC"
    }

    @Parcelize
    data class Matching(
        override val questionText: String,
        val options: List<String>, // [ "Paris", "Tokyo" ]
        val matches: List<String>
    ) : Question() {
        override val type = "MATCHING"
    }
}

/**
 * Display details for a class section, now parcelable.
 */
@Parcelize
data class ClassDisplayDetails(
    val sectionId: String,
    val subjectTitle: String
) : Parcelable

