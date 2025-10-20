package com.example.datadomeapp.teacher

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.datadomeapp.R
import com.example.datadomeapp.models.ClassAssignment
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore


class ManageClassesActivity : AppCompatActivity() {

    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()
    private lateinit var recyclerView: RecyclerView
    private lateinit var classAdapter: ClassAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.teacher_classes_assign)

        recyclerView = findViewById(R.id.recyclerViewClasses)
        recyclerView.layoutManager = LinearLayoutManager(this)

        loadAssignedClasses()
    }

    private fun loadAssignedClasses() {
        val currentTeacherUid = auth.currentUser?.uid

        if (currentTeacherUid == null) {
            Toast.makeText(this, "Error: Teacher not logged in.", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        firestore.collection("classAssignments")
            .whereEqualTo("teacherUid", currentTeacherUid)
            .get()
            .addOnSuccessListener { snapshot ->
                val classList = mutableListOf<ClassAssignment>()

                for (document in snapshot.documents) {
                    val assignment = document.toObject(ClassAssignment::class.java)
                    if (assignment != null) {
                        // Tiyakin na ang assignmentId ay Document ID
                        classList.add(assignment.copy(assignmentId = document.id))
                    }
                }

                if (classList.isEmpty()) {
                    Toast.makeText(this, "You currently have no classes assigned.", Toast.LENGTH_LONG).show()
                }

                classAdapter = ClassAdapter(
                    classList,
                    // 1st Listener (detailClickListener)
                    detailClickListener = { selectedClass ->
                        Toast.makeText(this, "Selected: ${selectedClass.subjectCode} - ${selectedClass.sectionName}", Toast.LENGTH_SHORT).show()
                        navigateToClassDetails(selectedClass)
                    },
                    // 2nd Listener (setLinkClickListener) - NAKAPALOOB NA ANG CALL SA BAGONG FUNCTION
                    setLinkClickListener = { selectedClass ->
                        showSetOnlineClassDialog(selectedClass)
                    }
                )
                recyclerView.adapter = classAdapter
            }
            .addOnFailureListener { e ->
                Log.e("ManageClasses", "Error loading assigned classes: $e")
                Toast.makeText(this, "Failed to load classes: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }

    private fun navigateToClassDetails(assignment: ClassAssignment) {
        val intent = Intent(this, ClassDetailsActivity::class.java)
        // I-pasa ang lahat ng critical data
        intent.putExtra("ASSIGNMENT_ID", assignment.assignmentId)
        intent.putExtra("CLASS_NAME", "${assignment.subjectTitle} - ${assignment.sectionName}")
        intent.putExtra("SUBJECT_CODE", assignment.subjectCode)
        // ✅ NEW: Ipinapasa ang online class link para sa details screen
        intent.putExtra("ONLINE_CLASS_LINK", assignment.onlineClassLink)
        startActivity(intent)
    }

    // --- BAGONG FUNCTION: ONLINE CLASS LINK SETUP DIALOG ---

    private fun showSetOnlineClassDialog(assignment: ClassAssignment) {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Set Online Link for ${assignment.sectionName}")

        val input = EditText(this)
        input.hint = "Paste Meet / Zoom Link dito"
        input.setText(assignment.onlineClassLink)
        builder.setView(input)

        // Option: BUTTON para tulungan ang user gumawa ng link
        builder.setNeutralButton("Generate Link (Meet)") { dialog, _ ->
            // I-re-redirect sa Google Meet app
            openGoogleMeetForCreation()
        }

        // Existing: Save button
        builder.setPositiveButton("Save") { dialog, _ ->
            val newLink = input.text.toString().trim()
            if (newLink.isNotEmpty()) {
                setOnlineClassLink(assignment.assignmentId, newLink)
            } else {
                Toast.makeText(this, "Link cannot be empty.", Toast.LENGTH_SHORT).show()
            }
        }

        builder.setNegativeButton("Cancel") { dialog, _ -> dialog.cancel() }
        builder.show()
    }

    private fun openGoogleMeetForCreation() {
        // Gagamitin ang package name ng unified Google Meet app (dating Duo/Meet).
        val meetPackageName = "com.google.android.apps.tachyon"

        val launchIntent = packageManager.getLaunchIntentForPackage(meetPackageName)

        if (launchIntent != null) {
            // May Meet app, i-launch ito.
            startActivity(launchIntent)
            Toast.makeText(this, "Opening Google Meet. Please create a new meeting and copy the link.", Toast.LENGTH_LONG).show()
        } else {
            // Walang Meet app, i-redirect sa Play Store.
            try {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$meetPackageName")))
            } catch (e: android.content.ActivityNotFoundException) {
                // Fallback kung walang Play Store app.
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$meetPackageName")))
            }
            Toast.makeText(this, "Please install Google Meet app first.", Toast.LENGTH_LONG).show()
        }
    }

    private fun setOnlineClassLink(assignmentId: String, link: String) {
        // Ang data na i-a-update
        val updateData = hashMapOf<String, Any>(
            "onlineClassLink" to link
        )

        firestore.collection("classAssignments").document(assignmentId)
            .update(updateData)
            .addOnSuccessListener {
                Toast.makeText(this, "Online Class Link successfully updated! 🔗", Toast.LENGTH_LONG).show()
                // I-re-load ang listahan para makita ang pagbabago sa UI
                loadAssignedClasses()
            }
            .addOnFailureListener { e ->
                Log.e("ManageClasses", "Error updating online class link: $e")
                Toast.makeText(this, "Failed to update link: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }
}