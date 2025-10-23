package com.example.datadomeapp.teacher

import android.content.ActivityNotFoundException
import android.content.DialogInterface
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
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
                    if (assignment != null) classList.add(assignment.copy(assignmentNo = document.id))
                }

                if (classList.isEmpty()) {
                    Toast.makeText(this, "You currently have no classes assigned.", Toast.LENGTH_LONG).show()
                }

                classAdapter = ClassAdapter(
                    classList,
                    detailClickListener = { navigateToClassDetails(it) },
                    setLinkClickListener = { showSetOnlineClassDialog(it) }
                )
                recyclerView.adapter = classAdapter
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to load classes: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }

    private fun navigateToClassDetails(assignment: ClassAssignment) {
        val intent = Intent(this, ClassDetailsActivity::class.java)
        val primarySlot = assignment.scheduleSlots.values.firstOrNull()
        val sectionBlock = primarySlot?.sectionBlock ?: "A"
        intent.putExtra("ASSIGNMENT_ID", assignment.assignmentNo)
        intent.putExtra("CLASS_NAME", "${assignment.subjectTitle} - $sectionBlock")
        intent.putExtra("SUBJECT_CODE", assignment.subjectCode)
        intent.putExtra("ONLINE_CLASS_LINK", assignment.onlineClassLink ?: "")
        startActivity(intent)
    }

    /** Dialog to set or update class online link */
    private fun showSetOnlineClassDialog(assignment: ClassAssignment) {
        val assignmentNo = assignment.assignmentNo
        val originalLink = assignment.onlineClassLink ?: ""
        val primarySlot = assignment.scheduleSlots.values.firstOrNull()
        val sectionBlock = primarySlot?.sectionBlock ?: "A"
        val courseName = assignment.courseCode.ifEmpty { "BSIT" }

        val builder = AlertDialog.Builder(this)
        val titleText = if (originalLink.isEmpty()) {
            "Set Online Link for $courseName - $sectionBlock"
        } else {
            "Update Online Link for $courseName - $sectionBlock"
        }
        builder.setTitle(titleText)

        val input = EditText(this)
        input.hint = "Paste Meet or Zoom Link"
        input.setText(originalLink)
        builder.setView(input)

        builder.setNeutralButton("Generate Meet Link", null)
        builder.setNegativeButton("Generate Zoom Link", null)
        builder.setPositiveButton("Save", null)

        val alertDialog = builder.create()
        alertDialog.show()

        val meetButton = alertDialog.getButton(DialogInterface.BUTTON_NEUTRAL)
        val zoomButton = alertDialog.getButton(DialogInterface.BUTTON_NEGATIVE)
        val saveButton = alertDialog.getButton(DialogInterface.BUTTON_POSITIVE)

        // Initially disable save button if no change
        saveButton.isEnabled = false

        input.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val newText = s.toString().trim()
                saveButton.isEnabled =
                    newText.isNotEmpty() && newText != originalLink && isValidOnlineLink(newText)
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        meetButton.setOnClickListener { openGoogleMeetForCreation() }
        zoomButton.setOnClickListener { openZoomForCreation() }

        saveButton.setOnClickListener {
            val newLink = input.text.toString().trim()
            if (!isValidOnlineLink(newLink)) {
                Toast.makeText(this, "Invalid link format.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            setOnlineClassLink(assignmentNo, newLink)
            alertDialog.dismiss()
        }
    }

    private fun openGoogleMeetForCreation() {
        val meetPackage = "com.google.android.apps.tachyon"
        val intent = packageManager.getLaunchIntentForPackage(meetPackage)
        if (intent != null) {
            startActivity(intent)
            Toast.makeText(this, "Opening Google Meet — create a meeting and copy the link.", Toast.LENGTH_LONG).show()
        } else {
            openPlayStoreOrFallback(meetPackage, "https://meet.google.com")
        }
    }

    private fun openZoomForCreation() {
        val zoomPackage = "us.zoom.videomeetings"
        val intent = packageManager.getLaunchIntentForPackage(zoomPackage)
        if (intent != null) {
            startActivity(intent)
            Toast.makeText(this, "Opening Zoom — create a meeting and copy the link.", Toast.LENGTH_LONG).show()
        } else {
            openPlayStoreOrFallback(zoomPackage, "https://zoom.us/start")
        }
    }

    /** fallback handler if app not installed */
    private fun openPlayStoreOrFallback(packageName: String, webFallback: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageName")))
        } catch (e: ActivityNotFoundException) {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(webFallback)))
        }
        Toast.makeText(this, "App not installed — opening web version.", Toast.LENGTH_LONG).show()
    }

    private fun setOnlineClassLink(assignmentNo: String, link: String) {
        firestore.collection("classAssignments").document(assignmentNo)
            .update("onlineClassLink", link)
            .addOnSuccessListener {
                Toast.makeText(this, "Online Class Link saved successfully! ✅", Toast.LENGTH_LONG).show()
                loadAssignedClasses()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to save link: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }

    private fun isValidOnlineLink(link: String): Boolean {
        val normalized = link.trim()
        return normalized.contains("meet.google.com") || normalized.contains("zoom.us/j")
    }
}
