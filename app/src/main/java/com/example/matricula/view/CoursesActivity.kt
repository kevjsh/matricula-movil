package com.example.matricula.view

import android.app.Dialog
import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.view.Window
import android.view.WindowManager
import android.widget.*
import android.widget.AdapterView.OnItemClickListener
import androidx.appcompat.app.AppCompatActivity
import com.example.matricula.BuildConfig
import com.example.matricula.R
import com.example.matricula.databinding.ActivityCoursesBinding
import com.example.matricula.model.Career
import com.example.matricula.model.Course
import com.example.matricula.model.User
import com.example.matricula.service.CoursesService
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit


class CoursesActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCoursesBinding
    private lateinit var allCareers: MutableList<Career>
    private var selectedCourse: Course? = null
    private var selectedCareer: Career? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCoursesBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val user: User = Gson().fromJson(intent.getStringExtra("User"), User::class.java)
        val saveUser = binding.saveUser

        // Using coroutines to resolve data requirements
        CoroutineScope(Dispatchers.IO).launch {

            getAllCareers()

            withContext(Dispatchers.Main) {
                getAllCourses()
            }
        }

        saveUser.setOnClickListener {
            selectedCourse = null
            saveCourseDialog()
        }

    }

    /* Basic operations */
    private fun saveCourse(course: Course) {

        val retrofit = Retrofit.Builder().baseUrl(BuildConfig.BASE_URL).build()
        val service = retrofit.create(CoursesService::class.java)

        val jsonObject = JSONObject()

        // Here we can create the body of http request
        jsonObject.put("id", course.id)
        jsonObject.put("code", course.code)
        jsonObject.put("name", course.name)
        jsonObject.put("credits", course.credits)
        jsonObject.put("hours", course.hours)
        jsonObject.put("careerId", course.careerId)

        // Type configurations
        val jsonObjectString = jsonObject.toString()
        val requestBody = jsonObjectString.toRequestBody("application/json".toMediaTypeOrNull())

        // Implements coroutines
        CoroutineScope(Dispatchers.IO).launch {

            val response = service.saveCourse(requestBody)
            withContext(Dispatchers.Main) {
                if (response.isSuccessful) {
                    message("Actualización correcta!")

                } else {
                    message("Ocurrió un error en la actualización!")
                }
            }

        }

    }

    private fun deleteCourse(id: Int) {

        val retrofit = Retrofit.Builder().baseUrl(BuildConfig.BASE_URL).build()
        val service = retrofit.create(CoursesService::class.java)

        // Implements coroutines
        CoroutineScope(Dispatchers.IO).launch {

            val response = service.deleteCourse(id)
            withContext(Dispatchers.Main) {
                if (response.isSuccessful) {
                    message("Curso eliminado correctamnte!")
                } else {
                    message("Ocurrió un error en la actualización!")
                }
            }

        }
    }


    //Function to display the custom dialog.
    private fun saveCourseDialog() {

        // Dialog configurations
        val dialog = Dialog(this@CoursesActivity)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setCancelable(true)
        dialog.setContentView(R.layout.savecourse_dialog)
        val lp = WindowManager.LayoutParams()
        lp.copyFrom(dialog.window?.attributes)
        lp.width = WindowManager.LayoutParams.MATCH_PARENT;
        lp.height = WindowManager.LayoutParams.WRAP_CONTENT;

        // Dialog elements
        val code: EditText = dialog.findViewById(R.id.code)
        val name: EditText = dialog.findViewById(R.id.name)
        val credits: EditText = dialog.findViewById(R.id.credits)
        val hours: EditText = dialog.findViewById(R.id.hours)
        val careerName: AutoCompleteTextView = dialog.findViewById(R.id.career)

        // Data for autocomplete text view
        var careerNames: Array<String> = allCareers.map { c -> c.name }.toList().toTypedArray();
        val adapter: ArrayAdapter<String> = ArrayAdapter<String>(this, android.R.layout.select_dialog_item, careerNames)
        careerName.threshold = 1
        careerName.setAdapter(adapter)

        careerName.onItemClickListener = OnItemClickListener { _, _, i, _ ->
            selectedCareer = allCareers.first { c -> c.name == adapter.getItem(i).toString() }
        }

        //Initializing the fields of the dialog
        if (selectedCourse != null) {

            code.text = Editable.Factory.getInstance().newEditable(selectedCourse?.code)
            name.text = Editable.Factory.getInstance().newEditable(selectedCourse?.name)
            credits.text = Editable.Factory.getInstance().newEditable(selectedCourse?.credits.toString())
            hours.text = Editable.Factory.getInstance().newEditable(selectedCourse?.hours.toString())

            selectedCareer = allCareers.first { c -> c.id == selectedCourse?.careerId }
            careerName.text = Editable.Factory.getInstance().newEditable(selectedCareer?.name)
        }

        // Buttons
        val saveCourse: Button = dialog.findViewById(R.id.saveCourse)
        val deleteCourse: Button = dialog.findViewById(R.id.deleteCourse)

        // Listeners
        saveCourse.setOnClickListener {

            var id = 0

            if (selectedCourse != null) {
                id = selectedCourse?.id!!
            }
            selectedCourse = Course(
                id,
                code.text.toString(),
                name.text.toString(),
                credits.text.toString().toInt(),
                hours.text.toString().toInt(),
                selectedCareer?.id!!
            )

            saveCourse(selectedCourse!!)


            dialog.dismiss()
        }

        deleteCourse.setOnClickListener {
            if (selectedCourse != null) {
                val id = selectedCourse?.id
                deleteCourse(selectedCourse!!.id)
            }
            dialog.dismiss()
        }

        // Showing dialog
        dialog.show()
        dialog.window?.attributes = lp;
    }


    /* Update view */
    private fun updateTable(coursesList: MutableList<Course>) {

        var table = binding.table

        try {

            // Cleaning the table
            this@CoursesActivity.runOnUiThread(java.lang.Runnable {
                table.removeAllViews()
            })

            // Generating Headers
            val header = TableRow(this)
            header.setBackgroundColor(Color.GRAY)

            val c1 = TextView(this)
            val c2 = TextView(this)
            val c3 = TextView(this)
            val c4 = TextView(this)
            val c5 = TextView(this)

            c1.text = "Código"
            c2.text = "Nombre"
            c3.text = "Créditos"
            c4.text = "Horas"
            c5.text = "Carrera"

            header.addView(c1);
            header.addView(c2);
            header.addView(c3);
            header.addView(c4);
            header.addView(c5);

            this@CoursesActivity.runOnUiThread(java.lang.Runnable {
                table.addView(header)
            })

            // Each course
            for (course in coursesList) {

                // Elements
                val row = TableRow(this)

                val code = TextView(this)
                val name = TextView(this)
                val credits = TextView(this)
                val hours = TextView(this)
                val career = TextView(this)

                // Fill elements
                code.text = course.code
                name.text = course.name
                credits.text = course.credits.toString()
                hours.text = course.hours.toString()
                career.text = allCareers.first { c -> c.id == course.careerId }.name

                row.addView(code);
                row.addView(name);
                row.addView(credits);
                row.addView(hours);
                row.addView(career);

                this@CoursesActivity.runOnUiThread(java.lang.Runnable {
                    table.addView(row)
                })

                row.setOnClickListener {
                    selectedCourse = course
                    saveCourseDialog()
                }
            }

        } catch (ex: Exception) {
            message(ex.message.toString())
        }

    }


    /* Get data from Websockets */
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

                // Update the information
                updateTable(coursesList)
            }
        })
    }

    private fun getAllCareers() {

        val httpClient = OkHttpClient.Builder().pingInterval(40, TimeUnit.SECONDS).build()
        val request = Request.Builder().url("${BuildConfig.BASE_URL_WS}/careers").build()

        httpClient.newWebSocket(request, object : WebSocketListener() {

            override fun onMessage(webSocket: WebSocket, careersWS: String) {
                super.onMessage(webSocket, careersWS)

                // Parsing data
                val careers = Gson().fromJson(careersWS, MutableList::class.java);
                val careersList: MutableList<Career> = mutableListOf()

                for (course in careers) {
                    careersList.add(Gson().fromJson(Gson().toJson(course), Career::class.java))
                }

                // Update the information
                allCareers = careersList;

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