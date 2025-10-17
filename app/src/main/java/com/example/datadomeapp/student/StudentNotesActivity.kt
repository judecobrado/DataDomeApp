package com.example.datadomeapp.student

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.example.datadomeapp.R
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class StudentNotesActivity : AppCompatActivity() {

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()
    private lateinit var notesContainer: LinearLayout
    private lateinit var btnAddNote: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_student_notes)

        notesContainer = findViewById(R.id.notesContainer)
        btnAddNote = findViewById(R.id.btnAddNote)

        loadNotes()

        btnAddNote.setOnClickListener { showAddEditNoteDialog() }
    }

    private fun showAddEditNoteDialog(noteId: String? = null, oldTitle: String? = null, oldContent: String? = null) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_note, null)
        val etTitle = dialogView.findViewById<EditText>(R.id.etTitle)
        val etContent = dialogView.findViewById<EditText>(R.id.etContent)

        etTitle.setText(oldTitle)
        etContent.setText(oldContent)

        AlertDialog.Builder(this)
            .setTitle(if (noteId == null) "Add Note" else "Edit Note")
            .setView(dialogView)
            .setPositiveButton(if (noteId == null) "Add" else "Update") { _, _ ->
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
                        .addOnSuccessListener { loadNotes() }
                } else {
                    db.collection("students").document(userId)
                        .collection("notes").document(noteId)
                        .update(noteMap as Map<String, Any>)
                        .addOnSuccessListener { loadNotes() }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun loadNotes() {
        val userId = auth.currentUser?.uid ?: return
        notesContainer.removeAllViews()

        db.collection("students").document(userId)
            .collection("notes")
            .orderBy("timestamp")
            .get()
            .addOnSuccessListener { result ->
                for (doc in result) {
                    val noteId = doc.id
                    val title = doc.getString("title") ?: ""
                    val content = doc.getString("content") ?: ""

                    val container = LinearLayout(this)
                    container.orientation = LinearLayout.VERTICAL
                    container.setPadding(20, 20, 20, 20)
                    container.setBackgroundColor(0xFFEFEFEF.toInt())
                    val lp = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                    lp.setMargins(0,0,0,16)
                    container.layoutParams = lp

                    val tvTitle = TextView(this)
                    tvTitle.text = "Title: $title"
                    tvTitle.textSize = 18f

                    val tvContent = TextView(this)
                    tvContent.text = content

                    val btnEdit = Button(this)
                    btnEdit.text = "Edit"
                    val btnDelete = Button(this)
                    btnDelete.text = "Delete"

                    val btnLayout = LinearLayout(this)
                    btnLayout.orientation = LinearLayout.HORIZONTAL
                    btnLayout.addView(btnEdit)
                    btnLayout.addView(btnDelete)

                    container.addView(tvTitle)
                    container.addView(tvContent)
                    container.addView(btnLayout)

                    notesContainer.addView(container)

                    btnEdit.setOnClickListener {
                        showAddEditNoteDialog(noteId, title, content)
                    }

                    btnDelete.setOnClickListener {
                        db.collection("students").document(userId)
                            .collection("notes").document(noteId)
                            .delete()
                            .addOnSuccessListener { loadNotes() }
                    }
                }
            }
    }
}
