package com.example.datadomeapp.canteen

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Base64
import android.view.View
import android.widget.*
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AlertDialog
import com.example.datadomeapp.R
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.io.ByteArrayOutputStream
import java.util.*

class AddEditMenuActivity : AppCompatActivity() {

    private lateinit var etName: EditText
    private lateinit var etPrice: EditText
    private lateinit var switchAvailable: Switch
    private lateinit var btnSave: Button
    private lateinit var btnUploadImage: Button
    private lateinit var ivPreview: ImageView
    private lateinit var progressBar: ProgressBar

    private var imageUri: Uri? = null
    private var menuId: String? = null
    private var existingBase64Image: String? = null

    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private var staffUid: String? = null
    private var canteenName: String? = null

    private val imagePickerLauncher: ActivityResultLauncher<String> =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            if (uri != null) {
                // Grant temporary read permission
                val flag = Intent.FLAG_GRANT_READ_URI_PERMISSION
                contentResolver.takePersistableUriPermission(uri, flag)

                imageUri = uri
                val bitmap = getBitmapFromUri(uri)
                if (bitmap != null) {
                    ivPreview.setImageBitmap(bitmap)
                } else {
                    Toast.makeText(this, "Failed to load or resize image.", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(this, "Image selection cancelled.", Toast.LENGTH_SHORT).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Ensure you have R.layout.canteen_add_edit_menu defined in your project
        setContentView(R.layout.canteen_add_edit_menu)

        // Ensure these IDs exist in your XML layout
        etName = findViewById(R.id.etMenuName)
        etPrice = findViewById(R.id.etMenuPrice)
        switchAvailable = findViewById(R.id.switchAvailable)
        btnSave = findViewById(R.id.btnSaveMenu)
        btnUploadImage = findViewById(R.id.btnUploadImage)
        ivPreview = findViewById(R.id.ivMenuPreview)
        progressBar = findViewById(R.id.progressBar)

        staffUid = auth.currentUser?.uid
        canteenName = intent.getStringExtra("canteenName")

        menuId = intent.getStringExtra("menuId")
        if (menuId != null) {
            supportActionBar?.title = "Edit Menu Item"
            loadMenuData(menuId!!)
        } else {
            supportActionBar?.title = "Add New Menu Item"
        }

        // --- UPDATED: Text Watcher updates button state immediately ---
        val validationTextWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
                // Perform validation check whenever text changes and update the button state
                btnSave.isEnabled = validateInputFields()
            }

            override fun afterTextChanged(s: Editable) {}
        }

        etName.addTextChangedListener(validationTextWatcher)
        etPrice.addTextChangedListener(validationTextWatcher)

        // NEW: Set the initial state of the Save button based on the data loaded
        btnSave.isEnabled = validateInputFields()
        // ------------------------------------------------------------------

        btnUploadImage.setOnClickListener { openGallery() }
        btnSave.setOnClickListener { validateAndSaveMenu() }
    }

    private fun openGallery() {
        imagePickerLauncher.launch("image/*")
    }

    // --- UTILITY FUNCTIONS ---

    /**
     * Shows a non-blocking dialog for critical errors or business logic failures.
     */
    private fun showCriticalErrorDialog(message: String, title: String = "Error") {
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show()
    }

    /**
     * Safely decodes and samples an image from a Uri to prevent OutOfMemory errors.
     */
    private fun getBitmapFromUri(uri: Uri): Bitmap? {
        return try {
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, options)
            }

            val targetSize = 800
            var inSampleSize = 1
            if (options.outHeight > targetSize || options.outWidth > targetSize) {
                val halfHeight: Int = options.outHeight / 2
                val halfWidth: Int = options.outWidth / 2
                while (halfHeight / inSampleSize >= targetSize && halfWidth / inSampleSize >= targetSize) {
                    inSampleSize *= 2
                }
            }

