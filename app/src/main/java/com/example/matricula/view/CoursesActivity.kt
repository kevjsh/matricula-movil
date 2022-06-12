package com.example.matricula.view

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.Toast
import com.example.matricula.BuildConfig
import com.example.matricula.databinding.ActivityCoursesBinding
import com.example.matricula.model.*
import com.google.gson.Gson
import com.google.gson.JsonParser
import okhttp3.*
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONTokener
import java.util.concurrent.TimeUnit

class CoursesActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCoursesBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCoursesBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val user: User = Gson().fromJson(intent.getStringExtra("User"), User::class.java)
        binding.welcome.text = "Bienvenido " + user.name

        getAllCourses();

    }

    private fun getAllCourses() {

        val httpClient = OkHttpClient.Builder().pingInterval(40, TimeUnit.SECONDS).build()
        val request = Request.Builder().url("${BuildConfig.BASE_URL_WS}/courses").build()

        httpClient.newWebSocket(request, object : WebSocketListener() {

            override fun onMessage(webSocket: WebSocket, coursesWS: String) {
                super.onMessage(webSocket, coursesWS)

                // Parsing data
                val courses = Gson().fromJson(coursesWS, MutableList::class.java);
                val coursesList: MutableList<Course> = mutableListOf()

                for (course in courses) {
                    coursesList.add(Gson().fromJson(Gson().toJson(course), Course::class.java))
                }

                message("We receive an update!")

                /* Here we can implement new logic */

            }
        })
    }

    /* Fast message on screen */
    private fun message(message: String) {
        runOnUiThread {
            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        }
    }

}