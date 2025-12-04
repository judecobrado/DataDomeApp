package com.example.datadomeapp.admin

import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.core.widget.addTextChangedListener
import androidx.recyclerview.widget.RecyclerView
import com.example.datadomeapp.R
import com.google.android.material.button.MaterialButton
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import android.os.Environment
import androidx.core.content.FileProvider

data class Student(
    val id: String = "",
    val studentId: String = "",
    val firstName: String = "",
    val lastName: String = "",
    val courseCode: String = "",
    val yearLevel: String = "",
    val rfidTag: String? = null,
    val userUid: String = "",
    val rfidStatus: String? = null,
    val profileImageUrl: String? = null
)

class ManageStudentsActivity : AppCompatActivity() {

    private val firestore = FirebaseFirestore.getInstance()
    private val storage = FirebaseStorage.getInstance()

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: StudentAdapter
    private lateinit var etSearch: EditText
    private lateinit var spinnerFilterCourse: Spinner
    private lateinit var spinnerFilterYear: Spinner

    private val studentList = mutableListOf<Student>()
    private var allStudentsCache = listOf<Student>()

    // RFID/NFC Declarations
    private var nfcAdapter: NfcAdapter? = null
    private var pendingIntent: PendingIntent? = null
    private var currentStudentForRfid: Student? = null
    private var rfidDetectionDialog: AlertDialog? = null
    private var isNfcSupported = false

    // Camera and Image Declarations
    private var currentStudentForRfidWithPhoto: Student? = null
    private var tempImageUri: Uri? = null

    // Permission and Request Codes
    private companion object {
        private const val REQUEST_CAMERA_PERMISSION = 1001
        private const val REQUEST_IMAGE_CAPTURE = 1002
        private const val REQUEST_IMAGE_PICK = 1003
    }

    private val TAG = "ManageStudentsActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.admin_manage_students)

        // BACK BUTTON FUNCTIONALITY - ADDED HERE
        val btnBackToDashboard = findViewById<MaterialButton>(R.id.btnBackToDashboard)
        btnBackToDashboard.setOnClickListener {
            finish() // Close current activity and go back
        }

        etSearch = findViewById(R.id.etSearchStudent)
        spinnerFilterCourse = findViewById(R.id.spinnerFilterCourse)
        spinnerFilterYear = findViewById(R.id.spinnerFilterYear)
        recyclerView = findViewById(R.id.rvStudents)
        recyclerView.layoutManager = LinearLayoutManager(this)

        adapter = StudentAdapter(studentList) { student ->
            showStudentDetailDialog(student)
        }
        recyclerView.adapter = adapter

