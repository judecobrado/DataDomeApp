package com.example.datadomeapp.student

import android.app.AlertDialog
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.datadomeapp.R
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.*

class StudentNotesActivity : AppCompatActivity() {

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()
    private lateinit var notesContainer: LinearLayout
    private lateinit var btnAddNote: MaterialButton
    private lateinit var emptyState: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.student_notes)

        notesContainer = findViewById(R.id.notesContainer)
        btnAddNote = findViewById(R.id.btnAddNote)
        emptyState = findViewById(R.id.emptyState)

        loadNotes()

        btnAddNote.setOnClickListener { showAddEditNoteDialog() }
    }

    private fun showAddEditNoteDialog(noteId: String? = null, oldTitle: String? = null, oldContent: String? = null) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.student_addnote_dialog, null)
        val etTitle = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etTitle)
        val etContent = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etContent)

        etTitle.setText(oldTitle)
        etContent.setText(oldContent)

        MaterialAlertDialogBuilder(this)
            .setTitle(if (noteId == null) "Add New Note" else "Edit Note")
            .setView(dialogView)
            .setPositiveButton(if (noteId == null) "Add" else "Update") { dialog, _ ->
                val title = etTitle.text.toString().trim()
                val content = etContent.text.toString().trim()
                val userId = auth.currentUser?.uid ?: return@setPositiveButton

                if (title.isEmpty() || content.isEmpty()) {
                    Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                val noteMap = hashMapOf(
                    "studentId" to userId,
                    "title" to title,
                    "content" to content,
                    "timestamp" to System.currentTimeMillis()
                )

                if (noteId == null) {
                    db.collection("students").document(userId)
                        .collection("notes")
                        .add(noteMap)
                        .addOnSuccessListener {
                            loadNotes()
                            Toast.makeText(this, "Note added successfully", Toast.LENGTH_SHORT).show()
                        }
                        .addOnFailureListener {
                            Toast.makeText(this, "Failed to add note", Toast.LENGTH_SHORT).show()
                        }
                } else {
                    db.collection("students").document(userId)
                        .collection("notes").document(noteId)
                        .update(noteMap as Map<String, Any>)
                        .addOnSuccessListener {
                            loadNotes()
                            Toast.makeText(this, "Note updated successfully", Toast.LENGTH_SHORT).show()
                        }
                        .addOnFailureListener {
                            Toast.makeText(this, "Failed to update note", Toast.LENGTH_SHORT).show()
                        }
                }
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    private fun loadNotes() {
        val userId = auth.currentUser?.uid ?: return
        notesContainer.removeAllViews()

        db.collection("students").document(userId)
            .collection("notes")
            .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .get()
            .addOnSuccessListener { result ->
                if (result.isEmpty) {
                    showEmptyState()
                    return@addOnSuccessListener
                }

                hideEmptyState()

                for (doc in result) {
                    val noteId = doc.id
                    val title = doc.getString("title") ?: ""
                    val content = doc.getString("content") ?: ""
                    val timestamp = doc.getLong("timestamp") ?: System.currentTimeMillis()

                    createNoteCard(noteId, title, content, timestamp)
                }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to load notes", Toast.LENGTH_SHORT).show()
                showEmptyState()
            }
    }

    private fun createNoteCard(noteId: String, title: String, content: String, timestamp: Long) {
        val noteCard = MaterialCardView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 0, 0, 16)
            }
            radius = 16f
            elevation = 4f
            setCardBackgroundColor(ContextCompat.getColor(this@StudentNotesActivity, R.color.white))
        }

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20, 20, 20, 20)
        }

        // Title
        val tvTitle = TextView(this).apply {
            text = title
            setTextColor(ContextCompat.getColor(this@StudentNotesActivity, R.color.black))
            textSize = 18f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 0, 0, 8)
            }
        }

        // Content (limited to 3 lines)
        val tvContent = TextView(this).apply {
            text = content
            setTextColor(ContextCompat.getColor(this@StudentNotesActivity, R.color.text_secondary))
            textSize = 14f
            maxLines = 3
            ellipsize = android.text.TextUtils.TruncateAt.END
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 0, 0, 12)
            }
        }

        // Timestamp
        val tvTimestamp = TextView(this).apply {
            text = formatTimestamp(timestamp)
            setTextColor(ContextCompat.getColor(this@StudentNotesActivity, R.color.text_secondary))
            textSize = 12f
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        // Button Layout
        val btnLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 16, 0, 0)
            }
        }

        // Edit Button
        val btnEdit = MaterialButton(this).apply {
            text = "Edit"
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                weight = 1f
                marginEnd = 8
            }
            setBackgroundColor(ContextCompat.getColor(this@StudentNotesActivity, R.color.primary_red))
            setTextColor(ContextCompat.getColor(this@StudentNotesActivity, R.color.white))
            cornerRadius = 8
            elevation = 0f
        }

        // Delete Button
        val btnDelete = MaterialButton(this).apply {
            text = "Delete"
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                weight = 1f
                marginStart = 8
            }
            setBackgroundColor(ContextCompat.getColor(this@StudentNotesActivity, R.color.gray_light))
            setTextColor(ContextCompat.getColor(this@StudentNotesActivity, R.color.text_secondary))
            cornerRadius = 8
            elevation = 0f
        }

        btnLayout.addView(btnEdit)
        btnLayout.addView(btnDelete)

        container.addView(tvTitle)
        container.addView(tvContent)
        container.addView(tvTimestamp)
        container.addView(btnLayout)

        noteCard.addView(container)
        notesContainer.addView(noteCard)

        // Click listeners
        btnEdit.setOnClickListener {
            showAddEditNoteDialog(noteId, title, content)
        }

        btnDelete.setOnClickListener {
            showDeleteConfirmationDialog(noteId)
        }

        // Card click to view full content
        noteCard.setOnClickListener {
            showNoteDetailDialog(title, content, timestamp)
        }
    }

    private fun showDeleteConfirmationDialog(noteId: String) {
        MaterialAlertDialogBuilder(this)
            .setTitle("Delete Note")
            .setMessage("Are you sure you want to delete this note?")
            .setPositiveButton("Delete") { dialog, _ ->
                val userId = auth.currentUser?.uid ?: return@setPositiveButton
                db.collection("students").document(userId)
                    .collection("notes").document(noteId)
                    .delete()
                    .addOnSuccessListener {
                        loadNotes()
                        Toast.makeText(this, "Note deleted successfully", Toast.LENGTH_SHORT).show()
                    }
                    .addOnFailureListener {
                        Toast.makeText(this, "Failed to delete note", Toast.LENGTH_SHORT).show()
                    }
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    private fun showNoteDetailDialog(title: String, content: String, timestamp: Long) {
        MaterialAlertDialogBuilder(this)
            .setTitle(title)
            .setMessage("$content\n\n\nCreated: ${formatTimestamp(timestamp)}")
            .setPositiveButton("Close") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    private fun formatTimestamp(timestamp: Long): String {
        val sdf = SimpleDateFormat("MMM dd, yyyy 'at' hh:mm a", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }

    private fun showEmptyState() {
        emptyState.visibility = View.VISIBLE
        notesContainer.visibility = View.GONE
    }

    private fun hideEmptyState() {
        emptyState.visibility = View.GONE
        notesContainer.visibility = View.VISIBLE
    }
}