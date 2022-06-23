package com.example.matricula.model

data class Group(
    val id: Int,
    val courseId: Int,
    val groupNumber: Int,
    val schedule: String,
    val cicleId: Int,
    val professorId: Int,
)