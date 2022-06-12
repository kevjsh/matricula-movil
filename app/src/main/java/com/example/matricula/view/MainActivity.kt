package com.example.matricula.view

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.Toast
import com.example.matricula.BuildConfig
import com.example.matricula.databinding.ActivityMainBinding
import com.example.matricula.service.LoginService
import com.google.gson.Gson
import com.google.gson.JsonParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import retrofit2.Retrofit

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val login = binding.login

        login.setOnClickListener {
            login()
        }

    }

    private fun login() {

        val personId: String = binding.personId.text.toString()
        val password: String = binding.password.text.toString()

        val retrofit = Retrofit.Builder().baseUrl(BuildConfig.BASE_URL).build()
        val service = retrofit.create(LoginService::class.java)

        val jsonObject = JSONObject()

        // Here we can create the body of http request
        jsonObject.put("personId", personId)
        jsonObject.put("password", password)

        // Type configurations
        val jsonObjectString = jsonObject.toString()
        val requestBody = jsonObjectString.toRequestBody("application/json".toMediaTypeOrNull())

        // Implements coroutines
        CoroutineScope(Dispatchers.IO).launch {

            val response = service.login(requestBody)
            withContext(Dispatchers.Main) {
                if (response.isSuccessful) {

                    val user = Gson().toJson(JsonParser.parseString(response.body()?.string()))
                    message("Bienvenido!")
                    mainMenu(user)

                } else {
                    message("Error en el usuario o la contraseña!")
                }
            }

        }
    }

    private fun mainMenu(user: String) {
        val intent = Intent(this, MenuActivity::class.java).apply {
            putExtra("User", user)
        }
        startActivity(intent)
    }

    /* Fast message on screen */
    private fun message(message: String) {
        runOnUiThread {
            Toast.makeText(this,  message, Toast.LENGTH_LONG).show()
        }
    }
}