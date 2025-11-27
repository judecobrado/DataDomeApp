package com.example.datadomeapp.canteen

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Base64
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.Spinner
import android.widget.Switch
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.datadomeapp.R
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.util.Locale

class AddEditMenuActivity : AppCompatActivity() {

    private lateinit var etName: EditText
    private lateinit var etPrice: EditText
    private lateinit var spinnerCategory: Spinner
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
                imageUri = uri
                val bitmap = getBitmapFromUri(uri)
                if (bitmap != null) {
                    ivPreview.setImageBitmap(bitmap)
                } else {
                    // Removed Toast
                    // Toast.makeText(this, "Failed to load or resize image.", Toast.LENGTH_SHORT).show()
                    showCriticalErrorDialog("Failed to load or resize image.", "Image Error")
                }
            } else {
                // Removed Toast
                // Toast.makeText(this, "Image selection cancelled.", Toast.LENGTH_SHORT).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.canteen_add_edit_menu)

        etName = findViewById(R.id.etMenuName)
        etPrice = findViewById(R.id.etMenuPrice)
        spinnerCategory = findViewById(R.id.spinnerCategory)
        switchAvailable = findViewById(R.id.switchAvailable)
        btnSave = findViewById(R.id.btnSaveMenu)
        btnUploadImage = findViewById(R.id.btnUploadImage)
        ivPreview = findViewById(R.id.ivMenuPreview)
        progressBar = findViewById(R.id.progressBar)

        val categoryList = resources.getStringArray(R.array.menu_categories).toList()
        val adapter = DisabledPlaceholderSpinnerAdapter(
            this,
            android.R.layout.simple_spinner_item, // Layout for the selected item view
            categoryList
        )
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) // Layout for the dropdown items
        spinnerCategory.adapter = adapter

        staffUid = auth.currentUser?.uid
        canteenName = intent.getStringExtra("canteenName")

        menuId = intent.getStringExtra("menuId")
        if (menuId != null) {
            supportActionBar?.title = "Edit Menu Item"
            loadMenuData(menuId!!)
        } else {
            supportActionBar?.title = "Add New Menu Item"
            // ✅ DEFAULT PRICE: I-set sa ₱1.00 ang default price para sa bagong item.
            etPrice.setText("1.00")
        }

        // 🟢 FOCUS LISTENER: Para sa automatic ₱1.00 adjustment at formatting pag-alis sa field
        etPrice.onFocusChangeListener = View.OnFocusChangeListener { v, hasFocus ->
            if (!hasFocus) {
                val priceText = etPrice.text.toString().trim()
                val parsedPrice = priceText.toDoubleOrNull()

                // 🚫 CHECK: Kung empty, null, o mas mababa sa 1.00, gawing 1.00
                if (parsedPrice == null || parsedPrice < 1.00) {
                    etPrice.setText("1.00")
                } else {
                    // Para masiguro na 2 decimal places ang format kapag umalis
                    etPrice.setText(String.format("%.2f", parsedPrice))
                }
            }
        }

        spinnerCategory.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                btnSave.isEnabled = validateInputFields()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {
                // Not strictly needed, but included for completeness
            }
        }

        // 🟢 REAL-TIME INPUT VALIDATION LOGIC
        val validationTextWatcher = object : TextWatcher {
            private var currentNameText: String = ""
            private var currentPriceText: String = ""

            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {
                if (etName.isFocused) {
                    currentNameText = s.toString()
                } else if (etPrice.isFocused) {
                    currentPriceText = s.toString()
                }
            }

            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
                // --- Price Validation (Existing Logic) ---
                if (etPrice.isFocused) {
                    val priceText = s.toString()
                    etPrice.removeTextChangedListener(this) // Pansamantalang i-alis ang listener

                    val parsedPrice = priceText.toDoubleOrNull()
                    var revert = false

                    // 1. Decimal Place Check (Hahadlang sa pag-input ng 3rd decimal place)
                    if (priceText.contains('.') && priceText.substringAfter('.').length > 2) {
                        revert = true
                        etPrice.error = "Only 2 decimal places allowed."
                        // Removed Toast
                        // Toast.makeText(this@AddEditMenuActivity, "Only 2 decimal places allowed.", Toast.LENGTH_SHORT).show()
                    }
                    // 2. Price Cap Check (Hahadlang sa ₱1000.00 at pataas)
                    else if (parsedPrice != null && parsedPrice >= 1000.00) {
                        revert = true
                        etPrice.error = "Price limit is ₱999.99."
                        // Removed Toast
                        // Toast.makeText(this@AddEditMenuActivity, "Price limit is ₱999.99.", Toast.LENGTH_SHORT).show()
                    }
                    // 3. Invalid Leading Zero Check (Hahadlang sa 00, 01, 000, etc.)
                    else if (priceText.startsWith("0") && priceText.length > 1 && !priceText.startsWith("0.")) {
                        revert = true
                        etPrice.error = "Cannot start with multiple zeros."
                        // Removed Toast
                        // Toast.makeText(this@AddEditMenuActivity, "Cannot start with multiple zeros.", Toast.LENGTH_SHORT).show()
                    }

                    if (revert) {
                        // Ibalik ang dating laman at cursor position
                        etPrice.setText(currentPriceText)
                        etPrice.setSelection(currentPriceText.length)
                    } else {
                        etPrice.error = null
                    }
                    etPrice.addTextChangedListener(this) // Ibalik ang listener
                }

                // --- Name Validation (NEW Logic) ---
                if (etName.isFocused) {
                    val nameText = s.toString()
                    etName.removeTextChangedListener(this)

                    // 1. No Numbers/Digits Check (Bawal ang numbers/digits)
                    if (nameText.any { it.isDigit() }) {
                        // Ibalik sa dating text na walang number
                        etName.setText(currentNameText)
                        etName.setSelection(currentNameText.length)
                        etName.error = "Menu name cannot contain numbers."
                        // Removed Toast
                        // Toast.makeText(this@AddEditMenuActivity, "Numbers are not allowed in the menu name.", Toast.LENGTH_SHORT).show()
                    } else {
                        etName.error = null
                        // 2. Max 50 Characters Check (Kapag lampas sa 50, hahadlang din)
                        if (nameText.length > 50) {
                            etName.setText(nameText.substring(0, 50))
                            etName.setSelection(50)
                            etName.error = "Menu name is limited to 50 characters."
                            // Removed Toast
                            // Toast.makeText(this@AddEditMenuActivity, "Menu name is limited to 50 characters.", Toast.LENGTH_SHORT).show()
                        }
                    }
                    etName.addTextChangedListener(this)
                }

                // I-update ang Save button status (lagi itong dapat gawin)
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
            // Removed Toast
            // Toast.makeText(this, "Error loading image: ${e.message}", Toast.LENGTH_LONG).show()
            showCriticalErrorDialog("Error loading image: ${e.message}", "Image Loading Error")
            null
        }
    }

    private fun bitmapToBase64(bitmap: Bitmap): String {
        val baos = ByteArrayOutputStream()
        // Reduced compression quality for faster upload/download and smaller database size
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
                    // I-format sa 2 decimal places ang presyo
                    etPrice.setText(if (priceValue != null) String.format("%.2f", priceValue) else "")
                    val category = doc.getString("category") ?: resources.getStringArray(R.array.menu_categories)[0]
                    val adapter = spinnerCategory.adapter as ArrayAdapter<String>
                    val categoryPosition = adapter.getPosition(category)
                    if (categoryPosition >= 0) {
                        spinnerCategory.setSelection(categoryPosition)
                    }
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
                // Removed Toast
                // Toast.makeText(this, "Failed to load menu data.", Toast.LENGTH_SHORT).show()
                showCriticalErrorDialog("Failed to load menu data from database.", "Data Load Error")
            }
    }

    private fun validateInputFields(): Boolean {
        val name = etName.text.toString().trim()
        val priceText = etPrice.text.toString().trim()

        etName.error = null
        etPrice.error = null

        var isValid = true

        // 1. Menu Name Validation (50 Characters Max, No Numbers)
        if (name.length < 3) {
            etName.error = "Name must be at least 3 characters long."
            isValid = false
        } else if (name.length > 50) {
            etName.error = "Name is limited to 50 characters."
            isValid = false
        } else if (name.any { it.isDigit() }) { // Final check for numbers just in case
            etName.error = "Menu name cannot contain numbers."
            isValid = false
        }

        // 2. Price Validation
        if (priceText.isEmpty()) {
            etPrice.error = "Price is required."
            isValid = false
        } else {
            val parsedPrice = priceText.toDoubleOrNull()

            // 🚫 FINAL VALIDATION: Price must be strictly >= ₱1.00 and <= ₱999.99
            if (parsedPrice == null || parsedPrice < 1.00) {
                etPrice.error = "Enter a valid price, minimum is ₱1.00."
                isValid = false
            } else if (parsedPrice >= 1000.00) {
                etPrice.error = "Price cannot be ₱1,000.00 or more."
                isValid = false
            }
            // Final check for decimal places
            else if (priceText.contains('.') && priceText.substringAfter('.').length > 2) {
                etPrice.error = "Price can only have up to two decimal places."
                isValid = false
            }
        }

        if (spinnerCategory.selectedItemPosition == 0) { // ⬅️ Change check to position 0
            isValid = false
        }

        return isValid
    }

    private fun validateAndSaveMenu() {
        if (!validateInputFields()) {
            return
        }

        // I-format ang presyo bago i-save para masigurado ang tamang format (min 1.00 enforced by focus listener/validation)
        val price = String.format("%.2f", etPrice.text.toString().trim().toDouble()).toDouble()
        val name = etName.text.toString().trim()

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
            // ✅ Stability Fix: Image conversion is done on the IO thread
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
            val category = spinnerCategory.selectedItem.toString()
            continueSaveLogic(formattedName, price, available, base64Image, uid, canteen, category)
        }
    }

    private fun continueSaveLogic(
        formattedName: String,
        price: Double,
        available: Boolean,
        base64Image: String,
        uid: String,
        canteen: String,
        category: String
    ) {
        if (menuId == null) {
            checkExistenceAndSave(formattedName, price, available, base64Image, uid, canteen, category)
        } else {
            val newCustomDocId = createDocumentId(canteen, formattedName)

            if (newCustomDocId != menuId) {
                handleNameChangeUpdate(menuId!!, formattedName, price, available, base64Image, uid, canteen, newCustomDocId, category)
            } else {
                saveToFirestore(formattedName, price, available, base64Image, uid, canteen, menuId!!, category)
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
        newDocId: String,
        category: String
    ) {
        firestore.collection("canteenMenu").document(newDocId).get()
            .addOnSuccessListener { doc ->
                if (doc.exists()) {
                    progressBar.visibility = View.GONE
                    btnSave.isEnabled = true
                    showCriticalErrorDialog("The new name '$newName' already exists as a separate item. Please choose a unique name.", "Name Conflict")
                } else {
                    saveToFirestore(newName, price, available, base64Image, staffUid, canteenName, newDocId, category, isNameChange = true)
                        .addOnSuccessListener {
                            firestore.collection("canteenMenu").document(oldDocId).delete()
                                .addOnSuccessListener {
                                    progressBar.visibility = View.GONE
                                    // Removed Toast
                                    // Toast.makeText(this, "Menu updated and name changed successfully!", Toast.LENGTH_LONG).show()
                                    // Use AlertDialog for successful completion of a complex operation
                                    AlertDialog.Builder(this).setTitle("Success")
                                        .setMessage("Menu updated and name changed successfully!")
                                        .setPositiveButton("OK") { _, _ -> finish() }
                                        .show()
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
                // Removed Toast
                // Toast.makeText(this, "Error checking new name existence: ${e.message}", Toast.LENGTH_SHORT).show()
                showCriticalErrorDialog("Error checking new name existence: ${e.message}", "Database Error")
            }
    }

    private fun checkExistenceAndSave(
        name: String,
        price: Double,
        available: Boolean,
        base64Image: String,
        staffUid: String,
        canteenName: String,
        category: String
    ) {
        val customDocId = createDocumentId(canteenName, name)

        firestore.collection("canteenMenu").document(customDocId).get()
            .addOnSuccessListener { doc ->
                if (doc.exists()) {
                    progressBar.visibility = View.GONE
                    btnSave.isEnabled = true
                    showCriticalErrorDialog("A menu item named '$name' already exists for your canteen. Please use the Edit feature to modify it.", "Duplicate Item")
                } else {
                    saveToFirestore(name, price, available, base64Image, staffUid, canteenName, customDocId, category)
                }
            }
            .addOnFailureListener { e ->
                progressBar.visibility = View.GONE
                btnSave.isEnabled = true
                // Removed Toast
                // Toast.makeText(this, "Error checking menu existence: ${e.message}", Toast.LENGTH_SHORT).show()
                showCriticalErrorDialog("Error checking menu existence: ${e.message}", "Database Error")
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
        category: String,
        isNameChange: Boolean = false
    ): com.google.android.gms.tasks.Task<Void> {
        val menuMap = hashMapOf(
            "name" to name,
            "price" to price,
            "available" to available,
            "imageUrl" to base64Image,
            "staffUid" to staffUid,
            "canteenName" to canteenName,
            "category" to category,
            "updatedAt" to com.google.firebase.Timestamp.now()
        )

        val task = firestore.collection("canteenMenu").document(docId).set(menuMap)

        task.addOnSuccessListener {
            if (!isNameChange) {
                progressBar.visibility = View.GONE
                // Removed Toast
                // Toast.makeText(this, "Menu saved successfully!", Toast.LENGTH_SHORT).show()
                // Use AlertDialog for successful completion
                AlertDialog.Builder(this).setTitle("Success")
                    .setMessage("Menu item saved successfully!")
                    .setPositiveButton("OK") { _, _ -> finish() }
                    .show()
            }
        }.addOnFailureListener { e ->
            progressBar.visibility = View.GONE
            btnSave.isEnabled = true
            showCriticalErrorDialog("An error occurred while saving the menu item. Error: ${e.message}", "Save Failed")
        }

        return task
    }
}

// Place this class at the bottom of the AddEditMenuActivity.kt file
class DisabledPlaceholderSpinnerAdapter(
    context: android.content.Context,
    resource: Int,
    private val items: List<String>
) : ArrayAdapter<String>(context, resource, items) {

    // 1. Physically prevents the user from selecting the item at position 0
    override fun isEnabled(position: Int): Boolean {
        return position != 0
    }

    // 2. Greys out the text for the placeholder in the dropdown list
    override fun getDropDownView(position: Int, convertView: View?, parent: android.view.ViewGroup): View {
        val view = super.getDropDownView(position, convertView, parent)
        // We use the default TextView ID (android.R.id.text1) used by simple_spinner_dropdown_item
        val tv = view.findViewById<android.widget.TextView>(android.R.id.text1)

        if (position == 0) {
            tv.setTextColor(android.graphics.Color.GRAY)
        } else {
            tv.setTextColor(android.graphics.Color.BLACK)
        }
        return view
    }
}