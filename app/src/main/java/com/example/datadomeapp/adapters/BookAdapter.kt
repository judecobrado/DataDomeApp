package com.example.datadomeapp.adapters

import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.datadomeapp.BookDetailsActivity
import com.example.datadomeapp.R
import com.example.datadomeapp.models.BookItem

class BookAdapter(private var books: List<BookItem>) :
    RecyclerView.Adapter<BookAdapter.BookViewHolder>() {

    inner class BookViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val title: TextView = itemView.findViewById(R.id.textTitle)
        val author: TextView = itemView.findViewById(R.id.textAuthor)
        val image: ImageView = itemView.findViewById(R.id.imageBook)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BookViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_book, parent, false)
        return BookViewHolder(view)
    }

    override fun onBindViewHolder(holder: BookViewHolder, position: Int) {
        val book = books[position]
        val volumeInfo = book.volumeInfo

        holder.title.text = volumeInfo.title ?: "No Title"
        holder.author.text = volumeInfo.authors?.joinToString(", ") ?: "Unknown Author"

        val imageUrl = volumeInfo.imageLinks?.thumbnail
        Glide.with(holder.itemView.context)
            .load(imageUrl)
            .placeholder(R.drawable.ic_book_placeholder)
            .into(holder.image)

        // ✅ Handle click to open details
        holder.itemView.setOnClickListener {
            val context = holder.itemView.context
            val intent = Intent(context, BookDetailsActivity::class.java)
            intent.putExtra("title", volumeInfo.title)
            intent.putExtra("authors", volumeInfo.authors?.joinToString(", ") ?: "Unknown Author")
            intent.putExtra("description", volumeInfo.description ?: "No description available")
            intent.putExtra("thumbnail", imageUrl)

            // ✅ Include preview and PDF links
            intent.putExtra("previewLink", book.accessInfo?.webReaderLink)
            intent.putExtra("pdfLink", book.accessInfo?.pdf?.acsTokenLink)

            context.startActivity(intent)
        }
    }

    override fun getItemCount() = books.size

    fun updateBooks(newBooks: List<BookItem>) {
        books = newBooks
        notifyDataSetChanged()
    }
}