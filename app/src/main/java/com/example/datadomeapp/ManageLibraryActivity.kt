package com.example.datadomeapp

import android.net.Uri
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.tasks.Tasks
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage

// Data class for Book
data class Book(
    val title: String = "",
    val author: String = "",
    val category: String = "",
    val isbn: String = "",
    val description: String = "",
    var photoUrl: String = "",
    var pdfUrl: String = ""
)

class ManageLibraryActivity : AppCompatActivity() {

    private val firestore = FirebaseFirestore.getInstance()
    private val storage = FirebaseStorage.getInstance()
    private val booksCollection = firestore.collection("library")

    private val bookList = ArrayList<String>()
    private lateinit var adapter: ArrayAdapter<String>

    private var selectedPhotoUri: Uri? = null
    private var selectedPdfUri: Uri? = null

    private lateinit var ivSelectedPhoto: ImageView
    private lateinit var tvSelectedPdf: TextView

    private val photoPicker = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        selectedPhotoUri = uri
        ivSelectedPhoto.setImageURI(uri)
    }

    private val pdfPicker = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        selectedPdfUri = uri
        tvSelectedPdf.text = uri?.lastPathSegment ?: "No PDF selected"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_manage_library)

        // Views
        val listView = findViewById<ListView>(R.id.lvBooks)
        val btnShowAddBook = findViewById<Button>(R.id.btnShowAddBook)
        val layoutBookInputs = findViewById<LinearLayout>(R.id.layoutBookInputs)
        val etTitle = findViewById<EditText>(R.id.etBookTitle)
        val etAuthor = findViewById<EditText>(R.id.etBookAuthor)
        val etCategory = findViewById<EditText>(R.id.etBookCategory)
        val etIsbn = findViewById<EditText>(R.id.etBookIsbn)
        val etDescription = findViewById<EditText>(R.id.etBookDescription)
        val btnSelectPhoto = findViewById<Button>(R.id.btnSelectPhoto)
        val btnSelectPdf = findViewById<Button>(R.id.btnSelectPdf)
        val btnAddBook = findViewById<Button>(R.id.btnAddBook)
        val btnDelete = findViewById<Button>(R.id.btnDeleteBook)

        ivSelectedPhoto = findViewById(R.id.ivSelectedPhoto)
        tvSelectedPdf = findViewById(R.id.tvSelectedPdf)

        // ListView setup
        adapter = ArrayAdapter(this, android.R.layout.simple_list_item_single_choice, bookList)
        listView.adapter = adapter
        listView.choiceMode = ListView.CHOICE_MODE_SINGLE

        // Load books from Firestore
        booksCollection.addSnapshotListener { snapshot, error ->
            if (error != null) {
                Toast.makeText(this, "Failed to load books", Toast.LENGTH_SHORT).show()
                return@addSnapshotListener
            }
            bookList.clear()
            snapshot?.documents?.forEach { doc ->
                val title = doc.getString("title")
                title?.let { bookList.add(it) }
            }
            adapter.notifyDataSetChanged()
        }

        // Show input fields only when "Add Book" clicked
        btnShowAddBook.setOnClickListener {
            layoutBookInputs.visibility = LinearLayout.VISIBLE
            btnShowAddBook.visibility = Button.GONE
        }

        // Select photo
        btnSelectPhoto.setOnClickListener { photoPicker.launch("image/*") }

        // Select PDF
        btnSelectPdf.setOnClickListener { pdfPicker.launch("application/pdf") }

        // Add book
        btnAddBook.setOnClickListener {
            val title = etTitle.text.toString().trim()
            val author = etAuthor.text.toString().trim()
            val category = etCategory.text.toString().trim()
            val isbn = etIsbn.text.toString().trim()
            val description = etDescription.text.toString().trim()

            if (title.isEmpty() || author.isEmpty() || category.isEmpty() || isbn.isEmpty() || description.isEmpty()) {
                Toast.makeText(this, "Fill all fields", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val book = Book(title, author, category, isbn, description)
            val tasks = mutableListOf<com.google.android.gms.tasks.Task<Uri>>()

            selectedPhotoUri?.let { uri ->
                val ref = storage.reference.child("books/photos/${System.currentTimeMillis()}")
                tasks.add(
                    ref.putFile(uri).continueWithTask { t ->
                        if (!t.isSuccessful) throw t.exception!!
                        ref.downloadUrl
                    }.addOnSuccessListener { book.photoUrl = it.toString() }
                )
            }

            selectedPdfUri?.let { uri ->
                val ref = storage.reference.child("books/pdfs/${System.currentTimeMillis()}")
                tasks.add(
                    ref.putFile(uri).continueWithTask { t ->
                        if (!t.isSuccessful) throw t.exception!!
                        ref.downloadUrl
                    }.addOnSuccessListener { book.pdfUrl = it.toString() }
                )
            }

            val saveBookAction = {
                booksCollection.add(book)
                    .addOnSuccessListener {
                        clearFields(etTitle, etAuthor, etCategory, etIsbn, etDescription)
                        selectedPhotoUri = null
                        selectedPdfUri = null
                        ivSelectedPhoto.setImageResource(0)
                        tvSelectedPdf.text = "No PDF selected"
                        Toast.makeText(this, "Book added!", Toast.LENGTH_SHORT).show()
                        // Hide input layout again
                        layoutBookInputs.visibility = LinearLayout.GONE
                        btnShowAddBook.visibility = Button.VISIBLE
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(this, "Failed: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
            }

            if (tasks.isEmpty()) {
                saveBookAction()
            } else {
                Tasks.whenAll(*tasks.toTypedArray())
                    .addOnSuccessListener { saveBookAction() }
                    .addOnFailureListener { e ->
                        Toast.makeText(this, "Upload failed: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
            }
        }

        // Delete book
        btnDelete.setOnClickListener {
            val pos = listView.checkedItemPosition
            if (pos != ListView.INVALID_POSITION) {
                val bookTitle = bookList[pos]
                booksCollection.whereEqualTo("title", bookTitle)
                    .get()
                    .addOnSuccessListener { docs ->
                        for (doc in docs) doc.reference.delete()
                        Toast.makeText(this, "Book removed", Toast.LENGTH_SHORT).show()
                    }
            } else {
                Toast.makeText(this, "Select a book to delete", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Clear input fields
    private fun clearFields(vararg editTexts: EditText) {
        editTexts.forEach { it.text.clear() }
    }
}
