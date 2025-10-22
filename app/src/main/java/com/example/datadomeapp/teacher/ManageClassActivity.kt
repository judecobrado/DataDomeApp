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
                        // FIX: Gamitin ang document ID bilang assignmentNo (Firestore Key).
                        classList.add(assignment.copy(assignmentNo = document.id))
                    }
                }

                if (classList.isEmpty()) {
                    Toast.makeText(this, "You currently have no classes assigned.", Toast.LENGTH_LONG).show()
                }

                // 🟢 FIX: Gumamit ng DALAWA (2) click listeners para tugma sa ClassAdapter constructor
                classAdapter = ClassAdapter(
                    classList,
                    // 1st Listener: detailClickListener (for View Details)
                    detailClickListener = { selectedClass ->
                        navigateToClassDetails(selectedClass)
                    },
                    // 2nd Listener: setLinkClickListener (for Set Link)
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

        // Kukunin ang sectionBlock para sa display
        val primarySlot = assignment.scheduleSlots.values.firstOrNull()
        val sectionBlock = primarySlot?.sectionBlock ?: "N/A"

        val onlineLink = assignment.onlineClassLink ?: ""
        val assignmentNo = assignment.assignmentNo // Firestore Key

        // I-pasa ang lahat ng critical data
        intent.putExtra("ASSIGNMENT_ID", assignmentNo)
        intent.putExtra("CLASS_NAME", "${assignment.subjectTitle} - $sectionBlock")
        intent.putExtra("SUBJECT_CODE", assignment.subjectCode)
        intent.putExtra("ONLINE_CLASS_LINK", onlineLink)
        startActivity(intent)
    }

    private fun showSetOnlineClassDialog(assignment: ClassAssignment) {
        val assignmentNo = assignment.assignmentNo
        val currentLink = assignment.onlineClassLink ?: ""

        val primarySlot = assignment.scheduleSlots.values.firstOrNull()
        val sectionBlock = primarySlot?.sectionBlock ?: assignment.subjectCode

        val builder = AlertDialog.Builder(this)
        builder.setTitle("Set Online Link for $sectionBlock")

        val input = EditText(this)
        input.hint = "Paste Meet / Zoom Link dito"
        input.setText(currentLink)
        builder.setView(input)

        builder.setNeutralButton("Generate Link (Meet)") { dialog, _ ->
            openGoogleMeetForCreation()
        }

        builder.setPositiveButton("Save") { dialog, _ ->
            val newLink = input.text.toString().trim()
            if (newLink.isNotEmpty()) {
                setOnlineClassLink(assignmentNo, newLink)
            } else {
                Toast.makeText(this, "Link cannot be empty.", Toast.LENGTH_SHORT).show()
            }
        }

        builder.setNegativeButton("Cancel") { dialog, _ -> dialog.cancel() }
        builder.show()
    }

    private fun openGoogleMeetForCreation() {
        val meetPackageName = "com.google.android.apps.tachyon"

        val launchIntent = packageManager.getLaunchIntentForPackage(meetPackageName)

        if (launchIntent != null) {
            startActivity(launchIntent)
            Toast.makeText(this, "Opening Google Meet. Please create a new meeting and copy the link.", Toast.LENGTH_LONG).show()
        } else {
            try {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$meetPackageName")))
            } catch (e: android.content.ActivityNotFoundException) {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$meetPackageName")))
            }
            Toast.makeText(this, "Please install Google Meet app first.", Toast.LENGTH_LONG).show()
        }
    }

    private fun setOnlineClassLink(assignmentNo: String, link: String) {
        val updateData = hashMapOf<String, Any>(
            "onlineClassLink" to link
        )

        firestore.collection("classAssignments").document(assignmentNo)
            .update(updateData)
            .addOnSuccessListener {
                Toast.makeText(this, "Online Class Link successfully updated! 🔗", Toast.LENGTH_LONG).show()
                loadAssignedClasses()
            }
            .addOnFailureListener { e ->
                Log.e("ManageClasses", "Error updating online class link: $e")
                Toast.makeText(this, "Failed to update link: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }
}