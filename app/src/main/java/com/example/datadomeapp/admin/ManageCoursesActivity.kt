package com.example.datadomeapp.admin

import android.app.AlertDialog
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.example.datadomeapp.R
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.FieldValue
import com.example.datadomeapp.models.Room // Import ang Room model
import java.util.Locale

// Data class para sa Course
data class Course(
    val name: String = "",
    val code: String = "",
    val description: String = "",
)

// Data class para sa Course Sections
data class CourseSections(
    val courseCode: String = "",
    val sections: Map<String, List<String>> = emptyMap()
)

class ManageCoursesActivity : AppCompatActivity() {

    private val firestore = FirebaseFirestore.getInstance()
    private val coursesCollection = firestore.collection("courses")
    private val sectionsCollection = firestore.collection("sections")
    private val roomsCollection = firestore.collection("rooms")

    // UI Elements
    private lateinit var listView: ListView
    private lateinit var btnManageSections: Button
    private lateinit var btnManageRooms: Button
    private lateinit var etName: EditText
    private lateinit var etCode: EditText
    private lateinit var etDescription: EditText

    // Data Holders
    private val courseMap = mutableMapOf<String, String>()
    private val courseList = ArrayList<String>()
    private lateinit var adapter: ArrayAdapter<String>
    private var selectedCourseCode: String? = null
    private var selectedCourseDocId: String? = null
    private val yearLevels = arrayOf("1st Year", "2nd Year", "3rd Year", "4th Year")


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.admin_course_management)

        // Initialize UI components
        listView = findViewById(R.id.lvCourses)
        etName = findViewById(R.id.etCourseName)
        etCode = findViewById(R.id.etCourseCode)
        etDescription = findViewById(R.id.etCourseDescription)
        val btnAdd = findViewById<Button>(R.id.btnAddCourse)
        val btnDelete = findViewById<Button>(R.id.btnDeleteCourse)
        btnManageSections = findViewById(R.id.btnManageSections)
        btnManageRooms = findViewById(R.id.btnManageRooms)

        adapter = ArrayAdapter(this, android.R.layout.simple_list_item_single_choice, courseList)
        listView.adapter = adapter
        listView.choiceMode = ListView.CHOICE_MODE_SINGLE

        loadCoursesOnce()

        // Listener for selecting a course
        listView.setOnItemClickListener { _, _, position, _ ->
            val selectedCourseDisplay = courseList[position]
            selectedCourseDocId = courseMap[selectedCourseDisplay]
            selectedCourseCode = selectedCourseDocId

            btnManageSections.isEnabled = true
            btnManageSections.text = "Manage Sections for ${selectedCourseCode ?: "..."}"
        }

        // --- MANAGEMENT ENTRY POINTS ---
        btnAdd.setOnClickListener { addNewCourse() }
        btnDelete.setOnClickListener {
            val pos = listView.checkedItemPosition
            if (pos != ListView.INVALID_POSITION) {
                val courseName = courseList[pos]
                val docId = courseMap[courseName]

                if (docId != null) {
                    AlertDialog.Builder(this)
                        .setTitle("Confirm Deletion")
                        .setMessage("Are you sure you want to delete '$courseName'? The associated section document will also be deleted.")
                        .setPositiveButton("Delete") { _, _ ->
                            deleteCourseAndSections(docId, courseName)
                        }
                        .setNegativeButton("Cancel", null)
                        .show()
                }
            } else {
                Toast.makeText(this, "Select a course to delete", Toast.LENGTH_SHORT).show()
            }
        }
        btnManageSections.setOnClickListener {
            if (selectedCourseCode != null) {
                showSectionManagerDialog(selectedCourseCode!!)
            } else {
                Toast.makeText(this, "Please select a course first.", Toast.LENGTH_SHORT).show()
            }
        }
        btnManageRooms.setOnClickListener {
            showRoomManagerDialog()
        }
    }

    // --- Course Management Functions (Unchanged) ---
    private fun addNewCourse() {
        val name = etName.text.toString().trim()
        val code = etCode.text.toString().trim().uppercase(Locale.getDefault())
        val desc = etDescription.text.toString().trim()

        if (name.isEmpty() || code.isEmpty() || desc.isEmpty()) {
            Toast.makeText(this, "Fill all fields", Toast.LENGTH_SHORT).show()
            return
        }

        coursesCollection.document(code).get()
            .addOnSuccessListener { documentSnapshot ->
                if (documentSnapshot.exists()) {
                    Toast.makeText(this, "Course code '$code' already exists.", Toast.LENGTH_SHORT).show()
                    return@addOnSuccessListener
                }

                val course = Course(name, code, desc)
                coursesCollection.document(code).set(course)
                    .addOnSuccessListener {
                        etName.text.clear()
                        etCode.text.clear()
                        etDescription.text.clear()
                        Toast.makeText(this, "Course added: $code!", Toast.LENGTH_SHORT).show()
                        loadCoursesOnce()
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(this, "Failed to add course: ${e.message}", Toast.LENGTH_LONG).show()
                        Log.e("ManageCourses", "Save failed", e)
                    }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error checking for existing course: ${e.message}", Toast.LENGTH_LONG).show()
                Log.e("ManageCourses", "Check failed", e)
            }
    }

    private fun deleteCourseAndSections(courseCode: String, courseName: String) {
        sectionsCollection.document(courseCode).delete()
            .addOnSuccessListener {
                Log.d("ManageCourses", "Successfully deleted section document for $courseCode.")
                coursesCollection.document(courseCode).delete()
                    .addOnSuccessListener {
                        Toast.makeText(this, "Course '$courseName' removed successfully.", Toast.LENGTH_LONG).show()
                        loadCoursesOnce()
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(this, "Failed to delete course: ${e.message}", Toast.LENGTH_LONG).show()
                    }
            }
            .addOnFailureListener { e ->
                Log.w("ManageCourses", "Failed to delete section document (may not exist): ${e.message}")
                coursesCollection.document(courseCode).delete()
                    .addOnSuccessListener {
                        Toast.makeText(this, "Course '$courseName' removed successfully.", Toast.LENGTH_LONG).show()
                        loadCoursesOnce()
                    }
                    .addOnFailureListener { err ->
                        Toast.makeText(this, "Failed to delete course: ${err.message}", Toast.LENGTH_LONG).show()
                    }
            }
    }

    private fun loadCoursesOnce() {
        selectedCourseCode = null
        selectedCourseDocId = null
        btnManageSections.isEnabled = false
        btnManageSections.text = "Manage Sections for Selected Course"

        coursesCollection.get()
            .addOnSuccessListener { snapshot ->
                courseList.clear()
                courseMap.clear()
                snapshot.documents.forEach { doc ->
                    val name = doc.getString("name")
                    val code = doc.getString("code")
                    name?.let {
                        val display = "$it ($code)"
                        courseList.add(display)
                        courseMap[display] = doc.id
                    }
                }
                adapter.notifyDataSetChanged()
                listView.clearChoices()
            }
            .addOnFailureListener { error ->
                Toast.makeText(this, "Failed to load courses: ${error.message}", Toast.LENGTH_SHORT).show()
            }
    }

    // --- Section Management Dialog ---
    private fun showSectionManagerDialog(courseCode: String) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.admin_section_dialog, null)
        val tvHeader = dialogView.findViewById<TextView>(R.id.tvSectionHeader)
        val lvSections = dialogView.findViewById<ListView>(R.id.lvSectionsDialog)
        val spnYear = dialogView.findViewById<Spinner>(R.id.spnYearLevelDialog)
        spnYear.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, yearLevels)

        val etSectionBlock = dialogView.findViewById<EditText>(R.id.etSectionBlockDialog)
        val btnAdd = dialogView.findViewById<Button>(R.id.btnAddSectionDialog)
        val btnDelete = dialogView.findViewById<Button>(R.id.btnDeleteSectionDialog)

        tvHeader.text = "Manage Sections for $courseCode"

        val sectionBlockDisplayList = ArrayList<String>()
        val sectionAdapter = ArrayAdapter(this, android.R.layout.simple_list_item_single_choice, sectionBlockDisplayList)
        lvSections.adapter = sectionAdapter
        lvSections.choiceMode = ListView.CHOICE_MODE_SINGLE

        var currentSectionMap: MutableMap<String, List<String>> = mutableMapOf()

        val filterAndRenderList = { selectedYear: String ->
            val yearKey = selectedYear.replace(" ", "")
            sectionBlockDisplayList.clear()
            val blocksForSelectedYear = currentSectionMap[yearKey] ?: emptyList()
            sectionBlockDisplayList.addAll(blocksForSelectedYear.map { "$selectedYear $it" })
            sectionBlockDisplayList.sort()
            sectionAdapter.notifyDataSetChanged()
            lvSections.clearChoices()
        }

        val loadSections = {
            sectionsCollection.document(courseCode).get()
                .addOnSuccessListener { doc ->
                    currentSectionMap.clear()

                    if (doc.exists()) {
                        val sectionsDoc = doc.toObject(CourseSections::class.java)

                        if (sectionsDoc != null) {
                            val validKeys = yearLevels.map { it.replace(" ", "") }
                            currentSectionMap = sectionsDoc.sections.filterKeys { it in validKeys }.toMutableMap()
                        }
                    }

                    if (currentSectionMap.isEmpty()) {
                        val baseSectionsMap = yearLevels.associate { it.replace(" ", "") to emptyList<String>() }
                        val baseDoc = CourseSections(courseCode, baseSectionsMap)
                        sectionsCollection.document(courseCode).set(baseDoc, SetOptions.merge())
                        currentSectionMap = baseSectionsMap.toMutableMap()
                    }

                    val selectedYear = spnYear.selectedItem.toString()
                    filterAndRenderList(selectedYear)
                }
                .addOnFailureListener { e ->
                    Toast.makeText(this, "Error loading sections: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        }
        loadSections()

        spnYear.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val selectedYear = parent?.getItemAtPosition(position).toString()
                filterAndRenderList(selectedYear)
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        btnAdd.setOnClickListener {
            val year = spnYear.selectedItem.toString()
            val blockName = etSectionBlock.text.toString().trim().uppercase(Locale.getDefault())

            if (blockName.isEmpty() || blockName.length > 3) {
                Toast.makeText(this, "Enter a valid section block (1-3 uppercase chars).", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val yearKey = year.replace(" ", "")
            val updatePath = "sections.$yearKey"

            val existingBlocks = currentSectionMap[yearKey] ?: emptyList()
            if (existingBlocks.contains(blockName)) {
                Toast.makeText(this, "Section '$year - $blockName' already exists.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }


            sectionsCollection.document(courseCode)
                .update(updatePath, FieldValue.arrayUnion(blockName))
                .addOnSuccessListener {
                    etSectionBlock.text.clear()
                    Toast.makeText(this, "Section '$year - $blockName' added!", Toast.LENGTH_SHORT).show()
                    loadSections()
                }
                .addOnFailureListener { e ->
                    Toast.makeText(this, "Failed to add section: ${e.message}", Toast.LENGTH_SHORT).show()
                    Log.e("Sections", "ArrayUnion failed: ${e.message}")
                }
        }

        btnDelete.setOnClickListener {
            val pos = lvSections.checkedItemPosition
            if (pos != ListView.INVALID_POSITION) {
                val displayBlock = sectionBlockDisplayList[pos]
                val blockToRemove = displayBlock.substringAfterLast(" ").trim().uppercase(Locale.getDefault())

                val year = spnYear.selectedItem.toString()
                val yearKey = year.replace(" ", "")
                val updatePath = "sections.$yearKey"

                sectionsCollection.document(courseCode)
                    .update(updatePath, FieldValue.arrayRemove(blockToRemove))
                    .addOnSuccessListener {
                        Toast.makeText(this, "Section '$displayBlock' removed.", Toast.LENGTH_SHORT).show()
                        loadSections()
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(this, "Failed to delete: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
            } else {
                Toast.makeText(this, "Select a section to delete.", Toast.LENGTH_SHORT).show()
            }
        }

        AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(true)
            .setPositiveButton("Close") { dialog, _ -> dialog.dismiss() }
            .show()
    }


    // --- ROOM MANAGEMENT DIALOG ---
    // --- ROOM MANAGEMENT DIALOG ---
    private fun showRoomManagerDialog() {
        // I-inflate ang dialog view
        val dialogView = LayoutInflater.from(this).inflate(R.layout.admin_rooms_management_dialog, null)
        val etRoomId = dialogView.findViewById<EditText>(R.id.etRoomId)
        // ✅ TINANGGAL: etRoomName
        val btnAddRoom = dialogView.findViewById<Button>(R.id.btnAddRoom)
        val btnDeleteRoom = dialogView.findViewById<Button>(R.id.btnDeleteRoom)
        val lvRooms = dialogView.findViewById<ListView>(R.id.lvRoomsDialog)

        val roomDisplayList = ArrayList<String>()
        val roomMap = mutableMapOf<String, String>() // Display Name -> Room ID
        val roomAdapter = ArrayAdapter(this, android.R.layout.simple_list_item_single_choice, roomDisplayList)
        lvRooms.adapter = roomAdapter
        lvRooms.choiceMode = ListView.CHOICE_MODE_SINGLE

        // 🎯 LOAD ROOMS
        val loadRooms = {
            roomsCollection.get()
                .addOnSuccessListener { snapshot ->
                    roomDisplayList.clear()
                    roomMap.clear()

                    snapshot.documents.forEach { doc ->
                        // Gumagamit ng Room model na mayroon lang 'id'
                        val room = doc.toObject(Room::class.java)
                        room?.let {
                            val display = it.id // ✅ Display na lang ang ID
                            roomDisplayList.add(display)
                            roomMap[display] = it.id
                        }
                    }
                    roomAdapter.notifyDataSetChanged()
                    lvRooms.clearChoices()
                }
                .addOnFailureListener { e ->
                    Toast.makeText(this, "Error loading rooms: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        }
        loadRooms()

        // 🎯 ADD ROOM
        btnAddRoom.setOnClickListener {
            val id = etRoomId.text.toString().trim().uppercase(Locale.getDefault())
            // ✅ TINANGGAL: name field validation at pagkuha ng text

            if (id.isEmpty()) {
                Toast.makeText(this, "Room ID is required.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // ✅ Gumagamit ng Room model na mayroon lang ID
            val room = Room(id = id)
            roomsCollection.document(id).set(room)
                .addOnSuccessListener {
                    etRoomId.text.clear()
                    Toast.makeText(this, "Room $id added.", Toast.LENGTH_SHORT).show()
                    loadRooms()
                }
                .addOnFailureListener { e ->
                    Toast.makeText(this, "Failed to add room: ${e.message}", Toast.LENGTH_SHORT).show()                }
        }

        // 🎯 DELETE ROOM
        btnDeleteRoom.setOnClickListener {
            val pos = lvRooms.checkedItemPosition
            if (pos != ListView.INVALID_POSITION) {
                val displayName = roomDisplayList[pos]
                val roomIdToDelete = roomMap[displayName]

                if (roomIdToDelete != null) {
                    AlertDialog.Builder(this)
                        .setTitle("Confirm Deletion")
                        .setMessage("Are you sure you want to delete room '$displayName'?")
                        .setPositiveButton("Delete") { _, _ ->
                            roomsCollection.document(roomIdToDelete).delete()
                                .addOnSuccessListener {
                                    Toast.makeText(this, "Room '$roomIdToDelete' removed.", Toast.LENGTH_SHORT).show()
                                    loadRooms()
                                }
                                .addOnFailureListener { e ->
                                    Toast.makeText(this, "Failed to delete room: ${e.message}", Toast.LENGTH_SHORT).show()
                                }
                        }
                        .setNegativeButton("Cancel", null)
                        .show()
                }
            } else {
                Toast.makeText(this, "Select a room to delete.", Toast.LENGTH_SHORT).show()
            }
        }

        AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(true)
            .setPositiveButton("Close") { dialog, _ -> dialog.dismiss() }
            .show()
    }
}