package com.example.datadomeapp.repository

import android.net.Uri
import com.example.datadomeapp.models.Assignment
import com.example.datadomeapp.models.StudentExtension
import com.example.datadomeapp.models.Submission
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.storage.FirebaseStorage
import java.util.*

object AssignmentRepository {

    private val db = FirebaseFirestore.getInstance()
    private val storage = FirebaseStorage.getInstance()

    // -------------------------------------------------------------
    // 🔹 CREATE ASSIGNMENT (Teacher)
    // -------------------------------------------------------------
    fun createAssignment(assignment: Assignment, callback: (Boolean, String?) -> Unit) {
        if (assignment.id.isEmpty()) {
            callback(false, "Assignment ID missing.")
            return
        }

        db.collection("assignments")
            .document(assignment.id)
            .set(assignment)
            .addOnSuccessListener { callback(true, null) }
            .addOnFailureListener { e -> callback(false, e.message) }
    }

    // -------------------------------------------------------------
    // 🔹 UPDATE ASSIGNMENT (Teacher)
    // -------------------------------------------------------------
    fun updateAssignment(assignment: Assignment, callback: (Boolean, String?) -> Unit) {
        if (assignment.id.isEmpty()) {
            callback(false, "Assignment ID missing.")
            return
        }

        val updates = hashMapOf<String, Any>(
            "title" to assignment.title,
            "instructions" to assignment.instructions,
            "dueDateMillis" to assignment.dueDateMillis
        )

        // Only update fileUrl if it's not null
        assignment.fileUrl?.let { fileUrl ->
            updates["fileUrl"] = fileUrl
        }

        // Update student extensions if they exist
        if (assignment.studentExtensions.isNotEmpty()) {
            updates["studentExtensions"] = assignment.studentExtensions
        }

        db.collection("assignments")
            .document(assignment.id)
            .update(updates)
            .addOnSuccessListener { callback(true, null) }
            .addOnFailureListener { e -> callback(false, e.message) }
    }

    // -------------------------------------------------------------
    // 🔹 GET ASSIGNMENT BY ID
    // -------------------------------------------------------------
    fun getAssignmentById(assignmentId: String, callback: (Assignment?) -> Unit) {
        db.collection("assignments")
            .document(assignmentId)
            .get()
            .addOnSuccessListener { doc ->
                if (doc.exists()) {
                    val assignment = doc.toObject(Assignment::class.java)
                    assignment?.id = doc.id
                    callback(assignment)
                } else {
                    callback(null)
                }
            }
            .addOnFailureListener { e ->
                callback(null)
            }
    }

    // -------------------------------------------------------------
    // 🔹 FETCH ASSIGNMENTS FOR CLASS
    // -------------------------------------------------------------
    fun getAssignmentsForClass(
        classId: String,
        callback: (success: Boolean, snapshot: QuerySnapshot?, error: String?) -> Unit
    ) {
        db.collection("assignments")
            .whereEqualTo("classId", classId)
            .get()
            .addOnSuccessListener { snapshot ->
                callback(true, snapshot, null)
            }
            .addOnFailureListener { e ->
                callback(false, null, e.message)
            }
    }

    // -------------------------------------------------------------
    // 🔹 DELETE ASSIGNMENT (Teacher)
    // -------------------------------------------------------------
    fun deleteAssignment(assignmentId: String, callback: (Boolean, String?) -> Unit) {
        db.collection("assignments")
            .document(assignmentId)
            .delete()
            .addOnSuccessListener { callback(true, null) }
            .addOnFailureListener { e -> callback(false, e.message) }
    }

    // -------------------------------------------------------------
    // 🔹 DELETE ASSIGNMENT WITH SUBMISSIONS (Teacher)
    // -------------------------------------------------------------
    fun deleteAssignmentWithSubmissions(assignmentId: String, callback: (Boolean, String?) -> Unit) {
        // First, delete all submissions for this assignment
        db.collection("submissions")
            .whereEqualTo("assignmentId", assignmentId)
            .get()
            .addOnSuccessListener { submissionsSnapshot ->
                val batch = db.batch()

                // Delete all submission documents
                for (doc in submissionsSnapshot.documents) {
                    batch.delete(doc.reference)
                }

                // Delete the assignment
                val assignmentRef = db.collection("assignments").document(assignmentId)
                batch.delete(assignmentRef)

                // Commit the batch
                batch.commit()
                    .addOnSuccessListener {
                        callback(true, null)
                    }
                    .addOnFailureListener { e ->
                        callback(false, "Failed to delete assignment and submissions: ${e.message}")
                    }
            }
            .addOnFailureListener { e ->
                callback(false, "Failed to fetch submissions: ${e.message}")
            }
    }

