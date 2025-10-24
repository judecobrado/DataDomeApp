package com.example.datadomeapp.canteen

import retrofit2.http.Url

data class MenuItem(
    var id: String = "",
    var name: String = "",
    var price: Double = 0.0,
    var available: Boolean = true,
    var imageUrl: String = "",
    var staffUid: String = "",
    val canteenName: String = ""
)
