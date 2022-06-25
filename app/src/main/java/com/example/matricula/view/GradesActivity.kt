package com.example.matricula.view

import android.R
import android.widget.AdapterView.OnItemSelectedListener
import android.app.Dialog
import android.graphics.Color
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.text.Editable
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.widget.*
import androidx.annotation.RequiresApi
import com.example.matricula.BuildConfig
import com.example.matricula.databinding.ActivityGradesBinding
import com.example.matricula.model.*
import com.example.matricula.service.EnrollmentsService
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
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit

class GradesActivity : AppCompatActivity() {
    private lateinit var binding: ActivityGradesBinding

    private lateinit var user: User
    private lateinit var allStudents: MutableList<User>
    private lateinit var allCicles: MutableList<Cicle>
    private lateinit var allCourses: MutableList<Course>
    private lateinit var allGroups: MutableList<Group>

    private var selectedCicle: Int? = null
    private var selectedGroup: Int? = null
    private var selectedEnrollment: Enrollment? = null

    // Websockets
    private lateinit var enrollmentsSocket: WebSocket

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityGradesBinding.inflate(layoutInflater)
        setContentView(binding.root)

        user = Gson().fromJson(intent.getStringExtra("User"), User::class.java)

        setSupportActionBar(binding.toolbar)
        var ab = supportActionBar
        if (ab!=null) {
            ab.title = "Matrícula"
        }

