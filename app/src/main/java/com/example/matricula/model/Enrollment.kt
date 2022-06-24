package com.example.matricula.model

data class Enrollment(
    val id: Int,
    val group: Group,
    val user: User,
    var grade: Int,
)