package com.example.aplikasialatpertanian

data class Product(
    var id: String? = null,
    val name: String = "",
    val price: Double = 0.0,
    val stock: Int = 0,
    val description: String = "",
    val imageUrl: String = ""
)