    // -------------------------------------------------------------
    // 🔹 UPDATE SUBMISSION COUNT
    // -------------------------------------------------------------
    fun updateSubmissionCount(assignmentId: String, callback: (Boolean, String?) -> Unit) {
        // Count submissions for this assignment
        db.collection("submissions")
            .whereEqualTo("assignmentId", assignmentId)
            .get()
            .addOnSuccessListener { snapshot ->
                val submissionCount = snapshot.documents.size

                // Update the assignment with new count
                db.collection("assignments")
                    .document(assignmentId)
                    .update("submissionCount", submissionCount)
                    .addOnSuccessListener { callback(true, null) }
                    .addOnFailureListener { e -> callback(false, e.message) }
            }
            .addOnFailureListener { e ->
                callback(false, "Failed to count submissions: ${e.message}")
            }
    }

    // -------------------------------------------------------------
    // 🔹 EXTEND DUE DATE FOR STUDENT
    // -------------------------------------------------------------
    fun extendDueDateForStudent(
        assignmentId: String,
        studentId: String,
        studentName: String,
        extendedDueDate: Long,
        reason: String,
        grantedBy: String,
        callback: (Boolean, String?) -> Unit
    ) {
        val extension = StudentExtension(
            studentId = studentId,
            studentName = studentName,
            extendedDueDate = extendedDueDate,
            reason = reason,
            grantedAt = System.currentTimeMillis(),
            grantedBy = grantedBy
        )

        val updateData = mapOf(
            "studentExtensions.$studentId" to extension
        )

        db.collection("assignments")
            .document(assignmentId)
            .update(updateData)
            .addOnSuccessListener { callback(true, null) }
            .addOnFailureListener { e -> callback(false, e.message) }
    }

    // -------------------------------------------------------------
    // 🔹 REMOVE STUDENT EXTENSION
    // -------------------------------------------------------------
    fun removeStudentExtension(
        assignmentId: String,
        studentId: String,
        callback: (Boolean, String?) -> Unit
    ) {
        val updateData = mapOf(
            "studentExtensions.$studentId" to FieldValue.delete()
        )

        db.collection("assignments")
            .document(assignmentId)
            .update(updateData)
            .addOnSuccessListener { callback(true, null) }
            .addOnFailureListener { e -> callback(false, e.message) }
    }

