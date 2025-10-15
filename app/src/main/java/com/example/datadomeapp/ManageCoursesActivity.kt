package com.example.datadomeapp

import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.FirebaseFirestore

data class Course(
    val name: String = "",
    val code: String = "",
    val description: String = "",
)

class ManageCoursesActivity : AppCompatActivity() {

    private val firestore = FirebaseFirestore.getInstance()
    private val coursesCollection = firestore.collection("courses")

    private val courseList = ArrayList<String>()
    private lateinit var adapter: ArrayAdapter<String>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_manage_courses)

        val listView = findViewById<ListView>(R.id.lvCourses)
        val etName = findViewById<EditText>(R.id.etCourseName)
        val etCode = findViewById<EditText>(R.id.etCourseCode)
        val etDescription = findViewById<EditText>(R.id.etCourseDescription)
        val btnAdd = findViewById<Button>(R.id.btnAddCourse)
        val btnDelete = findViewById<Button>(R.id.btnDeleteCourse)

        adapter = ArrayAdapter(this, android.R.layout.simple_list_item_single_choice, courseList)
        listView.adapter = adapter
        listView.choiceMode = ListView.CHOICE_MODE_SINGLE

        // 🔹 PAGBABAGO: Gumamit ng One-Time Get() imbes na addSnapshotListener
        loadCoursesOnce()

        btnAdd.setOnClickListener {
            val name = etName.text.toString().trim()
            val code = etCode.text.toString().trim()
            val desc = etDescription.text.toString().trim()

            if (name.isEmpty() || code.isEmpty() || desc.isEmpty()) {
                Toast.makeText(this, "Fill all fields", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val course = Course(name, code, desc)
            coursesCollection.document().set(course)
                .addOnSuccessListener {
                    etName.text.clear()
                    etCode.text.clear()
                    etDescription.text.clear()
                    Toast.makeText(this, "Course added!", Toast.LENGTH_SHORT).show()
                    // I-reload ang listahan pagkatapos mag-add
                    loadCoursesOnce()
                }
                .addOnFailureListener { e ->
                    Toast.makeText(this, "Failed to add course: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        }

        btnDelete.setOnClickListener {
            val pos = listView.checkedItemPosition
            if (pos != ListView.INVALID_POSITION) {
                val courseName = courseList[pos]
                coursesCollection.whereEqualTo("name", courseName)
                    .get()
                    .addOnSuccessListener { docs ->
                        for (doc in docs) doc.reference.delete()
                        Toast.makeText(this, "Course removed", Toast.LENGTH_SHORT).show()
                        // I-reload ang listahan pagkatapos mag-delete
                        loadCoursesOnce()
                    }
            } else {
                Toast.makeText(this, "Select a course to delete", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // IDINAGDAG: Function para sa one-time load ng courses
    private fun loadCoursesOnce() {
        coursesCollection.get()
            .addOnSuccessListener { snapshot ->
                courseList.clear()
                snapshot.documents.forEach { doc ->
                    val name = doc.getString("name")
                    name?.let { courseList.add(it) }
                }
                adapter.notifyDataSetChanged()
            }
            .addOnFailureListener { error ->
                Toast.makeText(this, "Failed to load courses: ${error.message}", Toast.LENGTH_SHORT).show()
            }
    }
}