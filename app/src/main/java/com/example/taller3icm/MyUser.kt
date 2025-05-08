package com.example.taller3icm

data class MyUser(
    val nombre: String = "",
    val apellido: String = "",
    val email: String = "",
    val password: String = "",
    val imagen: String? = null,
    val id: String = "",
    val latitud: Double = 0.0,
    val longitud: Double = 0.0,
    val available: Boolean = false
)
