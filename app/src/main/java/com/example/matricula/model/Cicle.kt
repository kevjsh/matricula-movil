package com.example.matricula.model

import java.sql.Date

data class Cicle(
    val id: Int,
    val year: Int,
    val cicleNumber: Int,
    val initDate: Date,
    val finishDate: Date,

)