package com.example.datadomeapp

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide

class BookDetailsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_book_details)

        val imageBook: ImageView = findViewById(R.id.imageBookDetail)
        val titleText: TextView = findViewById(R.id.textTitleDetail)
        val authorText: TextView = findViewById(R.id.textAuthorDetail)
        val descriptionText: TextView = findViewById(R.id.textDescriptionDetail)
        val btnPreview: Button = findViewById(R.id.btnPreview)
        val btnPdf: Button = findViewById(R.id.btnPdf)

        val title = intent.getStringExtra("title")
        val authors = intent.getStringExtra("authors")
        val description = intent.getStringExtra("description")
        val thumbnail = intent.getStringExtra("thumbnail")
        val previewLink = intent.getStringExtra("previewLink")
        val pdfLink = intent.getStringExtra("pdfLink")

        titleText.text = title
        authorText.text = authors
        descriptionText.text = description

        Glide.with(this)
            .load(thumbnail)
            .placeholder(R.drawable.ic_book_placeholder)
            .into(imageBook)

        // Open preview link
        btnPreview.setOnClickListener {
            previewLink?.let {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(it)))
            }
        }

        // Open PDF link (if available)
        btnPdf.setOnClickListener {
            pdfLink?.let {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(it)))
            }
        }

        // Hide buttons if links not available
        if (previewLink.isNullOrEmpty()) btnPreview.isEnabled = false
        if (pdfLink.isNullOrEmpty()) btnPdf.isEnabled = false
    }
}
