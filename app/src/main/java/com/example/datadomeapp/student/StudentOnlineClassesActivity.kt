package com.example.datadomeapp.student

import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.datadomeapp.R
import com.example.datadomeapp.models.OnlineClassAssignment // ✅ Import ang bagong class
import com.google.firebase.firestore.FirebaseFirestore

class StudentOnlineClassesActivity : AppCompatActivity() {

    private val firestore = FirebaseFirestore.getInstance()
    private lateinit var recyclerView: RecyclerView
    private var studentId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.student_online_classes)

        supportActionBar?.title = "Online Class Links"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        studentId = intent.getStringExtra("STUDENT_ID")

        if (studentId.isNullOrEmpty()) {
            Toast.makeText(this, "Error: Student ID is missing.", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        recyclerView = findViewById(R.id.recyclerViewOnlineClasses)
        recyclerView.layoutManager = LinearLayoutManager(this)

        fetchStudentSectionAndLoadClasses(studentId!!)
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun fetchStudentSectionAndLoadClasses(studentId: String) {
        Log.d("SECTION_DEBUG", "Fetching section for Student ID: $studentId")

        // Query ang student document
        firestore.collection("students")
            .document(studentId)
            .get()
            .addOnSuccessListener { document ->
                if (!document.exists()) {
                    Log.e("SECTION_DEBUG", "Student document not found for ID: $studentId")
                    Toast.makeText(this, "Error: Student data not found.", Toast.LENGTH_LONG).show()
                    return@addOnSuccessListener
                }

                // ✅ KRITIKAL: Dito kukunin ang sectionName. Tiyakin na ito ang tamang field name sa Firestore
                val studentSectionName = document.getString("sectionId")

                if (!studentSectionName.isNullOrEmpty()) {
                    Log.i("SECTION_DEBUG", "SUCCESS: Found sectionName: $studentSectionName")
                    loadOnlineClassLinks(studentSectionName)
                } else {
                    Log.e("SECTION_DEBUG", "ERROR: 'sectionName' field is missing or empty in student document.")
                    Toast.makeText(this, "Error: Please update your student profile data (Missing Section Name).", Toast.LENGTH_LONG).show()
                }
            }
            .addOnFailureListener { e ->
                Log.e("SECTION_DEBUG", "Error fetching student section: $e")
                Toast.makeText(this, "Failed to connect to database. Check internet connection.", Toast.LENGTH_LONG).show()
            }
    }

    private fun loadOnlineClassLinks(studentSectionName: String) {
        // Hakbang 2: Mag-query sa 'classAssignments' at i-filter gamit ang Section Name
        firestore.collection("classAssignments")
            .get()
            .addOnSuccessListener { snapshot ->
                val classList = mutableListOf<OnlineClassAssignment>() // ✅ Gamitin ang bagong class
                for (document in snapshot.documents) {
                    val assignment = document.toObject(OnlineClassAssignment::class.java) // ✅ Gamitin ang bagong class

                    // Filter: Ipakita lang ang assignments na tugma sa Section Name
                    if (assignment != null && assignment.sectionName == studentSectionName) {
                        classList.add(assignment)
                    }
                }

                if (classList.isEmpty()) {
                    Toast.makeText(this, "No online classes found for your section ($studentSectionName).", Toast.LENGTH_LONG).show()
                }

                // I-sort ang listahan
                val sortedList = classList.sortedBy { it.day + it.startTime }

                recyclerView.adapter = OnlineClassAdapter(sortedList)
            }
            .addOnFailureListener { e ->
                Log.e("OnlineClasses", "Error loading online classes: $e")
                Toast.makeText(this, "Failed to load subjects: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }
}