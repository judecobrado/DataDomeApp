package com.example.datadomeapp

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide

class BookDetailsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_book_details)

        val ivPhoto: ImageView = findViewById(R.id.ivBookDetailPhoto)
        val tvTitle: TextView = findViewById(R.id.tvDetailTitle)
        val tvAuthor: TextView = findViewById(R.id.tvDetailAuthor)
        val tvCategory: TextView = findViewById(R.id.tvDetailCategory)
        val tvIsbn: TextView = findViewById(R.id.tvDetailIsbn)
        val tvDescription: TextView = findViewById(R.id.tvDetailDescription)
        val btnViewPdf: Button = findViewById(R.id.btnViewPdf)

        // Receive book as Parcelable
        val book: Book? = intent.getParcelableExtra("book")
        if (book == null) {
            Toast.makeText(this, "Book details not available", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        tvTitle.text = book.title
        tvAuthor.text = "Author: ${book.author}"
        tvCategory.text = "Category: ${book.category}"
        tvIsbn.text = "ISBN: ${book.isbn}"
        tvDescription.text = book.description

        Glide.with(this)
            .load(book.photoUrl ?: "")
            .placeholder(android.R.color.darker_gray)
            .into(ivPhoto)

        btnViewPdf.setOnClickListener {
            val pdfUrl = book.pdfUrl
            if (!pdfUrl.isNullOrEmpty()) {
                try {
                    val intent = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(Uri.parse(pdfUrl), "application/pdf")
                        flags = Intent.FLAG_ACTIVITY_NO_HISTORY
                    }
                    startActivity(intent)
                } catch (e: Exception) {
                    Toast.makeText(this, "Unable to open PDF", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(this, "PDF not available", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
