package com.example.datadomeapp.canteen

import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.example.datadomeapp.R
import com.google.firebase.firestore.FirebaseFirestore
import com.squareup.picasso.Picasso
import java.io.File
import java.io.FileOutputStream
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
    private var existingImagePath: String? = null
    private var menuId: String? = null

    private val firestore = FirebaseFirestore.getInstance()

    private val imagePickerLauncher: ActivityResultLauncher<String> =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            if (uri != null) {
                imageUri = uri
                ivPreview.setImageURI(imageUri)
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

        menuId = intent.getStringExtra("menuId")
        if (menuId != null) {
            supportActionBar?.title = "Edit Menu Item"
            loadMenuData(menuId!!)
        } else {
            supportActionBar?.title = "Add New Menu Item"
        }

        btnUploadImage.setOnClickListener { openGallery() }
        btnSave.setOnClickListener { validateAndSaveMenu() }
    }

    private fun openGallery() {
        imagePickerLauncher.launch("image/*")
    }

    private fun loadMenuData(id: String) {
        firestore.collection("canteenMenu").document(id).get()
            .addOnSuccessListener { doc ->
                if (doc.exists()) {
                    etName.setText(doc.getString("name"))
                    etPrice.setText(doc.getDouble("price")?.toString())
                    switchAvailable.isChecked = doc.getBoolean("available") ?: true

                    val imagePath = doc.getString("imageUrl")
                    if (!imagePath.isNullOrEmpty()) {
                        existingImagePath = imagePath
                        val file = File(imagePath)
                        if (file.exists()) {
                            Picasso.get().load(file).into(ivPreview)
                        }
                    }
                }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to load menu data.", Toast.LENGTH_SHORT).show()
            }
    }

    private fun validateAndSaveMenu() {
        val name = etName.text.toString().trim()
        val priceText = etPrice.text.toString().trim()

        if (name.isEmpty() || priceText.isEmpty()) {
            Toast.makeText(this, "Please enter name and price.", Toast.LENGTH_SHORT).show()
            return
        }

        val price = priceText.toDoubleOrNull()
        if (price == null || price < 0) {
            Toast.makeText(this, "Please enter a valid price.", Toast.LENGTH_SHORT).show()
            return
        }

        progressBar.visibility = View.VISIBLE
        btnSave.isEnabled = false

        val available = switchAvailable.isChecked

        when {
            imageUri != null -> saveImageLocallyAndSave(name, price, available)
            menuId != null && existingImagePath != null -> saveToFirestore(name, price, available, existingImagePath!!)
            else -> saveToFirestore(name, price, available, "")
        }
    }

    private fun saveImageLocallyAndSave(name: String, price: Double, available: Boolean) {
        try {
            val inputStream = contentResolver.openInputStream(imageUri!!)
            val fileName = "menu_${UUID.randomUUID()}.jpg"
            val file = File(filesDir, fileName)
            inputStream?.use { input ->
                FileOutputStream(file).use { output ->
                    input.copyTo(output)
                }
            }
            saveToFirestore(name, price, available, file.absolutePath)
        } catch (e: Exception) {
            progressBar.visibility = View.GONE
            btnSave.isEnabled = true
            Toast.makeText(this, "Failed to save image locally: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun saveToFirestore(name: String, price: Double, available: Boolean, imagePath: String) {
        val menuMap = hashMapOf(
            "name" to name,
            "price" to price,
            "available" to available,
            "imageUrl" to imagePath
        )

        val saveTask = if (menuId == null) {
            firestore.collection("canteenMenu").add(menuMap)
        } else {
            firestore.collection("canteenMenu").document(menuId!!).set(menuMap)
        }

        saveTask.addOnSuccessListener {
            progressBar.visibility = View.GONE
            Toast.makeText(this, "Menu saved successfully!", Toast.LENGTH_SHORT).show()
            finish()
        }.addOnFailureListener { e ->
            progressBar.visibility = View.GONE
            btnSave.isEnabled = true
            Toast.makeText(this, "Failed to save menu: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}
