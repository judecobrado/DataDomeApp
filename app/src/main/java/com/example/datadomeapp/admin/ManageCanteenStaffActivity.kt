package com.example.datadomeapp.admin

import android.app.Activity
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.util.Base64
import android.util.Log
import android.view.View // Import for View.GONE and View.VISIBLE
import android.widget.*
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.example.datadomeapp.R
import com.example.datadomeapp.models.CanteenStaff
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.io.ByteArrayOutputStream
import com.example.datadomeapp.LoginActivity // Import LoginActivity for redirection

// Tag para sa Logcat
private const val TAG = "CanteenStaffActivity"

// Hardcoded admin credentials (must match LoginActivity)
private val adminEmail = "q"
private val adminPassword = "q"

class ManageCanteenStaffActivity : AppCompatActivity() {

    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()
    private val staffCollection = firestore.collection("canteen_staff")
    private val userCollection = firestore.collection("users")

    private val staffList = ArrayList<CanteenStaff>()
    private lateinit var adapter: ArrayAdapter<String>
    private var selectedStaffIndex = -1
    private var isEditing = false

    private val PICK_IMAGE_REQUEST = 101
    private var selectedImageUri: Uri? = null

    // View initialization is crucial for avoiding NullPointerExceptionss on button clicks
    private lateinit var etCanteenName: EditText
    private lateinit var etFirstName: EditText
    private lateinit var etMiddleName: EditText
    private lateinit var etLastName: EditText
    private lateinit var etEmail: EditText
    private lateinit var etPassword: EditText
    private lateinit var btnAdd: Button
    private lateinit var btnDelete: Button
    private lateinit var btnEdit: Button
    private lateinit var btnBack: Button
    private lateinit var btnSelectImage: Button
    private lateinit var ivStoreImage: ImageView
    private lateinit var listView: ListView
    private lateinit var btnCancelEdit: Button // Declared the Cancel button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.admin_manage_canteenstaff)

        // Initialize views
        etCanteenName = findViewById(R.id.etCanteenName)
        etFirstName = findViewById(R.id.etFirstName)
        etMiddleName = findViewById(R.id.etMiddleName)
        etLastName = findViewById(R.id.etLastName)
        etEmail = findViewById(R.id.etEmail)
        etPassword = findViewById(R.id.etPassword)
        btnAdd = findViewById(R.id.btnAddStaff)
        btnDelete = findViewById(R.id.btnDeleteStaff)
        btnEdit = findViewById(R.id.btnEditStaff)
        btnBack = findViewById(R.id.btnBackStaff)
        btnSelectImage = findViewById(R.id.btnSelectImage)
        ivStoreImage = findViewById(R.id.ivStoreImage)
        listView = findViewById(R.id.lvStaff)
        btnCancelEdit = findViewById(R.id.btnCancelEdit) // Initialized the Cancel button

        // Setup ListView
        adapter = ArrayAdapter(this, android.R.layout.simple_list_item_single_choice, ArrayList())
        listView.adapter = adapter
        listView.choiceMode = ListView.CHOICE_MODE_SINGLE

        loadStaffOnce()

        // Set initial visibility for Cancel button
        btnCancelEdit.visibility = View.GONE

        // ⭐ ERROR FIX: Updated OnItemClickListener
        listView.setOnItemClickListener { _, _, position, _ ->
            selectedStaffIndex = position
            // 1. Load data from the selected item into the input fields
            loadSelectedStaffToFields(position)

            // 2. Reset the form state, but keep the list selection highlighted
            resetFormAndState(keepSelection = true)
        }