    // -------------------------------------------------------------
    // 🔹 REOPEN SUBMISSION FOR STUDENT (DELETE OLD SUBMISSION)
    // -------------------------------------------------------------
    fun reopenSubmissionForStudent(
        submissionId: String,
        callback: (Boolean, String?) -> Unit
    ) {
        // First, get the current submission to get assignmentId
        db.collection("submissions")
            .document(submissionId)
            .get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val oldSubmission = document.toObject(Submission::class.java)
                    if (oldSubmission != null) {
                        val assignmentId = oldSubmission.assignmentId

                        // DELETE the old submission completely
                        db.collection("submissions")
                            .document(submissionId)
                            .delete()
                            .addOnSuccessListener {
                                // Update submission count after deletion
                                updateSubmissionCount(assignmentId) { countSuccess, countError ->
                                    callback(true, null)
                                }
                            }
                            .addOnFailureListener { e ->
                                callback(false, "Failed to delete old submission: ${e.message}")
                            }
                    } else {
                        callback(false, "Failed to parse old submission data")
                    }
                } else {
                    callback(false, "Submission not found")
                }
            }
            .addOnFailureListener { e ->
                callback(false, "Failed to fetch submission: ${e.message}")
            }
    }

    // -------------------------------------------------------------
    // 🔹 GET STUDENT EXTENSIONS FOR ASSIGNMENT
    // -------------------------------------------------------------
    fun getStudentExtensions(assignmentId: String, callback: (Map<String, StudentExtension>?) -> Unit) {
        db.collection("assignments")
            .document(assignmentId)
            .get()
            .addOnSuccessListener { doc ->
                val assignment = doc.toObject(Assignment::class.java)
                callback(assignment?.studentExtensions)
            }
            .addOnFailureListener {
                callback(null)
            }
    }

    // -------------------------------------------------------------
    // 🔹 SUBMIT ASSIGNMENT (Student) - PROPER SUBMISSION
    // -------------------------------------------------------------
    fun submitAssignment(
        submission: Submission,
        fileUri: Uri?,
        callback: (Boolean, String?) -> Unit
    ) {
        // ✅ Validate required fields
        if (submission.classId.isEmpty() ||
            submission.assignmentId.isEmpty() ||
            submission.studentId.isEmpty()
        ) {
            callback(false, "Missing classId, assignmentId, or studentId")
            return
        }

        // Check if file is provided
        if (fileUri == null) {
            callback(false, "Please select a file to submit")
            return
        }

        submission.submittedAt = System.currentTimeMillis()
        submission.status = "submitted"

        // ✅ Upload file first
        val path = "submission_files/${submission.classId}/${submission.assignmentId}/${UUID.randomUUID()}"
        val ref = storage.reference.child(path)

        ref.putFile(fileUri)
            .addOnSuccessListener {
                ref.downloadUrl.addOnSuccessListener { uri ->
                    submission.fileUrl = uri.toString()
                    saveSubmissionToFirestore(submission, callback)
                }.addOnFailureListener { e ->
                    callback(false, "Failed to get file URL: ${e.message}")
                }
            }
            .addOnFailureListener { e ->
                callback(false, "File upload failed: ${e.message}")
            }
    }

    // -------------------------------------------------------------
    // 🔹 SAVE SUBMISSION TO FIRESTORE (Helper)
    // -------------------------------------------------------------
    private fun saveSubmissionToFirestore(
        submission: Submission,
        callback: (Boolean, String?) -> Unit
    ) {
        val docId = submission.id.ifEmpty { UUID.randomUUID().toString() }
        submission.id = docId

        db.collection("submissions")
            .document(docId)
            .set(submission)
            .addOnSuccessListener {
                // Update submission count after successful submission
                updateSubmissionCount(submission.assignmentId) { success, error ->
                    if (!success) {
                        println("Failed to update submission count: $error")
                    }
                }
                callback(true, null)
            }
            .addOnFailureListener { e -> callback(false, e.message) }
    }

    // -------------------------------------------------------------
    // 🔹 GET SUBMISSIONS FOR ASSIGNMENT (Teacher)
    // -------------------------------------------------------------
    fun getSubmissionsForAssignment(
        assignmentId: String,
        callback: (Boolean, QuerySnapshot?, String?) -> Unit
    ) {
        db.collection("submissions")
            .whereEqualTo("assignmentId", assignmentId)
            .get()
            .addOnSuccessListener { snapshot ->
                callback(true, snapshot, null)
            }
            .addOnFailureListener { e ->
                callback(false, null, e.message)
            }
    }

    // -------------------------------------------------------------
    // 🔹 GET SUBMISSION BY STUDENT AND ASSIGNMENT
    // -------------------------------------------------------------
    fun getSubmissionByStudentAndAssignment(
        studentId: String,
        assignmentId: String,
        callback: (Submission?) -> Unit
    ) {
        db.collection("submissions")
            .whereEqualTo("studentId", studentId)
            .whereEqualTo("assignmentId", assignmentId)
            .get()
            .addOnSuccessListener { snapshot ->
                if (!snapshot.isEmpty && snapshot.documents.isNotEmpty()) {
                    val submission = snapshot.documents[0].toObject(Submission::class.java)
                    submission?.id = snapshot.documents[0].id
                    callback(submission)
                } else {
                    callback(null)
                }
            }
            .addOnFailureListener { e ->
                callback(null)
            }
    }

    // -------------------------------------------------------------
    // 🔹 GRADE SUBMISSION (Teacher)
    // -------------------------------------------------------------
    fun gradeSubmission(
        submissionId: String,
        grade: Double,
        feedback: String,
        callback: (Boolean, String?) -> Unit
    ) {
        val updates = mapOf(
            "grade" to grade,
            "feedback" to feedback,
            "gradedAt" to System.currentTimeMillis(),
            "status" to "graded"
        )

        db.collection("submissions")
            .document(submissionId)
            .update(updates)
            .addOnSuccessListener { callback(true, null) }
            .addOnFailureListener { e -> callback(false, e.message) }
    }

    // -------------------------------------------------------------
    // 🔹 DELETE SUBMISSION FILE FROM STORAGE
    // -------------------------------------------------------------
    fun deleteSubmissionFile(fileUrl: String, callback: (Boolean, String?) -> Unit) {
        try {
            val fileRef = storage.getReferenceFromUrl(fileUrl)
            fileRef.delete()
                .addOnSuccessListener { callback(true, null) }
                .addOnFailureListener { e -> callback(false, e.message) }
        } catch (e: Exception) {
            callback(false, "Invalid file URL: ${e.message}")
        }
    }

    // -------------------------------------------------------------
    // 🔹 DELETE ASSIGNMENT FILE FROM STORAGE
    // -------------------------------------------------------------
    fun deleteAssignmentFile(fileUrl: String, callback: (Boolean, String?) -> Unit) {
        try {
            val fileRef = storage.getReferenceFromUrl(fileUrl)
            fileRef.delete()
                .addOnSuccessListener { callback(true, null) }
                .addOnFailureListener { e -> callback(false, e.message) }
        } catch (e: Exception) {
            callback(false, "Invalid file URL: ${e.message}")
        }
    }
}