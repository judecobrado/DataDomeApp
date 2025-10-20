package com.example.datadomeapp.admin

import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.example.datadomeapp.library.Book
import com.example.datadomeapp.R
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage

class ManageLibrary : AppCompatActivity() {

    private val firestore = FirebaseFirestore.getInstance()
    private val storage = FirebaseStorage.getInstance()
    private val booksCollection = firestore.collection("library")

    private val bookTitles = ArrayList<String>()
    private lateinit var adapter: ArrayAdapter<String>

    private var selectedPhotoUri: Uri? = null
    private var selectedPdfUri: Uri? = null

    private lateinit var ivSelectedPhoto: ImageView
    private lateinit var tvSelectedPdf: TextView
    private lateinit var layoutBookInputs: LinearLayout
    private lateinit var btnShowAddBook: Button
    private lateinit var listView: ListView
    private lateinit var btnAddBook: Button

    private val photoPicker = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        selectedPhotoUri = uri
        ivSelectedPhoto.setImageURI(uri)
    }

    private val pdfPicker = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        selectedPdfUri = uri
        tvSelectedPdf.text = uri?.lastPathSegment ?: "No PDF selected"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.library_manage)

        // Enable App Check debug provider
        FirebaseAppCheck.getInstance()
            .installAppCheckProviderFactory(DebugAppCheckProviderFactory.getInstance())

        // Initialize views (make sure your XML IDs match these)
        listView = findViewById(R.id.lvBooks)
        btnShowAddBook = findViewById(R.id.btnShowAddBook)
        layoutBookInputs = findViewById(R.id.layoutBookInputs)
        ivSelectedPhoto = findViewById(R.id.ivSelectedPhoto)
        tvSelectedPdf = findViewById(R.id.tvSelectedPdf)
        btnAddBook = findViewById(R.id.btnAddBook)

        val etTitle = findViewById<EditText>(R.id.etBookTitle)
        val etAuthor = findViewById<EditText>(R.id.etBookAuthor)
        val etCategory = findViewById<EditText>(R.id.etBookCategory)
        val etIsbn = findViewById<EditText>(R.id.etBookIsbn)
        val etDescription = findViewById<EditText>(R.id.etBookDescription)

        val btnSelectPhoto = findViewById<Button>(R.id.btnSelectPhoto)
        val btnSelectPdf = findViewById<Button>(R.id.btnSelectPdf)
        val btnDelete = findViewById<Button>(R.id.btnDeleteBook)

        adapter = ArrayAdapter(this, android.R.layout.simple_list_item_single_choice, bookTitles)
        listView.adapter = adapter
        listView.choiceMode = ListView.CHOICE_MODE_SINGLE

        loadBooksOnce()

        btnShowAddBook.setOnClickListener {
            layoutBookInputs.visibility = View.VISIBLE
            btnShowAddBook.visibility = View.GONE
        }

        btnSelectPhoto.setOnClickListener { photoPicker.launch("image/*") }
        btnSelectPdf.setOnClickListener { pdfPicker.launch("application/pdf") }

        btnAddBook.setOnClickListener {
            val title = etTitle.text.toString().trim()
            val author = etAuthor.text.toString().trim()
            val category = etCategory.text.toString().trim()
            val isbn = etIsbn.text.toString().trim()
            val description = etDescription.text.toString().trim()

            if (title.isEmpty() || author.isEmpty() || category.isEmpty() || description.isEmpty()) {
                Toast.makeText(this, "Fill all fields", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            btnAddBook.isEnabled = false
            val book = Book(title, author, category, isbn, description)

            uploadFile(selectedPhotoUri, "books/photos/${System.currentTimeMillis()}_$title.jpg",
                onSuccess = { photoUrl ->
                    book.photoUrl = photoUrl
                    uploadFile(selectedPdfUri, "books/pdfs/${System.currentTimeMillis()}_$title.pdf",
                        onSuccess = { pdfUrl ->
                            book.pdfUrl = pdfUrl
                            saveBook(book, etTitle, etAuthor, etCategory, etIsbn, etDescription)
                        },
                        onFailure = { e ->
                            btnAddBook.isEnabled = true
                            Toast.makeText(this, "PDF upload failed: ${e.message}", Toast.LENGTH_SHORT).show()
                        })
                },
                onFailure = { e ->
                    btnAddBook.isEnabled = true
                    Toast.makeText(this, "Photo upload failed: ${e.message}", Toast.LENGTH_SHORT).show()
                })
        }

        btnDelete.setOnClickListener {
            val pos = listView.checkedItemPosition
            if (pos != ListView.INVALID_POSITION) {
                val bookTitleToDelete = bookTitles[pos]
                booksCollection.whereEqualTo("title", bookTitleToDelete)
                    .get()
                    .addOnSuccessListener { docs ->
                        for (doc in docs) doc.reference.delete()
                        Toast.makeText(this, "Book removed", Toast.LENGTH_SHORT).show()
                        loadBooksOnce()
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(this, "Delete failed: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
            } else {
                Toast.makeText(this, "Select a book to delete", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun uploadFile(uri: Uri?, path: String, onSuccess: (String) -> Unit, onFailure: (Exception) -> Unit) {
        if (uri == null) { onSuccess(""); return }
        val ref = storage.reference.child(path)
        ref.putFile(uri)
            .addOnSuccessListener {
                ref.downloadUrl
                    .addOnSuccessListener { downloadUri -> onSuccess(downloadUri.toString()) }
                    .addOnFailureListener { e -> onFailure(e) }
            }
            .addOnFailureListener { e -> onFailure(e) }
    }

    private fun saveBook(book: Book, vararg editTexts: EditText) {
        booksCollection.add(book)
            .addOnSuccessListener {
                editTexts.forEach { it.text.clear() }
                selectedPhotoUri = null
                selectedPdfUri = null
                ivSelectedPhoto.setImageResource(0)
                tvSelectedPdf.text = "No PDF selected"
                layoutBookInputs.visibility = View.GONE
                btnShowAddBook.visibility = View.VISIBLE
                btnAddBook.isEnabled = true
                Toast.makeText(this, "Book added successfully!", Toast.LENGTH_SHORT).show()
                loadBooksOnce()
            }
            .addOnFailureListener { e ->
                btnAddBook.isEnabled = true
                Toast.makeText(this, "Failed to save book: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun loadBooksOnce() {
        booksCollection.get()
            .addOnSuccessListener { snapshot ->
                bookTitles.clear()
                snapshot.documents.forEach { doc -> doc.getString("title")?.let { bookTitles.add(it) } }
                adapter.notifyDataSetChanged()
                listView.clearChoices()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to load books: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }
}