        btnSelectImage.setOnClickListener { selectImage() }
        btnEdit.setOnClickListener { editStaff() }
        btnAdd.setOnClickListener { addOrUpdateStaff() }
        btnDelete.setOnClickListener { deleteStaff() }
        btnBack.setOnClickListener { finish() }
        btnCancelEdit.setOnClickListener { cancelEdit() }
    }

    private fun selectImage() {
        val intent = Intent(Intent.ACTION_GET_CONTENT)
        intent.type = "image/*"
        startActivityForResult(intent, PICK_IMAGE_REQUEST)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == PICK_IMAGE_REQUEST && resultCode == Activity.RESULT_OK) {
            selectedImageUri = data?.data
            selectedImageUri?.let {
                // Display selected image
                try {
                    val inputStream = contentResolver.openInputStream(it)
                    val bitmap = BitmapFactory.decodeStream(inputStream)
                    ivStoreImage.setImageBitmap(bitmap)
                } catch (e: Exception) {
                    Log.e(TAG, "Image loading failed: ${e.message}", e)
                    Toast.makeText(this, "Failed to load image: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    // ⭐ NEW HELPER FUNCTION TO LOAD DATA (Resolves "Unresolved reference" error)
    // -------------------------------------------------------------------------
    /**
     * Loads the data of the staff at the given index into the input fields
     * without setting the form into 'isEditing = true' state.
     */
    private fun loadSelectedStaffToFields(position: Int) {
        if (position != -1 && position < staffList.size) {
            clearInputFields() // Clear fields first

            val staff = staffList[position]

            // Populate fields
            etFirstName.setText(staff.firstName)
            etMiddleName.setText(staff.middleName)
            etLastName.setText(staff.lastName)
            etEmail.setText(staff.email)
            etCanteenName.setText(staff.canteenName)

            // Show current image
            selectedImageUri = null
            if (staff.storeImageUrl.isNotEmpty()) {
                try {
                    val bytes = Base64.decode(staff.storeImageUrl, Base64.DEFAULT)
                    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    ivStoreImage.setImageBitmap(bitmap)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to decode Base64 image: ${e.message}", e)
                    ivStoreImage.setImageDrawable(null)
                    Toast.makeText(this, "Failed to load existing image (Corrupt Data).", Toast.LENGTH_LONG).show()
                }
            } else ivStoreImage.setImageDrawable(null)

            // Lock fields for display purposes
            etEmail.isEnabled = false
            etPassword.setText("********") // hide password
            etPassword.isEnabled = false

        } else {
            clearInputFields()
        }
    }


    private fun editStaff() {
        // CRITICAL CHECK: Ensure the selected index is valid and within the list bounds
        if (selectedStaffIndex != -1 && selectedStaffIndex < staffList.size) {

            // Use the helper function to load the data
            loadSelectedStaffToFields(selectedStaffIndex)

            // Set the editing state
            isEditing = true
            btnAdd.text = "Save Changes"

            // Unlock fields for editing, but keep password locked
            etEmail.isEnabled = false
            etPassword.isEnabled = false

            // Manage button visibility
            btnDelete.visibility = View.GONE
            btnEdit.visibility = View.GONE
            btnCancelEdit.visibility = View.VISIBLE

        } else {
            Toast.makeText(this, "Select a staff to edit", Toast.LENGTH_SHORT).show()
            resetFormAndState()
        }
    }

    private fun cancelEdit() {
        Toast.makeText(this, "Edit cancelled", Toast.LENGTH_SHORT).show()

        // Temporarily store the index of the staff member whose data was loaded
        val staffIndexToReload = selectedStaffIndex

        // 1. Reset the form state (sets isEditing=false, restores buttons, clears input fields)
        // We call resetFormAndState() without 'keepSelection = true' to fully reset the edit state.
        resetFormAndState()

        // 2. Re-select and re-load the original staff data (if a staff member was originally selected)
        if (staffIndexToReload != -1) {
            selectedStaffIndex = staffIndexToReload // Restore the index
            loadSelectedStaffToFields(selectedStaffIndex) // Load the original data
            listView.setItemChecked(selectedStaffIndex, true) // Restore the selection visual
        }
    }

    private fun addOrUpdateStaff() {
        val firstName = etFirstName.text.toString().trim()
        val middleName = etMiddleName.text.toString().trim()
        val lastName = etLastName.text.toString().trim()
        val email = etEmail.text.toString().trim()
        var password = etPassword.text.toString().trim()
        val canteenName = etCanteenName.text.toString().trim()

        if (firstName.isEmpty() || lastName.isEmpty() || email.isEmpty() || canteenName.isEmpty()) {
            Toast.makeText(this, "Fill all required fields", Toast.LENGTH_SHORT).show()
            return
        }

        // --- IMAGE HANDLING ---
        val base64Image = selectedImageUri?.let { compressToBase64(it) }
            ?: if (isEditing && selectedStaffIndex != -1 && selectedStaffIndex < staffList.size) staffList[selectedStaffIndex].storeImageUrl else ""

        if (isEditing && selectedStaffIndex != -1 && selectedStaffIndex < staffList.size) {
            // =================================================================
            // 1. UPDATE EXISTING STAFF (Firestore update)
            // =================================================================
            val staff = staffList[selectedStaffIndex]
            val uid = staff.uid
            val staffId = staff.canteenStaffId

            val updatedStaff = CanteenStaff(
                email = email,
                role = "canteen_staff",
                canteenName = canteenName,
                firstName = firstName,
                middleName = middleName,
                lastName = lastName,
                uid = uid,
                storeImageUrl = base64Image,
                canteenStaffId = staffId
            )

            staffCollection.document(staffId).set(updatedStaff)
                .addOnSuccessListener {
                    Log.d(TAG, "Staff document updated in canteen_staff for ID: $staffId")
                    uid?.let { userId ->
                        userCollection.document(userId).update(
                            mapOf(
                                "email" to email,
                                "canteenName" to canteenName
                            )
                        )
                            .addOnSuccessListener {
                                Toast.makeText(this, "Staff updated successfully!", Toast.LENGTH_SHORT).show()
                                resetFormAndState() // Updated call
                                loadStaffOnce()
                            }
                            .addOnFailureListener { e ->
                                Log.e(TAG, "Failed to update user document (User Collection) for UID: $userId", e)
                                Toast.makeText(this, "Update failed in User Collection: ${e.message}", Toast.LENGTH_LONG).show()
                            }
                    }
                }
                .addOnFailureListener { e ->
                    // Show error on failure
                    Log.e(TAG, "Failed to update staff document (Canteen Staff Collection) for ID: $staffId", e)
                    Toast.makeText(this, "Failed to update staff in Firestore: ${e.message}", Toast.LENGTH_LONG).show()
                }

        } else {
            // =================================================================
            // 2. ADD NEW STAFF (Firebase Auth + Firestore)
            // =================================================================

            // --- Password Check for New Staff ---
            if (password.isEmpty()) {
                password = generateRandomPassword()
            }
            if (password.length < 6) {
                Toast.makeText(this, "Password must be at least 6 characters long", Toast.LENGTH_SHORT).show()
                return
            }
            val finalPassword = password
            // --- End Password Check ---

            generateNextStaffId { staffId ->
                auth.createUserWithEmailAndPassword(email, finalPassword)
                    .addOnSuccessListener { result ->
                        val uid = result.user?.uid
                        if (uid == null) {
                            Log.e(TAG, "Auth successful but UID is null.")
                            Toast.makeText(this, "Failed to get user ID after creation.", Toast.LENGTH_SHORT).show()
                            return@addOnSuccessListener
                        }
                        Log.d(TAG, "Auth user created successfully with UID: $uid")

                        val newStaff = CanteenStaff(
                            email = email,
                            role = "canteen_staff",
                            canteenName = canteenName,
                            firstName = firstName,
                            middleName = middleName,
                            lastName = lastName,
                            uid = uid,
                            storeImageUrl = base64Image,
                            canteenStaffId = staffId
                        )

                        // 1. Add to canteen_staff collection
                        staffCollection.document(staffId).set(newStaff)
                            .addOnSuccessListener {
                                // 2. Add to users collection
                                userCollection.document(uid).set(
                                    mapOf(
                                        "uid" to uid,
                                        "email" to email,
                                        "role" to "canteen_staff",
                                        "canteenStaffId" to staffId,
                                        "canteenName" to canteenName
                                    )
                                )
                                    .addOnSuccessListener {
                                        // ⭐ CRITICAL FIX IMPLEMENTED HERE: Sign Admin back in
                                        auth.signInWithEmailAndPassword(adminEmail, adminPassword)
                                            .addOnCompleteListener { adminTask ->
                                                if (adminTask.isSuccessful) {
                                                    // Admin session restored successfully
                                                    Toast.makeText(this, "Staff added successfully! Admin session restored. Password: $finalPassword", Toast.LENGTH_LONG).show()
                                                    resetFormAndState()
                                                    loadStaffOnce()
                                                } else {
                                                    // This is a critical error! Admin must re-login manually.
                                                    Log.e(TAG, "Admin re-login failed: ${adminTask.exception?.message}")
                                                    Toast.makeText(this, "Staff added, but Admin session LOST. Please log in again.", Toast.LENGTH_LONG).show()
                                                    // Redirect to login screen
                                                    startActivity(Intent(this, LoginActivity::class.java))
                                                    finishAffinity()
                                                }
                                            }
                                    }
                                    .addOnFailureListener { e ->
                                        Log.e(TAG, "Failed to write user document (User Collection): ${e.message}", e)
                                        // If User Collection fails, attempt to delete the Auth user and Canteen Staff entry
                                        staffCollection.document(staffId).delete()
                                        result.user?.delete()
                                        Toast.makeText(this, "FATAL ERROR: Failed to finalize account. User and Staff entry deleted.", Toast.LENGTH_LONG).show()
                                    }
                            }
                            .addOnFailureListener { e ->
                                Log.e(TAG, "Failed to write staff document (Canteen Staff Collection): ${e.message}", e)
                                // If Firestore (staff collection) fails, attempt to delete the Auth user for cleanup
                                result.user?.delete()
                                Toast.makeText(this, "Firestore Failed: ${e.message}. Auth user deleted.", Toast.LENGTH_LONG).show()
                            }
                    }
                    .addOnFailureListener { e ->
                        // Show error on Auth failure (e.g., email already exists, network error, weak password)
                        Log.e(TAG, "Firebase Auth failed: ${e.message}", e)
                        Toast.makeText(this, "Failed to create account (Auth): ${e.message}", Toast.LENGTH_LONG).show()
                    }
            }
        }
    }

    private fun deleteStaff() {
        // CRITICAL CHECK: Ensure the selected index is valid and within the list bounds
        if (selectedStaffIndex != -1 && selectedStaffIndex < staffList.size) {
            val staff = staffList[selectedStaffIndex]
            val staffId = staff.canteenStaffId
            val uid = staff.uid

            staffCollection.document(staffId).delete().addOnSuccessListener {
                Log.d(TAG, "Staff document deleted in canteen_staff for ID: $staffId")
                // Delete user from the 'users' collection
                uid?.let { userId ->
                    userCollection.document(userId).delete()
                        .addOnSuccessListener {
                            Toast.makeText(this, "Staff deleted successfully!", Toast.LENGTH_SHORT).show()
                            resetFormAndState() // Updated call
                            loadStaffOnce()
                        }
                        .addOnFailureListener { e ->
                            Log.e(TAG, "Failed to delete user document (User Collection) for UID: $userId", e)
                            Toast.makeText(this, "Deleted from Canteen Staff, but failed to delete from Users: ${e.message}", Toast.LENGTH_LONG).show()
                            resetFormAndState() // Updated call
                            loadStaffOnce() // I-refresh pa rin ang listahan
                        }
                } ?: run {
                    // Walang UID, tapos na ang pag-delete.
                    Toast.makeText(this, "Staff deleted successfully (No linked User).", Toast.LENGTH_SHORT).show()
                    resetFormAndState() // Updated call
                    loadStaffOnce()
                }

            }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Failed to delete staff document (Canteen Staff Collection) for ID: $staffId", e)
                    Toast.makeText(this, "Failed to delete staff: ${e.message}", Toast.LENGTH_LONG).show()
                }
        } else Toast.makeText(this, "Select a staff to delete", Toast.LENGTH_SHORT).show()
    }

    // New helper function to clear only the input views
    private fun clearInputFields() {
        etFirstName.text.clear()
        etMiddleName.text.clear()
        etLastName.text.clear()
        etEmail.text.clear()
        etPassword.text.clear()
        etCanteenName.text.clear()
        selectedImageUri = null
        ivStoreImage.setImageDrawable(null)
        etEmail.isEnabled = true
        etPassword.isEnabled = true
    }

    // --------------------------------------------------------------------------------
    // ⭐ UPDATED resetFormAndState (Resolves "No parameter with name 'keepSelection'" error)
    // --------------------------------------------------------------------------------
    /**
     * Resets the form to the default 'Add Staff' state.
     * @param keepSelection If true, the visual selection in the ListView is maintained.
     */
    private fun resetFormAndState(keepSelection: Boolean = false) {
        clearInputFields()
        isEditing = false
        btnAdd.text = "Add Staff"

        // Restore button visibility
        btnDelete.visibility = View.VISIBLE
        btnEdit.visibility = View.VISIBLE
        btnCancelEdit.visibility = View.GONE

        if (!keepSelection) {
            selectedStaffIndex = -1 // Critical state reset
            listView.clearChoices() // Clear selection visual
        } else {
            // Keep the selected index and ensure the item is checked visually
            if (selectedStaffIndex != -1) {
                listView.setItemChecked(selectedStaffIndex, true)
            }
        }
        adapter.notifyDataSetChanged()
    }

    private fun loadStaffOnce() {
        staffCollection.get().addOnSuccessListener { snapshot ->
            staffList.clear()
            val displayList = ArrayList<String>()
            snapshot.documents.forEach { doc ->
                val staff = try {
                    doc.toObject(CanteenStaff::class.java)
                } catch (e: Exception) {
                    Log.e(TAG, "Error deserializing staff document ${doc.id}: ${e.message}", e)
                    null
                }

                staff?.let {
                    staffList.add(it)
                    displayList.add("${it.firstName} ${it.middleName} ${it.lastName} (${it.canteenName})")
                }
            }
            adapter.clear()
            adapter.addAll(displayList)
            adapter.notifyDataSetChanged()
            // Reset index and selection visual after loading new data
            selectedStaffIndex = -1
            listView.clearChoices()
            resetFormAndState() // Ensure the form is cleared after a fresh load
        }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to load staff list from Firestore: ${e.message}", e)
                Toast.makeText(this, "Failed to load staff list: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }

    private fun compressToBase64(uri: Uri): String {
        return try {
            contentResolver.openInputStream(uri)?.use { inputStream ->
                val bitmap = BitmapFactory.decodeStream(inputStream)
                val baos = ByteArrayOutputStream()
                // Compress to 50% quality to save space
                bitmap.compress(Bitmap.CompressFormat.JPEG, 50, baos)
                Base64.encodeToString(baos.toByteArray(), Base64.DEFAULT)
            } ?: ""
        } catch (e: Exception) {
            Log.e(TAG, "Image compression failed: ${e.message}", e)
            Toast.makeText(this, "Image compression failed: ${e.message}", Toast.LENGTH_LONG).show()
            ""
        }
    }

    private fun generateRandomPassword(length: Int = 8): String {
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789@#\$%^&*"
        return (1..length).map { chars.random() }.joinToString("")
    }

    private fun generateNextStaffId(callback: (String) -> Unit) {
        staffCollection.get().addOnSuccessListener { snapshot ->
            var lastNumber = 0
            snapshot.documents.forEach { doc ->
                val id = doc.getString("canteenStaffId") ?: ""
                if (id.startsWith("CS-")) {
                    val num = id.substringAfter("CS-").toIntOrNull() ?: 0
                    if (num > lastNumber) lastNumber = num
                }
            }
            val newId = "CS-" + (lastNumber + 1).toString().padStart(3, '0')
            callback(newId)
        }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to generate staff ID: ${e.message}", e)
                Toast.makeText(this, "Failed to generate staff ID: ${e.message}", Toast.LENGTH_LONG).show()
                callback("CS-000")
            }
    }
}
