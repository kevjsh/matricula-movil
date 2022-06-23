package com.example.matricula.model

import java.lang.reflect.Constructor
import java.sql.Date

data class User(
    val id: Int,
    val personId: String,
    val name: String,
    val telephone: Int,
    var birthday: Date?,
    val careerId: Int,
    val roleId: Int,
    val email: String,
    val password: String,
)