        setupNfc()
        loadAllStudents()
        setupFilters()
    }

    private fun setupNfc() {
        nfcAdapter = NfcAdapter.getDefaultAdapter(this)
        if (nfcAdapter == null) {
            Toast.makeText(this, "NFC is not supported on this device. RFID features disabled.", Toast.LENGTH_LONG).show()
            isNfcSupported = false
            return
        }
        isNfcSupported = true
        val intent = Intent(this, javaClass).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    override fun onResume() {
        super.onResume()
        if (isNfcSupported) {
            nfcAdapter?.enableForegroundDispatch(this, pendingIntent, null, null)
        }
    }

    override fun onPause() {
        super.onPause()
        if (isNfcSupported) {
            nfcAdapter?.disableForegroundDispatch(this)
        }
    }

    override fun onNewIntent(intent: Intent) {
        if (!isNfcSupported) {
            super.onNewIntent(intent)
            return
        }
        super.onNewIntent(intent)

        if (NfcAdapter.ACTION_TAG_DISCOVERED == intent.action ||
            NfcAdapter.ACTION_NDEF_DISCOVERED == intent.action ||
            NfcAdapter.ACTION_TECH_DISCOVERED == intent.action) {

            val tag: Tag? = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG)
            if (tag != null) {
                val rfidHex = bytesToHex(tag.id)
                Log.d("NFC_SCAN", "RFID Detected: $rfidHex")

                if (currentStudentForRfid != null) {
                    saveRfidTag(currentStudentForRfid!!, rfidHex)
                } else {
                    performRfidQuickSearch(rfidHex)
                }
            }
        }
    }

    // CAMERA AND IMAGE HANDLING
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (resultCode == RESULT_OK) {
            when (requestCode) {
                REQUEST_IMAGE_CAPTURE -> {
                    tempImageUri?.let { uri ->
                        uploadImageToFirebase(uri)
                    } ?: run {
                        Toast.makeText(this, "Failed to capture image", Toast.LENGTH_SHORT).show()
                        currentStudentForRfidWithPhoto?.let { proceedToRfidScanning(it) }
                    }
                }
                REQUEST_IMAGE_PICK -> {
                    data?.data?.let { uri ->
                        uploadImageToFirebase(uri)
                    } ?: run {
                        Toast.makeText(this, "Failed to select image", Toast.LENGTH_SHORT).show()
                        currentStudentForRfidWithPhoto?.let { proceedToRfidScanning(it) }
                    }
                }
            }
        } else {
            currentStudentForRfidWithPhoto?.let { proceedToRfidScanning(it) }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            REQUEST_CAMERA_PERMISSION -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    currentStudentForRfidWithPhoto?.let { takePhoto(it) }
                } else {
                    Toast.makeText(this, "Camera permission is required to take photos", Toast.LENGTH_LONG).show()
                    currentStudentForRfidWithPhoto?.let { proceedToRfidScanning(it) }
                }
            }
        }
    }

    private fun takePhoto(student: Student) {
        Log.d(TAG, "takePhoto called for ${student.firstName}")
        currentStudentForRfidWithPhoto = student

        // Check camera permission
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            Log.d(TAG, "Requesting camera permission")
            ActivityCompat.requestPermissions(this, arrayOf(android.Manifest.permission.CAMERA), REQUEST_CAMERA_PERMISSION)
            return
        }

        Log.d(TAG, "Camera permission granted, launching camera")

        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)

        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val imageFileName = "JPEG_${timeStamp}_"
        val storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        val imageFile = File.createTempFile(imageFileName, ".jpg", storageDir)

        // Get the photo URI using FileProvider
        val photoURI: Uri = FileProvider.getUriForFile(
            this,
            "com.example.datadomeapp.fileprovider",
            imageFile
        )

        tempImageUri = photoURI

        intent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI)
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

        // Check if there's a camera app available
        if (intent.resolveActivity(packageManager) != null) {
            Log.d(TAG, "Camera app found, starting activity")
            startActivityForResult(intent, REQUEST_IMAGE_CAPTURE)
        } else {
            Log.e(TAG, "No camera app found")
            Toast.makeText(this, "No camera app found on your device", Toast.LENGTH_LONG).show()
        }
    }

    private fun chooseFromGallery(student: Student) {
        currentStudentForRfidWithPhoto = student
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        intent.type = "image/*"
        startActivityForResult(intent, REQUEST_IMAGE_PICK)
    }

    private fun uploadImageToFirebase(imageUri: Uri) {
        val student = currentStudentForRfidWithPhoto ?: return

        val progressDialog = AlertDialog.Builder(this)
            .setView(LayoutInflater.from(this).inflate(R.layout.dialog_loading, null))
            .setCancelable(false)
            .create()
        progressDialog.show()

        try {
            val fileName = "profile_${student.id}_${System.currentTimeMillis()}.jpg"
            val storageRef = storage.reference.child("student_profile_pictures/$fileName")

            storageRef.putFile(imageUri)
                .addOnSuccessListener { taskSnapshot ->
                    storageRef.downloadUrl.addOnSuccessListener { downloadUri ->
                        updateStudentProfileImage(student, downloadUri.toString(), progressDialog)
                    }.addOnFailureListener { e ->
                        progressDialog.dismiss()
                        Toast.makeText(this, "Failed to get image URL: ${e.message}", Toast.LENGTH_LONG).show()
                        proceedToRfidScanning(student)
                    }
                }
                .addOnFailureListener { e ->
                    progressDialog.dismiss()
                    Toast.makeText(this, "Failed to upload image: ${e.message}", Toast.LENGTH_LONG).show()
                    proceedToRfidScanning(student)
                }
        } catch (e: Exception) {
            progressDialog.dismiss()
            Toast.makeText(this, "Error uploading image: ${e.message}", Toast.LENGTH_LONG).show()
            proceedToRfidScanning(student)
        }
    }

    private fun updateStudentProfileImage(student: Student, imageUrl: String, progressDialog: AlertDialog) {
        val studentRef = firestore.collection("students").document(student.id)
        val userRef = if (!student.userUid.isNullOrEmpty()) {
            firestore.collection("users").document(student.userUid)
        } else {
            null
        }

        val updateData = mapOf("profileImageUrl" to imageUrl)

        CoroutineScope(Dispatchers.Main).launch {
            try {
                if (userRef != null) {
                    firestore.runBatch { batch ->
                        batch.update(studentRef, updateData)
                        batch.update(userRef, updateData)
                    }.await()
                } else {
                    studentRef.update(updateData).await()
                }

                progressDialog.dismiss()
                Toast.makeText(this@ManageStudentsActivity, "Profile picture updated successfully!", Toast.LENGTH_SHORT).show()

                val updatedList = allStudentsCache.map {
                    if (it.id == student.id) it.copy(profileImageUrl = imageUrl) else it
                }
                allStudentsCache = updatedList
                applyFilters()

                proceedToRfidScanning(student.copy(profileImageUrl = imageUrl))

            } catch (e: Exception) {
                progressDialog.dismiss()
                Toast.makeText(this@ManageStudentsActivity, "Failed to update profile: ${e.message}", Toast.LENGTH_LONG).show()
                Log.e(TAG, "Error updating profile image", e)
                proceedToRfidScanning(student)
            }
        }
    }

    private fun showRfidDetectionDialog(student: Student) {
        val options = arrayOf("Take Photo", "Choose from Gallery", "Skip Photo")

        AlertDialog.Builder(this)
            .setTitle("Add Profile Picture")
            .setMessage("First, add a profile picture for ${student.firstName} ${student.lastName}")
            .setItems(options) { dialog, which ->
                when (which) {
                    0 -> {
                        dialog.dismiss()
                        takePhoto(student)
                    }
                    1 -> {
                        dialog.dismiss()
                        chooseFromGallery(student)
                    }
                    2 -> {
                        dialog.dismiss()
                        proceedToRfidScanning(student)
                    }
                }
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
                showStudentDetailDialog(student)
            }
            .setCancelable(false)
            .show()
    }

    private fun proceedToRfidScanning(student: Student) {
        currentStudentForRfid = student
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_rfid_detection, null)
        val tvStatus = dialogView.findViewById<TextView>(R.id.tvRfidDetectionStatus)
        tvStatus.text = "Ready to scan RFID/NFC tag for ${student.firstName} ${student.lastName}.\n\nBring the tag near the phone's NFC area."

        rfidDetectionDialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setTitle("RFID Tag Registration")
            .setPositiveButton("Cancel") { _, _ ->
                currentStudentForRfid = null
                showStudentDetailDialog(student)
            }
            .setCancelable(false)
            .create()
        rfidDetectionDialog!!.show()
    }

    // REST OF THE FUNCTIONS (loadAllStudents, setupFilters, applyFilters, etc.) remain the same as your working code
    private fun performRfidQuickSearch(rfidTag: String) {
        firestore.collection("students").whereEqualTo("rfidTag", rfidTag).get()
            .addOnSuccessListener { snapshot ->
                if (snapshot.isEmpty) {
                    Toast.makeText(this, "RFID Tag $rfidTag is not registered to any student.", Toast.LENGTH_LONG).show()
                } else {
                    val studentDoc = snapshot.documents.first()
                    val student = studentDoc.toObject(Student::class.java)?.copy(id = studentDoc.id)
                    if (student != null) {
                        etSearch.setText(student.studentId)
                        showStudentDetailDialog(student)
                        Toast.makeText(this, "Student found: ${student.firstName} ${student.lastName}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Search failed: ${e.message}", Toast.LENGTH_LONG).show()
                Log.e("RFID_SEARCH", "Error searching by RFID tag", e)
            }
    }

    private fun bytesToHex(bytes: ByteArray): String {
        val hexArray = charArrayOf('0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F')
        val hexChars = CharArray(bytes.size * 2)
        for (j in bytes.indices) {
            val v: Int = bytes[j].toInt() and 0xFF
            hexChars[j * 2] = hexArray[v ushr 4]
            hexChars[j * 2 + 1] = hexArray[v and 0x0F]
        }
        return String(hexChars)
    }

    private fun loadAllStudents() {
        firestore.collection("students").orderBy("lastName", Query.Direction.ASCENDING).get()
            .addOnSuccessListener { snapshot ->
                allStudentsCache = snapshot.documents.mapNotNull { doc ->
                    doc.toObject(Student::class.java)?.copy(id = doc.id)
                }
                val courses = allStudentsCache.map { it.courseCode }.distinct().toMutableList()
                courses.add(0, "All Courses")
                setupSpinner(spinnerFilterCourse, courses) { _ -> applyFilters() }
                applyFilters()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error loading students", Toast.LENGTH_SHORT).show()
                Log.e("Students", "Error loading students", e)
            }
    }

    private fun setupFilters() {
        val years = listOf("All Year Levels", "1st Year")
        setupSpinner(spinnerFilterYear, years) { _ -> applyFilters() }
        etSearch.addTextChangedListener { applyFilters() }
    }

    private fun setupSpinner(spinner: Spinner, items: List<String>, onItemSelected: (String) -> Unit) {
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, items)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinner.adapter = adapter
        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                onItemSelected(parent?.getItemAtPosition(position).toString())
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun applyFilters() {
        val searchText = etSearch.text.toString().trim().lowercase(Locale.getDefault())
        val selectedCourse = spinnerFilterCourse.selectedItem?.toString()
        val selectedYear = spinnerFilterYear.selectedItem?.toString()

        val filteredList = allStudentsCache.filter { student ->
            val courseMatch = selectedCourse == "All Courses" || student.courseCode == selectedCourse
            val yearMatch = selectedYear == "All Year Levels" || student.yearLevel == selectedYear
            val searchMatch = searchText.isEmpty() ||
                    student.firstName.lowercase(Locale.getDefault()).contains(searchText) ||
                    student.lastName.lowercase(Locale.getDefault()).contains(searchText) ||
                    student.studentId.lowercase(Locale.getDefault()).contains(searchText) ||
                    (!student.rfidTag.isNullOrEmpty() && student.rfidTag!!.lowercase(Locale.getDefault()).contains(searchText))
            courseMatch && yearMatch && searchMatch
        }

        studentList.clear()
        studentList.addAll(filteredList)
        adapter.notifyDataSetChanged()
    }

    private suspend fun checkRfidConflict(rfidTag: String, currentStudentDocId: String): Boolean {
        val studentSnapshot = firestore.collection("students").whereEqualTo("rfidTag", rfidTag).get().await()
        val studentConflict = studentSnapshot.documents.any { doc ->
            doc.id != currentStudentDocId && doc.getString("rfidTag") == rfidTag
        }
        if (studentConflict) return true

        val teacherSnapshot = firestore.collection("teachers").whereEqualTo("rfidTag", rfidTag).limit(1).get().await()
        val teacherConflict = !teacherSnapshot.isEmpty
        if (teacherConflict) return true

        return false
    }

    private fun disableRfidTag(student: Student) {
        AlertDialog.Builder(this)
            .setTitle("Confirm RFID Disable")
            .setMessage("Are you sure you want to **DISABLE** the RFID tag for ${student.firstName} ${student.lastName}? This will set the tag to **DISABLED** status, meaning it can't be used for attendance or login.")
            .setPositiveButton("Disable Tag") { dialog, _ ->
                val studentRef = firestore.collection("students").document(student.id)
                if (student.userUid.isEmpty()) {
                    Toast.makeText(this, "Disable failed: Missing User UID for this student.", Toast.LENGTH_LONG).show()
                    dialog.dismiss()
                    return@setPositiveButton
                }
                val userRef = firestore.collection("users").document(student.userUid)
                CoroutineScope(Dispatchers.Main).launch {
                    try {
                        val updateData = mapOf("rfidStatus" to "DISABLED")
                        firestore.runBatch { batch ->
                            batch.update(studentRef, updateData)
                            batch.update(userRef, updateData)
                        }.await()
                        Toast.makeText(this@ManageStudentsActivity, "RFID Tag successfully **DISABLED** for ${student.firstName}.", Toast.LENGTH_LONG).show()
                        val updatedList = allStudentsCache.map {
                            if (it.id == student.id) it.copy(rfidStatus = "DISABLED") else it
                        }
                        allStudentsCache = updatedList
                        applyFilters()
                    } catch (e: Exception) {
                        Toast.makeText(this@ManageStudentsActivity, "Failed to disable RFID tag: ${e.message}", Toast.LENGTH_LONG).show()
                        Log.e(TAG, "Error disabling RFID tag or syncing user", e)
                    }
                }
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun resetRfidTag(student: Student) {
        AlertDialog.Builder(this)
            .setTitle("Confirm RFID Reset & Re-registration")
            .setMessage("Are you sure you want to **RESET** the RFID tag for ${student.firstName} ${student.lastName}? This will remove the current tag, clear the status, and immediately start the process to scan a NEW one.")
            .setPositiveButton("Reset & Scan New") { dialog, _ ->
                val studentRef = firestore.collection("students").document(student.id)
                if (student.userUid.isEmpty()) {
                    Toast.makeText(this, "Reset failed: Missing User UID for this student.", Toast.LENGTH_LONG).show()
                    dialog.dismiss()
                    return@setPositiveButton
                }
                val userRef = firestore.collection("users").document(student.userUid)
                CoroutineScope(Dispatchers.Main).launch {
                    try {
                        val updateData = mapOf("rfidTag" to null, "rfidStatus" to null)
                        firestore.runBatch { batch ->
                            batch.update(studentRef, updateData)
                            batch.update(userRef, updateData)
                        }.await()
                        Toast.makeText(this@ManageStudentsActivity, "RFID Tag cleared. Please scan the new tag now.", Toast.LENGTH_LONG).show()
                        val updatedList = allStudentsCache.map {
                            if (it.id == student.id) it.copy(rfidTag = null, rfidStatus = null) else it
                        }
                        allStudentsCache = updatedList
                        applyFilters()
                        showRfidDetectionDialog(student.copy(rfidTag = null, rfidStatus = null))
                    } catch (e: Exception) {
                        Toast.makeText(this@ManageStudentsActivity, "Failed to reset RFID tag: ${e.message}", Toast.LENGTH_LONG).show()
                        Log.e(TAG, "Error resetting RFID tag or syncing user", e)
                    }
                }
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showStudentDetailDialog(student: Student) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.admin_student_detail_dialog, null)

        val tvName = dialogView.findViewById<TextView>(R.id.tvName)
        val tvId = dialogView.findViewById<TextView>(R.id.tvStudentId)
        val tvCourse = dialogView.findViewById<TextView>(R.id.tvCourseYear)
        val tvRfid = dialogView.findViewById<TextView>(R.id.tvRfidStatus)
        val btnAddRfid = dialogView.findViewById<Button>(R.id.btnAddRfid)
        val btnResetRfid = dialogView.findViewById<Button>(R.id.btnResetRfid)
        val btnDisableRfid = dialogView.findViewById<Button>(R.id.btnDisableRfid)
        val btnAddProfilePicture = dialogView.findViewById<Button>(R.id.btnAddProfilePicture) // ADD THIS BUTTON

        tvName.text = "${student.lastName}, ${student.firstName}"
        tvId.text = "ID: ${student.studentId}"
        tvCourse.text = "${student.courseCode} - ${student.yearLevel}"

        // Itago muna ang lahat ng button
        btnAddRfid.visibility = View.GONE
        btnResetRfid.visibility = View.GONE
        btnDisableRfid.visibility = View.GONE
        btnAddProfilePicture.visibility = View.GONE // HIDE PROFILE PICTURE BUTTON INITIALLY

        // 🛑 FINAL LOGIC PARA SA BUTTONS AT TEXT 🛑
        if (!student.rfidTag.isNullOrEmpty()) {
            // May rfidTag (pwedeng ACTIVE o DISABLED)
            when (student.rfidStatus) {
                "ACTIVE" -> {
                    tvRfid.text = "RFID Status: 🟢 ACTIVE (${student.rfidTag})"
                    btnResetRfid.visibility = View.VISIBLE
                    btnDisableRfid.visibility = View.VISIBLE
                    btnDisableRfid.text = "Disable RFID"
                    btnAddProfilePicture.visibility = View.VISIBLE // SHOW PROFILE PICTURE BUTTON
                }
                "DISABLED" -> {
                    tvRfid.text = "RFID Status: 🟡 DISABLED (${student.rfidTag})"
                    btnResetRfid.visibility = View.VISIBLE
                    btnDisableRfid.visibility = View.VISIBLE
                    btnDisableRfid.text = "Activate RFID"
                    btnAddProfilePicture.visibility = View.VISIBLE // SHOW PROFILE PICTURE BUTTON
                }
                else -> {
                    tvRfid.text = "RFID Status: ❓ UNKNOWN TAG STATUS (${student.rfidTag})"
                    btnResetRfid.visibility = View.VISIBLE
                    btnDisableRfid.visibility = View.VISIBLE
                    btnDisableRfid.text = "Disable RFID"
                    btnAddProfilePicture.visibility = View.VISIBLE // SHOW PROFILE PICTURE BUTTON
                }
            }
        } else if (!isNfcSupported) {
            // Case 4: Walang tag AND walang NFC support ang phone.
            tvRfid.text = "RFID Status: ❌ NFC NOT AVAILABLE"
            btnAddProfilePicture.visibility = View.VISIBLE // SHOW PROFILE PICTURE BUTTON EVEN WITHOUT NFC
        } else {
            // Case 5: Walang tag AND may NFC support ang phone (Not Registered/Ready for Registration).
            tvRfid.text = "RFID Status: 🔴 NOT REGISTERED"
            btnAddRfid.visibility = View.VISIBLE
            btnAddProfilePicture.visibility = View.VISIBLE // SHOW PROFILE PICTURE BUTTON
        }

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setTitle("Student Profile")
            .setNegativeButton("Close", null)
            .create()

        btnAddRfid.setOnClickListener {
            dialog.dismiss()
            showRfidDetectionDialog(student.copy(rfidTag = null, rfidStatus = null))
        }

        // Reset Click Listener
        btnResetRfid.setOnClickListener {
            dialog.dismiss()
            resetRfidTag(student)
        }

        // Disable/Activate Click Listener (Conditional Logic)
        btnDisableRfid.setOnClickListener {
            dialog.dismiss()
            if (student.rfidStatus == "DISABLED") {
                activateRfidTag(student)
            } else {
                disableRfidTag(student)
            }
        }

        // NEW: Profile Picture Button Click Listener
        btnAddProfilePicture.setOnClickListener {
            dialog.dismiss()
            showProfilePictureDialog(student)
        }

        dialog.show()
    }

    // ADD THIS NEW FUNCTION FOR PROFILE PICTURE ONLY
    // FIXED VERSION: Profile Picture Dialog with working buttons
    private fun showProfilePictureDialog(student: Student) {
        // Set the current student for photo
        currentStudentForRfidWithPhoto = student

        Log.d(TAG, "showProfilePictureDialog called for ${student.firstName}")

        val dialog = AlertDialog.Builder(this)
            .setTitle("Update Profile Picture")
            .setMessage("Update profile picture for ${student.firstName} ${student.lastName}")
            .setPositiveButton("📷 Take Photo") { dialog, _ ->
                Log.d(TAG, "Take Photo button clicked")
                dialog.dismiss()
                takePhoto(student)
            }
            .setNeutralButton("🖼️ Choose from Gallery") { dialog, _ ->
                Log.d(TAG, "Choose from Gallery button clicked")
                dialog.dismiss()
                chooseFromGallery(student)
            }
            .setNegativeButton("❌ Cancel") { dialog, _ ->
                Log.d(TAG, "Cancel button clicked")
                dialog.dismiss()
                showStudentDetailDialog(student)
            }
            .setCancelable(false)
            .create()

        dialog.show()

        // Debug log to confirm dialog is showing
        Log.d(TAG, "Profile picture dialog shown for ${student.firstName}")
    }

    private fun activateRfidTag(student: Student) {
        AlertDialog.Builder(this)
            .setTitle("Confirm RFID Activation")
            .setMessage("Are you sure you want to **ACTIVATE** the RFID tag for ${student.firstName} ${student.lastName}? This will set the tag back to **ACTIVE** status, allowing it to be used for attendance and login.")
            .setPositiveButton("Activate Tag") { dialog, _ ->
                val studentRef = firestore.collection("students").document(student.id)
                if (student.userUid.isEmpty()) {
                    Toast.makeText(this, "Activation failed: Missing User UID for this student.", Toast.LENGTH_LONG).show()
                    dialog.dismiss()
                    return@setPositiveButton
                }
                val userRef = firestore.collection("users").document(student.userUid)
                CoroutineScope(Dispatchers.Main).launch {
                    try {
                        val updateData = mapOf("rfidStatus" to "ACTIVE")
                        firestore.runBatch { batch ->
                            batch.update(studentRef, updateData)
                            batch.update(userRef, updateData)
                        }.await()
                        Toast.makeText(this@ManageStudentsActivity, "RFID Tag successfully **ACTIVATED** for ${student.firstName}.", Toast.LENGTH_LONG).show()
                        val updatedList = allStudentsCache.map {
                            if (it.id == student.id) it.copy(rfidStatus = "ACTIVE") else it
                        }
                        allStudentsCache = updatedList
                        applyFilters()
                        showStudentDetailDialog(student.copy(rfidStatus = "ACTIVE"))
                    } catch (e: Exception) {
                        Toast.makeText(this@ManageStudentsActivity, "Failed to activate RFID tag: ${e.message}", Toast.LENGTH_LONG).show()
                        Log.e(TAG, "Error activating RFID tag or syncing user", e)
                    }
                }
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun saveRfidTag(student: Student, rfidTag: String) {
        val studentRef = firestore.collection("students").document(student.id)
        if (student.userUid.isEmpty()) {
            Toast.makeText(this, "Registration failed: Missing User UID for this student.", Toast.LENGTH_LONG).show()
            Log.e(TAG, "Error: Student document is missing userUid field for ${student.studentId}")
            currentStudentForRfid = null
            rfidDetectionDialog?.dismiss()
            return
        }
        val userRef = firestore.collection("users").document(student.userUid)

        CoroutineScope(Dispatchers.Main).launch {
            try {
                val conflict = checkRfidConflict(rfidTag, student.id)
                if (conflict) {
                    val errorMessage = "❌ ERROR: RFID Tag **$rfidTag** is already registered to another user (Student or Teacher). Tag not assigned."
                    AlertDialog.Builder(this@ManageStudentsActivity)
                        .setTitle("RFID Registration Conflict")
                        .setMessage(errorMessage)
                        .setPositiveButton("Try Again") { dialog, _ -> dialog.dismiss() }
                        .setNegativeButton("Cancel Registration") { dialog, _ ->
                            currentStudentForRfid = null
                            rfidDetectionDialog?.dismiss()
                            showStudentDetailDialog(student)
                        }
                        .setCancelable(false)
                        .show()
                    return@launch
                }

                val updateData = mapOf("rfidTag" to rfidTag, "rfidStatus" to "ACTIVE")
                firestore.runBatch { batch ->
                    batch.update(studentRef, updateData)
                    batch.update(userRef, updateData)
                }.await()

                Toast.makeText(this@ManageStudentsActivity, "Successfully registered RFID: $rfidTag. Status: ACTIVE", Toast.LENGTH_LONG).show()
                val updatedList = allStudentsCache.map {
                    if (it.id == student.id) it.copy(rfidTag = rfidTag, rfidStatus = "ACTIVE") else it
                }
                allStudentsCache = updatedList
                applyFilters()
                currentStudentForRfid = null
                rfidDetectionDialog?.dismiss()

            } catch (e: Exception) {
                Toast.makeText(this@ManageStudentsActivity, "Registration failed: ${e.message}", Toast.LENGTH_LONG).show()
                Log.e(TAG, "Error saving/validating RFID tag or syncing user: ${e.message}", e)
                currentStudentForRfid = null
                rfidDetectionDialog?.dismiss()
                showStudentDetailDialog(student)
            }
        }
    }
}

class StudentAdapter(
    private val items: List<Student>,
    private val clickListener: (Student) -> Unit
) : RecyclerView.Adapter<StudentAdapter.StudentViewHolder>() {

    inner class StudentViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvName: TextView = view.findViewById(R.id.tvStudentItemName)
        val tvId: TextView = view.findViewById(R.id.tvStudentItemId)
        val tvRfid: TextView = view.findViewById(R.id.tvRfidStatus)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): StudentViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.admin_student_item, parent, false)
        return StudentViewHolder(view)
    }

    override fun onBindViewHolder(holder: StudentViewHolder, position: Int) {
        val student = items[position]
        holder.tvName.text = "${student.lastName}, ${student.firstName}"
        holder.tvId.text = "ID: ${student.studentId} (${student.courseCode})"
        when (student.rfidStatus) {
            "ACTIVE" -> holder.tvRfid.text = "🟢 ACTIVE"
            "DISABLED" -> holder.tvRfid.text = "🟡 DISABLED"
            else -> holder.tvRfid.text = "🔴 Not Registered"
        }
        holder.itemView.setOnClickListener { clickListener(student) }
    }

    override fun getItemCount(): Int = items.size
}