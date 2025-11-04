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
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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

    private var originalMenuName: String? = null

    private val imagePickerLauncher: ActivityResultLauncher<String> =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            if (uri != null) {
                // ✅ FIX: Removed takePersistableUriPermission to avoid SecurityException
                // No need for persistable permission since we process the image immediately.

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
        setContentView(R.layout.canteen_add_edit_menu)

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

        val validationTextWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
                btnSave.isEnabled = validateInputFields()
            }

            override fun afterTextChanged(s: Editable) {}
        }

        etName.addTextChangedListener(validationTextWatcher)
        etPrice.addTextChangedListener(validationTextWatcher)

        btnSave.isEnabled = validateInputFields()

        btnUploadImage.setOnClickListener { openGallery() }
        btnSave.setOnClickListener { validateAndSaveMenu() }
    }

    private fun openGallery() {
        imagePickerLauncher.launch("image/*")
    }

    // --- UTILITY FUNCTIONS ---

    private fun showCriticalErrorDialog(message: String, title: String = "Error") {
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show()
    }

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

    private fun bitmapToBase64(bitmap: Bitmap): String {
        val baos = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 50, baos)
        return Base64.encodeToString(baos.toByteArray(), Base64.DEFAULT)
    }

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
     * ✅ Stability Fix: Uses Locale.ROOT for consistent formatting.
     * Formats the name: Title Case (First letter caps, rest lowercase).
     */
    private fun formatMenuName(name: String): String {
        return name.toLowerCase(Locale.ROOT).split(' ').joinToString(" ") {
            // Using a simple check to capitalize the first letter of each word safely
            it.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() }
        }
    }

    /**
     * ✅ Stability Fix: Uses Locale.ROOT for consistent document ID generation.
     * Creates a clean document ID using the format: CanteenName_FoodName
     */
    private fun createDocumentId(canteenName: String, foodName: String): String {
        val cleanCanteen = canteenName.trim().replace("\\s+".toRegex(), "").toLowerCase(Locale.ROOT)
        // Replace non-alphanumeric characters with underscore to ensure valid Firestore ID
        val cleanFood = foodName.trim().replace("[^a-zA-Z0-9]".toRegex(), "_").toLowerCase(Locale.ROOT)
        return "${cleanCanteen}_${cleanFood}"
    }

    // --- MAIN LOGIC FUNCTIONS ---

    private fun loadMenuData(id: String) {
        firestore.collection("canteenMenu").document(id).get()
            .addOnSuccessListener { doc ->
                if (doc.exists()) {
                    val loadedName = doc.getString("name") ?: ""
                    etName.setText(loadedName)
                    originalMenuName = loadedName

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
                            existingBase64Image = null
                        }
                    }
                    btnSave.isEnabled = validateInputFields()
                }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to load menu data.", Toast.LENGTH_SHORT).show()
            }
    }

    private fun validateInputFields(): Boolean {
        val name = etName.text.toString().trim()
        val priceText = etPrice.text.toString().trim()

        etName.error = null
        etPrice.error = null

        var isValid = true

        if (name.length < 3 || name.length > 50) {
            etName.error = "Name must be 3 to 50 characters long."
            isValid = false
        }

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
            else if (priceText.contains('.') && priceText.substringAfter('.').length > 2) {
                etPrice.error = "Price can only have up to two decimal places."
                isValid = false
            }
        }

        return isValid
    }

    private fun validateAndSaveMenu() {
        if (!validateInputFields()) {
            return
        }

        val name = etName.text.toString().trim()
        val price = etPrice.text.toString().trim().toDouble()

        progressBar.visibility = View.VISIBLE
        btnSave.isEnabled = false

        val available = switchAvailable.isChecked
        val uid = staffUid
        val canteen = canteenName

        if (canteen.isNullOrEmpty()) {
            progressBar.visibility = View.GONE
            showCriticalErrorDialog("Canteen name is missing. Cannot save menu.", "Data Error")
            btnSave.isEnabled = true
            return
        }

        if (uid == null) {
            progressBar.visibility = View.GONE
            showCriticalErrorDialog("User session expired or invalid. Please re-login.", "Authentication Error")
            return
        }

        val formattedName = formatMenuName(name)

        lifecycleScope.launch {

            val base64Image = withContext(Dispatchers.IO) {
                when {
                    imageUri != null -> {
                        val bitmap = getBitmapFromUri(imageUri!!)
                        if (bitmap != null) bitmapToBase64(bitmap) else ""
                    }
                    existingBase64Image != null -> existingBase64Image!!
                    else -> ""
                }
            }

            continueSaveLogic(formattedName, price, available, base64Image, uid, canteen)
        }
    }

    private fun continueSaveLogic(
        formattedName: String,
        price: Double,
        available: Boolean,
        base64Image: String,
        uid: String,
        canteen: String
    ) {
        if (menuId == null) {
            checkExistenceAndSave(formattedName, price, available, base64Image, uid, canteen)
        } else {
            val newCustomDocId = createDocumentId(canteen, formattedName)

            if (newCustomDocId != menuId) {
                handleNameChangeUpdate(menuId!!, formattedName, price, available, base64Image, uid, canteen, newCustomDocId)
            } else {
                saveToFirestore(formattedName, price, available, base64Image, uid, canteen, menuId!!)
            }
        }
    }

    private fun handleNameChangeUpdate(
        oldDocId: String,
        newName: String,
        price: Double,
        available: Boolean,
        base64Image: String,
        staffUid: String,
        canteenName: String,
        newDocId: String
    ) {
        firestore.collection("canteenMenu").document(newDocId).get()
            .addOnSuccessListener { doc ->
                if (doc.exists()) {
                    progressBar.visibility = View.GONE
                    btnSave.isEnabled = true
                    showCriticalErrorDialog("The new name '$newName' already exists as a separate item. Please choose a unique name.", "Name Conflict")
                } else {
                    saveToFirestore(newName, price, available, base64Image, staffUid, canteenName, newDocId, isNameChange = true)
                        .addOnSuccessListener {
                            firestore.collection("canteenMenu").document(oldDocId).delete()
                                .addOnSuccessListener {
                                    progressBar.visibility = View.GONE
                                    Toast.makeText(this, "Menu updated and name changed successfully!", Toast.LENGTH_LONG).show()
                                    finish()
                                }
                                .addOnFailureListener { e ->
                                    progressBar.visibility = View.GONE
                                    showCriticalErrorDialog("Data saved but failed to delete old menu item. Please contact administrator.", "Partial Update Error")
                                    finish()
                                }
                        }
                }
            }
            .addOnFailureListener { e ->
                progressBar.visibility = View.GONE
                btnSave.isEnabled = true
                Toast.makeText(this, "Error checking new name existence: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

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
                    progressBar.visibility = View.GONE
                    btnSave.isEnabled = true
                    showCriticalErrorDialog("A menu item named '$name' already exists for your canteen. Please use the Edit feature to modify it.", "Duplicate Item")
                } else {
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
        docId: String,
        isNameChange: Boolean = false
    ): com.google.android.gms.tasks.Task<Void> {
        val menuMap = hashMapOf(
            "name" to name,
            "price" to price,
            "available" to available,
            "imageUrl" to base64Image,
            "staffUid" to staffUid,
            "canteenName" to canteenName
        )

        val task = firestore.collection("canteenMenu").document(docId).set(menuMap)

        task.addOnSuccessListener {
            if (!isNameChange) {
                progressBar.visibility = View.GONE
                Toast.makeText(this, "Menu saved successfully!", Toast.LENGTH_SHORT).show()
                finish()
            }
        }.addOnFailureListener { e ->
            progressBar.visibility = View.GONE
            btnSave.isEnabled = true
            showCriticalErrorDialog("An error occurred while saving the menu item. Error: ${e.message}", "Save Failed")
        }

        return task
    }
}