package com.example.matricula.service

import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.POST
import retrofit2.http.Path

interface UsersService {

    @POST("users")
    suspend fun saveUser(@Body requestBody: RequestBody): Response<ResponseBody>

    @DELETE("users/{id}")
    suspend fun deleteUser(@Path("id") id: Int): Response<ResponseBody>
}