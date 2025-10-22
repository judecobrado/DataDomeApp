package com.example.datadomeapp.api

import com.example.datadomeapp.models.BookResponse
import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Query

interface GoogleBooksApi {
    @GET("volumes")
    fun searchBooks(
        @Query("q") query: String
    ): Call<BookResponse>
}