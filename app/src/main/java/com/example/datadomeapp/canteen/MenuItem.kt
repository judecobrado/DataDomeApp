package com.example.datadomeapp.canteen

data class MenuItem(
    var id: String = "",
    var name: String = "",
    var price: Double = 0.0,
    var available: Boolean = true,
    var imageUrl: String = ""
)