            val finalOptions = BitmapFactory.Options().apply {
                this.inSampleSize = inSampleSize
                inJustDecodeBounds = false
            }

            contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, finalOptions)
            }

        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Error loading image: ${e.message}", Toast.LENGTH_LONG).show()
            null
        }
    }

    /**
     * Converts a Bitmap to a Base64 string, using JPEG compression at 50% quality.
     */
    private fun bitmapToBase64(bitmap: Bitmap): String {
        val baos = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 50, baos)
        return Base64.encodeToString(baos.toByteArray(), Base64.DEFAULT)
    }

    /**
     * Converts a Base64 string back to a Bitmap, with error handling.
     */
    private fun base64ToBitmap(base64: String): Bitmap? {
        return try {
            val bytes = Base64.decode(base64, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        } catch (e: IllegalArgumentException) {
            showCriticalErrorDialog("Invalid image data format found in database.", "Data Error")
            e.printStackTrace()
            null
        }
    }

    /**
     * Formats the name: Title Case (First letter caps, rest lowercase).
     */
    private fun formatMenuName(name: String): String {
        return name.toLowerCase(Locale.getDefault()).split(' ').joinToString(" ") {
            it.capitalize(Locale.getDefault())
        }
    }

    /**
     * Creates a clean document ID using the format: CanteenName_FoodName
     */
    private fun createDocumentId(canteenName: String, foodName: String): String {
        val cleanCanteen = canteenName.trim().replace("\\s+".toRegex(), "").toLowerCase(Locale.getDefault())
        val cleanFood = foodName.trim().replace("\\s+".toRegex(), "").toLowerCase(Locale.getDefault())
        return "${cleanCanteen}_${cleanFood}"
    }

    // --- MAIN LOGIC FUNCTIONS ---

    private fun loadMenuData(id: String) {
        firestore.collection("canteenMenu").document(id).get()
            .addOnSuccessListener { doc ->
                if (doc.exists()) {
                    etName.setText(doc.getString("name"))
                    val priceValue = doc.getDouble("price")
                    etPrice.setText(if (priceValue != null) String.format("%.2f", priceValue) else "")
                    switchAvailable.isChecked = doc.getBoolean("available") ?: true

                    val base64Image = doc.getString("imageUrl")
                    if (!base64Image.isNullOrEmpty()) {
                        existingBase64Image = base64Image
                        val bitmap = base64ToBitmap(base64Image)
                        if (bitmap != null) {
                            ivPreview.setImageBitmap(bitmap)
                        } else {
                            existingBase64Image = null // Corrupted image, don't try to save it
                        }
                    }
                    // Re-validate to set initial button state after data loads
                    btnSave.isEnabled = validateInputFields()
                }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to load menu data.", Toast.LENGTH_SHORT).show()
            }
    }

    /**
     * Performs all input validation and sets inline errors.
     * @return true if all fields are valid, false otherwise.
     * * Note: This function provides the real-time inline error feedback.
     */
    private fun validateInputFields(): Boolean {
        val name = etName.text.toString().trim()
        val priceText = etPrice.text.toString().trim()

        // --- Reset Inline Errors ---
        etName.error = null
        etPrice.error = null

        var isValid = true

        // --- NAME VALIDATION (Inline Error) ---
        if (name.length < 3 || name.length > 50) {
            etName.error = "Name must be 3 to 50 characters long."
            isValid = false
        } else if (name.any { it.isDigit() }) {
            etName.error = "Name cannot contain numbers."
            isValid = false
        }
        // ------------------------------------

        // --- PRICE VALIDATION (Inline Error) ---
        if (priceText.isEmpty()) {
            etPrice.error = "Price is required."
            isValid = false
        } else {
            val parsedPrice = priceText.toDoubleOrNull()
            if (parsedPrice == null || parsedPrice <= 0) {
                etPrice.error = "Enter a valid positive price."
                isValid = false
            } else if (parsedPrice > 1000.00) {
                etPrice.error = "Price cannot exceed ₱1,000.00."
                isValid = false
            }
            // Check for decimal precision (max two digits after the decimal point)
            else if (priceText.contains('.') && priceText.substringAfter('.').length > 2) {
                etPrice.error = "Price can only have up to two decimal places."
                isValid = false
            }
        }
        // ------------------------------------

        return isValid
    }

    private fun validateAndSaveMenu() {
        // Step 1: Run validation again just before saving (final safeguard).
        if (!validateInputFields()) {
            return
        }

        // Fields are valid. Get final values.
        val name = etName.text.toString().trim()
        val price = etPrice.text.toString().trim().toDouble()

        // --- Critical Checks ---
        progressBar.visibility = View.VISIBLE
        btnSave.isEnabled = false // Disable again to prevent double-click during network call

        val available = switchAvailable.isChecked
        val uid = staffUid
        val canteen = canteenName ?: "UnknownCanteen"

        if (uid == null) {
            progressBar.visibility = View.GONE
            showCriticalErrorDialog("User session expired or invalid. Please re-login.", "Authentication Error")
            return
        }

        // --- Formatting ---
        val formattedName = formatMenuName(name)

        // --- Image Encoding ---
        val base64Image = when {
            imageUri != null -> {
                val bitmap = getBitmapFromUri(imageUri!!)
                if (bitmap != null) bitmapToBase64(bitmap) else ""
            }
            existingBase64Image != null -> existingBase64Image!!
            else -> ""
        }

        // --- Save Logic ---
        if (menuId == null) {
            // Check existence for new items
            checkExistenceAndSave(formattedName, price, available, base64Image, uid, canteen)
        } else {
            // Update existing item
            saveToFirestore(formattedName, price, available, base64Image, uid, canteen, menuId!!)
        }
    }

    /**
     * Checks if a new menu item already exists by its custom ID before saving.
     */
    private fun checkExistenceAndSave(
        name: String,
        price: Double,
        available: Boolean,
        base64Image: String,
        staffUid: String,
        canteenName: String
    ) {
        val customDocId = createDocumentId(canteenName, name)

        firestore.collection("canteenMenu").document(customDocId).get()
            .addOnSuccessListener { doc ->
                if (doc.exists()) {
                    // Item exists: Use Dialog
                    progressBar.visibility = View.GONE
                    btnSave.isEnabled = true
                    showCriticalErrorDialog("A menu item named '$name' already exists for your canteen. Please use the Edit feature to modify it.", "Duplicate Item")
                } else {
                    // Document does not exist: Safe to save as a new item
                    saveToFirestore(name, price, available, base64Image, staffUid, canteenName, customDocId)
                }
            }
            .addOnFailureListener { e ->
                progressBar.visibility = View.GONE
                btnSave.isEnabled = true
                Toast.makeText(this, "Error checking menu existence: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun saveToFirestore(
        name: String,
        price: Double,
        available: Boolean,
        base64Image: String,
        staffUid: String,
        canteenName: String,
        docId: String
    ) {
        val menuMap = hashMapOf(
            "name" to name,
            "price" to price,
            "available" to available,
            "imageUrl" to base64Image,
            "staffUid" to staffUid,
            "canteenName" to canteenName
        )

        firestore.collection("canteenMenu").document(docId).set(menuMap)
            .addOnSuccessListener {
                progressBar.visibility = View.GONE
                Toast.makeText(this, "Menu saved successfully!", Toast.LENGTH_SHORT).show()
                finish()
            }.addOnFailureListener { e ->
                progressBar.visibility = View.GONE
                btnSave.isEnabled = true
                // Network/Database Failure: Use Dialog for critical technical errors
                showCriticalErrorDialog("An error occurred while saving the menu item. Please check your connection or image size (max 1MB). Error: ${e.message}", "Save Failed")
            }
    }
}
