package com.example.datadomeapp

import android.os.Bundle
import android.widget.SearchView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.datadomeapp.adapters.BookAdapter
import com.example.datadomeapp.api.RetrofitInstance
import com.example.datadomeapp.models.BookItem
import com.example.datadomeapp.models.BookResponse
import com.example.datadomeapp.LibraryActivity
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class LibraryActivity : AppCompatActivity() {

    private lateinit var searchView: SearchView
    private lateinit var recyclerViewBooks: RecyclerView
    private lateinit var adapter: BookAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_library)

        // Initialize views
        searchView = findViewById(R.id.searchView)
        recyclerViewBooks = findViewById(R.id.recyclerViewBooks)

        recyclerViewBooks.layoutManager = LinearLayoutManager(this)
        adapter = BookAdapter(emptyList())
        recyclerViewBooks.adapter = adapter

        // Listen for search input
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                if (!query.isNullOrEmpty()) {
                    searchBooks(query)
                }
                return true
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                return false
            }
        })
    }

    private fun searchBooks(query: String) {
        val call = RetrofitInstance.api.searchBooks(query)

        call.enqueue(object : Callback<BookResponse> {
            override fun onResponse(call: Call<BookResponse>, response: Response<BookResponse>) {
                if (response.isSuccessful) {
                    val items: List<BookItem> = response.body()?.items ?: emptyList()

                    if (items.isNotEmpty()) {
                        adapter.updateBooks(items)
                    } else {
                        Toast.makeText(this@LibraryActivity, "No books found", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(this@LibraryActivity, "Error: ${response.message()}", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onFailure(call: Call<BookResponse>, t: Throwable) {
                Toast.makeText(this@LibraryActivity, "Failed: ${t.localizedMessage}", Toast.LENGTH_SHORT).show()
                t.printStackTrace()
            }
        })
    }
}