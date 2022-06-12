package com.example.matricula.model

data class Course(
    val id: Int,
    val code: String,
    val name: String,
    val credits: Int,
    val hours: Int,
    val careerId: Int,
)