        getAllStudents()

    }

    /* Basic operations */
    private fun saveEnrollment(enrollment: Enrollment) {

        val retrofit = Retrofit.Builder().baseUrl(BuildConfig.BASE_URL).build()
        val service = retrofit.create(EnrollmentsService::class.java)

        // Type configurations
        enrollment.user.birthday = null
        val jsonObjectString = Gson().toJson(enrollment)

        val requestBody = jsonObjectString.toRequestBody("application/json".toMediaTypeOrNull())

        // Implements coroutines
        CoroutineScope(Dispatchers.IO).launch {

            val response = service.saveEnrollment(requestBody)
            withContext(Dispatchers.Main) {
                if (response.isSuccessful) {
                    message("Actualización correcta!")

                } else {
                    message("Ocurrió un error en la actualización!")
                }
            }

        }

    }

    private fun initializations() {

        // Data for cicles
        var ciclesNames: Array<String> = allCicles.map { c -> "${c.year} - ${c.cicleNumber}" }.toList().toTypedArray();
        val ciclesAdapter: ArrayAdapter<String> =
            ArrayAdapter<String>(this, R.layout.simple_spinner_dropdown_item, ciclesNames)

        // Data for groups
        var groupsNames: Array<String> = arrayOf()
        var groupsAdapter: ArrayAdapter<String> = ArrayAdapter<String>(this, R.layout.simple_spinner_dropdown_item, groupsNames)

        this@GradesActivity.runOnUiThread(java.lang.Runnable {

            binding.cicles.adapter = ciclesAdapter

            binding.groups.visibility = View.GONE
            binding.enrollmentLayout.visibility = View.GONE

            binding.cicles.onItemSelectedListener = object : OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>, view: View, position: Int, id: Long) {
                    selectedCicle = binding.cicles.selectedItemPosition + 1

                    // Data for groups
                    groupsNames = allGroups.filter { g -> g.cicleId == selectedCicle }
                        .map { g -> "${allCourses.first { c -> c.id == g.courseId }.name} (grupo ${g.groupNumber})" }
                        .toList().toTypedArray();

                    groupsAdapter = updateCoursesList(groupsNames);

                    binding.groups.adapter = groupsAdapter

                    binding.groups.visibility = View.VISIBLE

                }

                override fun onNothingSelected(parent: AdapterView<*>) {}
            }

            binding.groups.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>, view: View, position: Int, id: Long) {
                    selectedGroup = binding.groups.selectedItemPosition
                    binding.enrollmentLayout.visibility = View.VISIBLE

                    if (::enrollmentsSocket.isInitialized) {
                        enrollmentsSocket.cancel()
                    }
                    getAllEnrollments()
                }

                override fun onNothingSelected(parent: AdapterView<*>) {}
            }

        })

    }

    private fun updateCoursesList(groupsNames:Array<String>):ArrayAdapter<String>{
        return ArrayAdapter<String>(this, R.layout.simple_spinner_dropdown_item, groupsNames)
    }

    // Function to display the custom dialog.
    private fun saveEnrollmentDialog() {

        // Dialog configurations
        val dialog = Dialog(this@GradesActivity)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setCancelable(true)
        dialog.setContentView(com.example.matricula.R.layout.savegrade_dialog)
        val lp = WindowManager.LayoutParams()
        lp.copyFrom(dialog.window?.attributes)
        lp.width = WindowManager.LayoutParams.MATCH_PARENT;
        lp.height = WindowManager.LayoutParams.WRAP_CONTENT;

        // Dialog element
        val grade: EditText = dialog.findViewById(com.example.matricula.R.id.grade)
        grade.text = Editable.Factory.getInstance().newEditable(selectedEnrollment?.grade.toString())

        // Button
        val saveEnrollment: Button = dialog.findViewById(com.example.matricula.R.id.saveEnrollment)

        // Listener
        saveEnrollment.setOnClickListener {

            selectedEnrollment?.grade = grade.text.toString().toInt()
            saveEnrollment(selectedEnrollment!!)
            dialog.dismiss()

        }

        // Showing dialog
        dialog.show()
        dialog.window?.attributes = lp;
    }

    /* Update view */
    private fun updateTable(enrollmentList: MutableList<Enrollment>) {

        var table = binding.table

        try {

            // Cleaning the table
            this@GradesActivity.runOnUiThread(java.lang.Runnable {
                table.removeAllViews()
            })

            // Generating Headers
            val header = TableRow(this)
            header.setBackgroundColor(Color.GRAY)

            val c1 = TextView(this)
            val c2 = TextView(this)
            val c3 = TextView(this)

            c1.text = "Alumno"
            c2.text = "Grupo"
            c3.text = "Nota"

            header.addView(c1);
            header.addView(c2);
            header.addView(c3);

            this@GradesActivity.runOnUiThread(java.lang.Runnable {
                table.addView(header)
            })

            // Each course
            for (enrollment in enrollmentList) {

                // Elements
                val row = TableRow(this)

                val studentName = TextView(this)
                val groupName = TextView(this)
                val grade = TextView(this)

                // Fill elements
                studentName.text = enrollment.user.name
                groupName.text =
                    "${allCourses.first { c -> c.id == enrollment.group.courseId }.name} (grupo ${enrollment.group.groupNumber})"
                grade.text = enrollment.grade.toString()

                row.addView(studentName);
                row.addView(groupName);
                row.addView(grade);

                this@GradesActivity.runOnUiThread(java.lang.Runnable {
                    table.addView(row)
                })

                row.setOnClickListener {
                    selectedEnrollment = enrollment
                    saveEnrollmentDialog()
                }

            }

        } catch (ex: Exception) {
            message(ex.message.toString())
        }

    }

    /* Get data from Websockets */

    private fun getAllStudents() {

        val httpClient = OkHttpClient.Builder().pingInterval(40, TimeUnit.SECONDS).build()
        val request = Request.Builder().url("${BuildConfig.BASE_URL_WS}/users").build()

        httpClient.newWebSocket(request, object : WebSocketListener() {

            override fun onMessage(webSocket: WebSocket, usersWS: String) {
                super.onMessage(webSocket, usersWS)

                // Parsing data
                val users = Gson().fromJson(usersWS, MutableList::class.java);
                var studentList: MutableList<User> = mutableListOf()

                for (student in users) {
                    studentList.add(Gson().fromJson(Gson().toJson(student), User::class.java))
                }

                studentList = studentList.filter { u -> u.roleId == 4 }.toMutableList()

                // Update the information
                allStudents = studentList;

                getAllCicles()

            }
        })
    }

    private fun getAllCicles() {

        val httpClient = OkHttpClient.Builder().pingInterval(40, TimeUnit.SECONDS).build()
        val request = Request.Builder().url("${BuildConfig.BASE_URL_WS}/cicles").build()

        httpClient.newWebSocket(request, object : WebSocketListener() {

            override fun onMessage(webSocket: WebSocket, ciclesWS: String) {
                super.onMessage(webSocket, ciclesWS)

                // Parsing data
                val cicles = Gson().fromJson(ciclesWS, MutableList::class.java);
                var cicleList: MutableList<Cicle> = mutableListOf()

                for (cicle in cicles) {
                    cicleList.add(Gson().fromJson(Gson().toJson(cicle), Cicle::class.java))
                }

                // Update the information
                allCicles = cicleList

                getAllGroups()

            }
        })
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

                // Update the information
                allCourses = coursesList

                try {
                    initializations()
                } catch (ex: Exception) {
                    message(ex.message.toString())
                }

            }
        })
    }

    private fun getAllGroups() {

        val httpClient = OkHttpClient.Builder().pingInterval(40, TimeUnit.SECONDS).build()
        val request = Request.Builder().url("${BuildConfig.BASE_URL_WS}/groups").build()

        httpClient.newWebSocket(request, object : WebSocketListener() {

            override fun onMessage(webSocket: WebSocket, groupsWS: String) {
                super.onMessage(webSocket, groupsWS)

                // Parsing data
                val groups = Gson().fromJson(groupsWS, MutableList::class.java);
                val groupsList: MutableList<Group> = mutableListOf()

                for (group in groups) {
                    groupsList.add(Gson().fromJson(Gson().toJson(group), Group::class.java))
                }

                // Update the information
                allGroups = groupsList.filter { g -> g.professorId == user.id }.toMutableList()

                getAllCourses()

            }
        })
    }

    private fun getAllEnrollments() {

        val httpClient = OkHttpClient.Builder().pingInterval(40, TimeUnit.SECONDS).build()
        val request = Request.Builder().url("${BuildConfig.BASE_URL_WS}/enrollments").build()

        enrollmentsSocket = httpClient.newWebSocket(request, object : WebSocketListener() {

            override fun onMessage(webSocket: WebSocket, enrollmentsWS: String) {
                super.onMessage(webSocket, enrollmentsWS)

                // Parsing data
                val enrollments = Gson().fromJson(enrollmentsWS, MutableList::class.java);
                var enrollmentsList: MutableList<Enrollment> = mutableListOf()

                for (enrollment in enrollments) {
                    enrollmentsList.add(Gson().fromJson(Gson().toJson(enrollment), Enrollment::class.java))
                }

                var filteredGroups: MutableList<Group> = allGroups.filter { g -> g.cicleId == selectedCicle }.toMutableList()
                enrollmentsList = enrollmentsList.filter { u -> u.group.id == filteredGroups[selectedGroup!!].id }.toMutableList()

                // Update the information
                updateTable(enrollmentsList)

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